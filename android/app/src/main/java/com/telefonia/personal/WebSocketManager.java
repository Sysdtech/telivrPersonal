package com.telefonia.personal;

import android.content.Context;
import android.content.SharedPreferences;
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

    private Context context;
    private SharedPreferences preferences;
    private Gson gson;
    private WebSocketClient client;
    private DeviceInfoHelper deviceInfoHelper;

    // Estado de conexión usando LiveData
    public enum ConnectionStatus {
        CONNECTING, CONNECTED, DISCONNECTED, ERROR
    }
    
    private static final MutableLiveData<ConnectionStatus> _connectionStatus =
            new MutableLiveData<>(ConnectionStatus.DISCONNECTED);
    public static final LiveData<ConnectionStatus> connectionStatus = _connectionStatus;

    // Intervalo de reconexión (en milisegundos)
    private int reconnectInterval = 5000; // 5 segundos
    private boolean autoReconnect = true;

    // Mapas para callbacks y handlers
    private final Map<String, MessageCallback> pendingMessages = new ConcurrentHashMap<>();
    private final Map<String, CommandHandler> commandHandlers = new ConcurrentHashMap<>();

    // Constructor privado que recibe un Context
    private WebSocketManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences("TelefoniaPersonal", Context.MODE_PRIVATE);
        this.gson = new Gson();
        // Ahora se utiliza el constructor que recibe Context
        this.deviceInfoHelper = new DeviceInfoHelper(context);
        registerCommandHandlers();
    }

    // Método estático para obtener la instancia (siempre se requiere pasar un Context)
    public static synchronized WebSocketManager getInstance(Context context) {
        if (instance == null) {
            instance = new WebSocketManager(context);
        }
        return instance;
    }

    // Métodos de conexión (adaptados para requerir Context en getInstance)
    public static boolean isConnected(Context context) {
        WebSocketManager mgr = getInstance(context);
        return mgr.client != null && mgr.client.isOpen() &&
               _connectionStatus.getValue() == ConnectionStatus.CONNECTED;
    }
    
    public static boolean isConnecting(Context context) {
        return _connectionStatus.getValue() == ConnectionStatus.CONNECTING;
    }

    // Registra los manejadores de comandos
    private void registerCommandHandlers() {
        // Ejemplo: manejador para iniciar una llamada
        commandHandlers.put("CALL", (message) -> {
            String callId = message.get("callId").getAsString();
            String phoneNumber = message.get("phoneNumber").getAsString();
            String direction = message.get("direction").getAsString();
            
            // Llama a CallService (asegúrate de que la firma coincida)
            CallService.startCall(context, callId, phoneNumber, direction);
            
            // Responde con confirmación
            JsonObject response = new JsonObject();
            response.addProperty("type", "CALL_RESPONSE");
            response.addProperty("callId", callId);
            response.addProperty("status", "accepted");
            sendMessage(response);
        });
        
        // Otros manejadores (END_CALL, SEND_DTMF, PLAY_AUDIO, GET_STATUS, RESTART) se definen de forma similar.
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
        
        commandHandlers.put("PLAY_AUDIO", (message) -> {
            String callId = message.get("callId").getAsString();
            int audioFileId = message.get("audioFileId").getAsInt();
            CallService.playAudio(context, callId, audioFileId);
            JsonObject response = new JsonObject();
            response.addProperty("type", "PLAY_AUDIO_RESPONSE");
            response.addProperty("callId", callId);
            response.addProperty("audioFileId", audioFileId);
            response.addProperty("status", "playing");
            sendMessage(response);
        });
        
        commandHandlers.put("GET_STATUS", (message) -> {
            DeviceInfo deviceInfo = deviceInfoHelper.collectDeviceInfo();
            JsonObject response = new JsonObject();
            response.addProperty("type", "STATUS_RESPONSE");
            response.addProperty("status", "online");
            // Asigna los valores disponibles
            response.addProperty("batteryLevel", deviceInfo.batteryLevel);
            response.addProperty("isCharging", deviceInfo.isCharging);
            response.addProperty("networkType", deviceInfo.networkType);
            response.addProperty("signalStrength", deviceInfo.signalStrength);
            sendMessage(response);
        });
        
        commandHandlers.put("RESTART", (message) -> {
            JsonObject response = new JsonObject();
            response.addProperty("type", "RESTART_RESPONSE");
            response.addProperty("status", "restarting");
            sendMessage(response);
            new android.os.Handler().postDelayed(() -> {
                System.exit(0);
            }, 3000);
        });
    }
    
    // Conexión al servidor WebSocket
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
        
        if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
            if (serverUrl.startsWith("http://")) {
                serverUrl = serverUrl.replace("http://", "ws://");
            } else if (serverUrl.startsWith("https://")) {
                serverUrl = serverUrl.replace("https://", "wss://");
            } else {
                serverUrl = "ws://" + serverUrl;
            }
        }
        
        if (!serverUrl.endsWith("/ws")) {
            serverUrl = serverUrl.endsWith("/") ? serverUrl + "ws" : serverUrl + "/ws";
        }
        
        String deviceId = preferences.getString("deviceId", "");
        if (deviceId.isEmpty()) {
            Log.e(TAG, "No device ID found");
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
        new android.os.Handler().postDelayed(() -> {
            if (_connectionStatus.getValue() != ConnectionStatus.CONNECTED &&
                _connectionStatus.getValue() != ConnectionStatus.CONNECTING) {
                Log.i(TAG, "Attempting to reconnect WebSocket...");
                connect();
            }
        }, reconnectInterval);
    }
    
    private void sendConnectionMessage() {
        String deviceId = preferences.getString("deviceId", "");
        String serverToken = preferences.getString("serverToken", "");
        
        JsonObject message = new JsonObject();
        message.addProperty("type", "CONNECT");
        message.addProperty("deviceId", deviceId);
        message.addProperty("authToken", serverToken);
        message.addProperty("connectionMode", "WEBSOCKET");
        
        DeviceInfo deviceInfo = deviceInfoHelper.collectDeviceInfo();
        message.addProperty("batteryLevel", deviceInfo.batteryLevel);
        message.addProperty("isCharging", deviceInfo.isCharging);
        message.addProperty("networkType", deviceInfo.networkType);
        message.addProperty("signalStrength", deviceInfo.signalStrength);
        
        sendMessage(message);
    }
    
    private void startPingTimer() {
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (client != null && client.isOpen()) {
                    JsonObject ping = new JsonObject();
                    ping.addProperty("type", "PING");
                    ping.addProperty("deviceId", preferences.getString("deviceId", ""));
                    ping.addProperty("timestamp", System.currentTimeMillis());
                    sendMessage(ping);
                    new android.os.Handler().postDelayed(this, TimeUnit.SECONDS.toMillis(30));
                }
            }
        }, TimeUnit.SECONDS.toMillis(30));
    }
    
    private void handleMessage(String messageText) {
        try {
            JsonObject message = gson.fromJson(messageText, JsonObject.class);
            if (message.has("messageId") && pendingMessages.containsKey(message.get("messageId").getAsString())) {
                String messageId = message.get("messageId").getAsString();
                MessageCallback callback = pendingMessages.get(messageId);
                pendingMessages.remove(messageId);
                callback.onResponse(message);
                return;
            }
            
            if (message.has("type")) {
                String type = message.get("type").getAsString();
                if (type.equals("PONG")) {
                    long roundTripTime = System.currentTimeMillis() - message.get("timestamp").getAsLong();
                    Log.d(TAG, "Ping-pong round trip time: " + roundTripTime + "ms");
                    return;
                }
                
                if (commandHandlers.containsKey(type)) {
                    commandHandlers.get(type).handle(message);
                } else {
                    Log.w(TAG, "No handler for message type: " + type);
                }
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
        
        try {
            if (!message.has("messageId")) {
                message.addProperty("messageId", UUID.randomUUID().toString());
            }
            if (!message.has("deviceId")) {
                message.addProperty("deviceId", preferences.getString("deviceId", ""));
            }
            if (!message.has("timestamp")) {
                message.addProperty("timestamp", System.currentTimeMillis());
            }
            
            String messageText = gson.toJson(message);
            client.send(messageText);
        } catch (Exception e) {
            Log.e(TAG, "Error sending message: " + e.getMessage());
        }
    }
    
    public void sendMessage(JsonObject message, MessageCallback callback) {
        String messageId = UUID.randomUUID().toString();
        message.addProperty("messageId", messageId);
        pendingMessages.put(messageId, callback);
        sendMessage(message);
        new android.os.Handler().postDelayed(() -> {
            if (pendingMessages.containsKey(messageId)) {
                MessageCallback cb = pendingMessages.remove(messageId);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("error", "timeout");
                cb.onResponse(errorResponse);
            }
        }, TimeUnit.SECONDS.toMillis(30));
    }
    
    public void disconnect() {
        if (client != null) {
            client.close();
            client = null;
        }
        _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
    }
    
    public void setAutoReconnect(boolean autoReconnect) {
        this.autoReconnect = autoReconnect;
    }
    
    public void setReconnectInterval(int milliseconds) {
        this.reconnectInterval = milliseconds;
    }
    
    public interface MessageCallback {
        void onResponse(JsonObject response);
    }
    
    private interface CommandHandler {
        void handle(JsonObject message);
    }
}
