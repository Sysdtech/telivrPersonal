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
    
    private final Map<String, MessageCallback> pendingMessages = new ConcurrentHashMap<>();
    private final Map<String, CommandHandler> commandHandlers = new ConcurrentHashMap<>();
    
    // Estado de conexión
    public enum ConnectionStatus {
        CONNECTING, CONNECTED, DISCONNECTED, ERROR
    }
    
    private static final MutableLiveData<ConnectionStatus> _connectionStatus = new MutableLiveData<>(ConnectionStatus.DISCONNECTED);
    public static final LiveData<ConnectionStatus> connectionStatus = _connectionStatus;
    
    // Intervalo de reconexión
    private int reconnectInterval = 5000; // 5 segundos
    private boolean autoReconnect = true;
    
    private WebSocketManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = context.getSharedPreferences("TelefoniaPersonal", Context.MODE_PRIVATE);
        this.gson = new Gson();
        this.deviceInfoHelper = new DeviceInfoHelper(context);
        
        // Registrar manejadores de comandos
        registerCommandHandlers();
    }
    
    public static synchronized WebSocketManager getInstance(Context context) {
        if (instance == null) {
            instance = new WebSocketManager(context);
        }
        return instance;
    }
    
    public static boolean isConnected() {
        return instance != null && instance.client != null && 
               instance.client.isOpen() && _connectionStatus.getValue() == ConnectionStatus.CONNECTED;
    }
    
    public static boolean isConnecting() {
        return _connectionStatus.getValue() == ConnectionStatus.CONNECTING;
    }
    
    private void registerCommandHandlers() {
        // Manejador para llamadas
        commandHandlers.put("CALL", (message) -> {
            String callId = message.get("callId").getAsString();
            String phoneNumber = message.get("phoneNumber").getAsString();
            String direction = message.get("direction").getAsString();
            
            // Iniciar servicio de llamada
            CallService.startCall(context, callId, phoneNumber, direction);
            
            // Responder confirmación
            JsonObject response = new JsonObject();
            response.addProperty("type", "CALL_RESPONSE");
            response.addProperty("callId", callId);
            response.addProperty("status", "accepted");
            sendMessage(response);
        });
        
        // Manejador para finalizar llamadas
        commandHandlers.put("END_CALL", (message) -> {
            String callId = message.get("callId").getAsString();
            
            // Finalizar llamada
            CallService.endCall(context, callId);
            
            // Responder confirmación
            JsonObject response = new JsonObject();
            response.addProperty("type", "END_CALL_RESPONSE");
            response.addProperty("callId", callId);
            response.addProperty("status", "completed");
            sendMessage(response);
        });
        
        // Manejador para enviar tonos DTMF
        commandHandlers.put("SEND_DTMF", (message) -> {
            String callId = message.get("callId").getAsString();
            String digit = message.get("digit").getAsString();
            
            // Enviar DTMF
            CallService.sendDtmf(context, callId, digit);
            
            // Responder confirmación
            JsonObject response = new JsonObject();
            response.addProperty("type", "DTMF_RESPONSE");
            response.addProperty("callId", callId);
            response.addProperty("digit", digit);
            response.addProperty("status", "sent");
            sendMessage(response);
        });
        
        // Manejador para reproducir audio
        commandHandlers.put("PLAY_AUDIO", (message) -> {
            String callId = message.get("callId").getAsString();
            int audioFileId = message.get("audioFileId").getAsInt();
            
            // Reproducir audio
            CallService.playAudio(context, callId, audioFileId);
            
            // Responder confirmación
            JsonObject response = new JsonObject();
            response.addProperty("type", "PLAY_AUDIO_RESPONSE");
            response.addProperty("callId", callId);
            response.addProperty("audioFileId", audioFileId);
            response.addProperty("status", "playing");
            sendMessage(response);
        });
        
        // Manejador para obtener estado del dispositivo
        commandHandlers.put("GET_STATUS", (message) -> {
            // Obtener información del dispositivo
            DeviceInfo deviceInfo = deviceInfoHelper.collectDeviceInfo();
            
            // Responder con el estado actual
            JsonObject response = new JsonObject();
            response.addProperty("type", "STATUS_RESPONSE");
            response.addProperty("status", "online");
            response.addProperty("batteryLevel", deviceInfo.batteryLevel);
            response.addProperty("isCharging", deviceInfo.isCharging);
            response.addProperty("networkType", deviceInfo.networkType);
            response.addProperty("signalStrength", deviceInfo.signalStrength);
            sendMessage(response);
        });
        
        // Manejador para reiniciar el dispositivo (reinicia la aplicación)
        commandHandlers.put("RESTART", (message) -> {
            // Responder primero para confirmar recepción
            JsonObject response = new JsonObject();
            response.addProperty("type", "RESTART_RESPONSE");
            response.addProperty("status", "restarting");
            sendMessage(response);
            
            // Programar reinicio de la aplicación
            new android.os.Handler().postDelayed(() -> {
                System.exit(0); // Esto reiniciará la aplicación
            }, 3000);
        });
    }
    
    public void connect() {
        // Si ya hay una conexión, cerrarla
        if (client != null && (client.isOpen() || client.isConnecting())) {
            client.close();
        }
        
        // Obtener URL del servidor
        String serverUrl = preferences.getString("serverUrl", "");
        if (serverUrl.isEmpty()) {
            Log.e(TAG, "No server URL configured");
            _connectionStatus.postValue(ConnectionStatus.ERROR);
            return;
        }
        
        // Asegurarse de que la URL sea para WebSocket
        if (!serverUrl.startsWith("ws://") && !serverUrl.startsWith("wss://")) {
            if (serverUrl.startsWith("http://")) {
                serverUrl = serverUrl.replace("http://", "ws://");
            } else if (serverUrl.startsWith("https://")) {
                serverUrl = serverUrl.replace("https://", "wss://");
            } else {
                serverUrl = "ws://" + serverUrl;
            }
        }
        
        // Añadir path de websocket si no está presente
        if (!serverUrl.endsWith("/ws")) {
            serverUrl = serverUrl.endsWith("/") ? serverUrl + "ws" : serverUrl + "/ws";
        }
        
        // Obtener información del dispositivo
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
                    
                    // Enviar mensaje de conexión
                    sendConnectionMessage();
                    
                    // Iniciar ping periódico
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
                    
                    // Intentar reconectar si es automático
                    if (autoReconnect) {
                        reconnect();
                    }
                }
                
                @Override
                public void onError(Exception ex) {
                    Log.e(TAG, "WebSocket error: " + ex.getMessage());
                    _connectionStatus.postValue(ConnectionStatus.ERROR);
                    
                    // Intentar reconectar si es automático
                    if (autoReconnect) {
                        reconnect();
                    }
                }
            };
            
            // Conectar
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
        
        // Incluir información del dispositivo
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
                    // Enviar mensaje de ping
                    JsonObject ping = new JsonObject();
                    ping.addProperty("type", "PING");
                    ping.addProperty("deviceId", preferences.getString("deviceId", ""));
                    ping.addProperty("timestamp", System.currentTimeMillis());
                    
                    sendMessage(ping);
                    
                    // Programar siguiente ping
                    new android.os.Handler().postDelayed(this, TimeUnit.SECONDS.toMillis(30)); // Cada 30 segundos
                }
            }
        }, TimeUnit.SECONDS.toMillis(30));
    }
    
    private void handleMessage(String messageText) {
        try {
            JsonObject message = gson.fromJson(messageText, JsonObject.class);
            
            // Verificar si es respuesta a un mensaje anterior
            if (message.has("messageId") && pendingMessages.containsKey(message.get("messageId").getAsString())) {
                String messageId = message.get("messageId").getAsString();
                MessageCallback callback = pendingMessages.get(messageId);
                pendingMessages.remove(messageId);
                callback.onResponse(message);
                return;
            }
            
            // Manejar por tipo de mensaje
            if (message.has("type")) {
                String type = message.get("type").getAsString();
                
                // Manejar PONG
                if (type.equals("PONG")) {
                    long roundTripTime = System.currentTimeMillis() - message.get("timestamp").getAsLong();
                    Log.d(TAG, "Ping-pong round trip time: " + roundTripTime + "ms");
                    return;
                }
                
                // Verificar si hay un manejador para este comando
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
            // Añadir ID de mensaje si no tiene
            if (!message.has("messageId")) {
                message.addProperty("messageId", UUID.randomUUID().toString());
            }
            
            // Añadir deviceId si no tiene
            if (!message.has("deviceId")) {
                message.addProperty("deviceId", preferences.getString("deviceId", ""));
            }
            
            // Añadir timestamp si no tiene
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
        
        // Registrar callback
        pendingMessages.put(messageId, callback);
        
        // Enviar mensaje
        sendMessage(message);
        
        // Timeout para eliminar el callback después de cierto tiempo
        new android.os.Handler().postDelayed(() -> {
            if (pendingMessages.containsKey(messageId)) {
                MessageCallback cb = pendingMessages.remove(messageId);
                JsonObject errorResponse = new JsonObject();
                errorResponse.addProperty("error", "timeout");
                cb.onResponse(errorResponse);
            }
        }, TimeUnit.SECONDS.toMillis(30)); // 30 segundos de timeout
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
    
    // Interfaces para callbacks y manejadores
    public interface MessageCallback {
        void onResponse(JsonObject response);
    }
    
    private interface CommandHandler {
        void handle(JsonObject message);
    }
}