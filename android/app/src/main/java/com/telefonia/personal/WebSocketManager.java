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
    private Context context;
    private SharedPreferences preferences;
    private Gson gson;
    private WebSocketClient client;
    private DeviceInfoHelper deviceInfoHelper;

    public enum ConnectionStatus {
        CONNECTING, CONNECTED, DISCONNECTED, ERROR
    }
    
    private static final MutableLiveData<ConnectionStatus> _connectionStatus = new MutableLiveData<>(ConnectionStatus.DISCONNECTED);
    public static final LiveData<ConnectionStatus> connectionStatus = _connectionStatus;
    private int reconnectInterval = 5000; 
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

    public boolean isConnected() {
        return client != null && client.isOpen() && _connectionStatus.getValue() == ConnectionStatus.CONNECTED;
    }
    
    public boolean isConnecting() {
        return _connectionStatus.getValue() == ConnectionStatus.CONNECTING;
    }

    private void registerCommandHandlers() {
        commandHandlers.put("CALL", (message) -> {
            String callId = message.get("callId").getAsString();
            String phoneNumber = message.get("phoneNumber").getAsString();
            String direction = message.get("direction").getAsString();
            CallService.initiateCall(context, phoneNumber, callId);
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
                    Log.i(TAG, "WebSocket connected");
                    _connectionStatus.postValue(ConnectionStatus.CONNECTED);
                }
                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.w(TAG, "WebSocket closed: " + reason);
                    _connectionStatus.postValue(ConnectionStatus.DISCONNECTED);
                }
                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error: " + ex.getMessage());
                    _connectionStatus.postValue(ConnectionStatus.ERROR);
                }
            };
            client.connect();
        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid WebSocket URI", e);
            _connectionStatus.postValue(ConnectionStatus.ERROR);
        }
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
