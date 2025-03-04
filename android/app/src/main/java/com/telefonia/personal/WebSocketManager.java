package com.telefonia.personal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class WebSocketManager {
    private static final String TAG = "WebSocketManager";
    private static WebSocketManager instance;

    private final Context context;
    private final SharedPreferences preferences;
    private final Gson gson;
    private WebSocketClient client;
    private final DeviceInfoHelper deviceInfoHelper;

    public enum ConnectionStatus {
        CONNECTING, CONNECTED, DISCONNECTED, ERROR
    }

    private static final MutableLiveData<ConnectionStatus> _connectionStatus =
            new MutableLiveData<>(ConnectionStatus.DISCONNECTED);
    public static final LiveData<ConnectionStatus> connectionStatus = _connectionStatus;

    private final int reconnectInterval = 5000; // 5 segundos
    private boolean autoReconnect = true;

    private final Map<String, MessageCallback> pendingMessages = new ConcurrentHashMap<>();
    private final Map<String, CommandHandler> commandHandlers = new ConcurrentHashMap<>();

    private WebSocketManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences("TelefoniaPersonal", Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.deviceInfoHelper = new DeviceInfoHelper(context);
        registerCommandHandlers();
    }

    public static synchronized WebSocketManager getInstance(Context context) {
        if (instance == null) {
            instance = new WebSocketManager(context);
        }
        return instance;
    }

    public static boolean isConnected(Context context) {
        WebSocketManager mgr = getInstance(context);
        return mgr.client != null && mgr.client.isOpen() &&
                _connectionStatus.getValue() == ConnectionStatus.CONNECTED;
    }

    public static boolean isConnecting(Context context) {
        return _connectionStatus.getValue() == ConnectionStatus.CONNECTING;
    }

    private void registerCommandHandlers() {
        commandHandlers.put("CALL", (message) -> {
            String callId = message.get("callId").getAsString();
            String phoneNumber = message.get("phoneNumber").getAsString();
            String direction = message.get("direction").getAsString();
            CallService.startCall(context, callId, phoneNumber, direction);

            JsonObject response = new JsonObject();
            response.addProperty("type", "CALL_RESPONSE");
            response.addProperty("callId", callId);
            response.addProperty("status", "accepted");
            sendMessage(response);
        });

        commandHandlers.put("END_CALL", (message) -> {
            String callId = message.get("callId").getAsString();
            CallService.endCall(context, callId);
            JsonObject response = new JsonObject();
            response.addProperty("type", "END_CALL_RESPONSE");
            response.addProperty("callId", callId);
            response.addProperty("status", "completed");
            sendMessage(response);
        });

        commandHandlers.put("SEND_DTMF", (message) -> {
            String callId = message.get("callId").getAsString();
            String digit = message.get("digit").getAsString();
            CallService.sendDtmf(context, callId, digit);
            JsonObject response = new JsonObject();
            response.addProperty("type", "DTMF_RESPONSE");
            response.addProperty("callId", callId);
            response.addProperty("digit", digit);
            response.addProperty("status", "sent");
            sendMessage(response);
        });
    }

    public void connect() {
        if (client != null && (client.isOpen() || client.isConnecting())) {
            client.close();
        }

        String serverUrl = preferences.getString("serverUrl", "");
        if (serverUrl.isEmpty()) {
            Log.e(TAG, "No server URL configured");
            _connectionStatus.postValue(ConnectionStatus.ERROR);
            return;
        }

        try {
            URI uri = new URI(serverUrl);
            _connectionStatus.postValue(ConnectionStatus.CONNECTING);
            client = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    Log.i(TAG, "WebSocket connection opened");
                    _connectionStatus.postValue(ConnectionStatus.CONNECTED);
                    sendConnectionMessage();
                    startPingTimer();
                }

                @Override
                public void onMessage(String message) {
                    Log.d(TAG, "WebSocket message received: " + message);
                    handleMessage(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.w(TAG, "WebSocket connection closed: " + reason);
                    _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
                    if (autoReconnect) {
                        reconnect();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error: " + ex.getMessage());
                    _connectionStatus.postValue(ConnectionStatus.ERROR);
                    if (autoReconnect) {
                        reconnect();
                    }
                }
            };
            client.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid WebSocket URI: " + e.getMessage());
            _connectionStatus.postValue(ConnectionStatus.ERROR);
        }
    }

    private void reconnect() {
        new Handler().postDelayed(() -> {
            if (_connectionStatus.getValue() != ConnectionStatus.CONNECTED &&
                    _connectionStatus.getValue() != ConnectionStatus.CONNECTING) {
                Log.i(TAG, "Attempting to reconnect WebSocket...");
                connect();
            }
        }, reconnectInterval);
    }

    private void sendConnectionMessage() {
        JsonObject message = new JsonObject();
        message.addProperty("type", "CONNECT");
        message.addProperty("deviceId", preferences.getString("deviceId", ""));
        message.addProperty("authToken", preferences.getString("serverToken", ""));
        sendMessage(message);
    }

    private void startPingTimer() {
        new Handler().postDelayed(() -> {
            if (client != null && client.isOpen()) {
                JsonObject ping = new JsonObject();
                ping.addProperty("type", "PING");
                ping.addProperty("deviceId", preferences.getString("deviceId", ""));
                ping.addProperty("timestamp", System.currentTimeMillis());
                sendMessage(ping);
                startPingTimer();
            }
        }, TimeUnit.SECONDS.toMillis(30));
    }

    private void handleMessage(String messageText) {
        try {
            JsonObject message = gson.fromJson(messageText, JsonObject.class);
            String type = message.get("type").getAsString();

            if ("PONG".equals(type)) return;
            if (commandHandlers.containsKey(type)) {
                commandHandlers.get(type).handle(message);
            } else {
                Log.w(TAG, "No handler for message type: " + type);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling message: " + e.getMessage());
        }
    }

    public void sendMessage(JsonObject message) {
        if (client == null || !client.isOpen()) {
            Log.e(TAG, "Cannot send message: WebSocket not connected");
            return;
        }
        client.send(gson.toJson(message));
    }

    public void disconnect() {
        if (client != null) {
            client.close();
            client = null;
        }
        _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
    }

    public interface MessageCallback {
        void onResponse(JsonObject response);
    }

    private interface CommandHandler {
        void handle(JsonObject message);
    }
}
