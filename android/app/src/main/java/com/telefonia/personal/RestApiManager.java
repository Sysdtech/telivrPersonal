package com.telefonia.personal;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RestApiManager {
    private static final String TAG = "RestApiManager";
    private static RestApiManager instance;
    
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int POLLING_INTERVAL_MS = 5000; // 5 segundos
    
    private OkHttpClient client;
    private String serverUrl;
    private String deviceId;
    private boolean isPolling = false;
    private List<RestApiListener> listeners = new ArrayList<>();
    
    private Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;
    
    // Constructor privado (Singleton)
    private RestApiManager() {
        client = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
            
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                if (isPolling) {
                    pollCommands();
                    pollingHandler.postDelayed(this, POLLING_INTERVAL_MS);
                }
            }
        };
    }
    
    // Obtener instancia (Singleton)
    public static synchronized RestApiManager getInstance() {
        if (instance == null) {
            instance = new RestApiManager();
        }
        return instance;
    }
    
    // Inicializar con configuración
    public void initialize(String serverUrl, String deviceId) {
        this.serverUrl = serverUrl;
        this.deviceId = deviceId;
    }
    
    // Iniciar conexión REST
    public void connect() {
        try {
            // Enviar estado inicial del dispositivo
            JSONObject body = new JSONObject();
            body.put("deviceId", deviceId);
            body.put("status", "online");
            body.put("connectionMode", "REST_API");
            
            JSONObject deviceInfo = new JSONObject();
            deviceInfo.put("deviceModel", android.os.Build.MODEL);
            deviceInfo.put("androidVersion", android.os.Build.VERSION.RELEASE);
            deviceInfo.put("batteryLevel", 100); // Actualizar con valor real
            deviceInfo.put("networkType", "WIFI"); // Actualizar con valor real
            
            body.put("deviceInfo", deviceInfo);
            
            post("/api/public/device-connect", body, new RestApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    notifyConnectionStatus(true, "Conectado vía REST API");
                    
                    // Iniciar polling de comandos
                    startPolling();
                    
                    // Procesar comandos pendientes si los hay
                    try {
                        if (response.has("pendingCommands")) {
                            JSONObject pendingCommands = response.getJSONObject("pendingCommands");
                            processPendingCommands(pendingCommands);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error al procesar comandos pendientes", e);
                    }
                }
                
                @Override
                public void onFailure(String error) {
                    notifyConnectionStatus(false, "Error al conectar vía REST API: " + error);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error al crear cuerpo de la petición", e);
            notifyConnectionStatus(false, "Error al conectar vía REST API: " + e.getMessage());
        }
    }
    
    // Procesar comandos pendientes recibidos
    private void processPendingCommands(JSONObject commands) {
        // Implementar procesamiento de comandos según la API
    }
    
    // Iniciar polling de comandos
    private void startPolling() {
        isPolling = true;
        pollingHandler.removeCallbacks(pollingRunnable);
        pollingHandler.post(pollingRunnable);
    }
    
    // Detener polling de comandos
    private void stopPolling() {
        isPolling = false;
        pollingHandler.removeCallbacks(pollingRunnable);
    }
    
    // Consultar comandos pendientes
    private void pollCommands() {
        try {
            JSONObject body = new JSONObject();
            body.put("deviceId", deviceId);
            
            post("/api/public/device-status", body, new RestApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    try {
                        if (response.has("commands")) {
                            JSONObject commands = response.getJSONObject("commands");
                            processPendingCommands(commands);
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error al procesar comandos en polling", e);
                    }
                }
                
                @Override
                public void onFailure(String error) {
                    Log.e(TAG, "Error en polling: " + error);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error al crear cuerpo para polling", e);
        }
    }
    
    // Enviar actualización de estado de llamada
    public void sendCallStatus(String callId, String phoneNumber, String status, String direction) {
        try {
            JSONObject body = new JSONObject();
            body.put("deviceId", deviceId);
            body.put("action", "UPDATE_CALL_STATUS");
            body.put("callId", callId);
            body.put("phoneNumber", phoneNumber);
            body.put("callStatus", status);
            body.put("direction", direction);
            body.put("timestamp", System.currentTimeMillis());
            
            post("/api/public/device-call-action", body, new RestApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    Log.d(TAG, "Estado de llamada enviado correctamente");
                }
                
                @Override
                public void onFailure(String error) {
                    Log.e(TAG, "Error al enviar estado de llamada: " + error);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error al crear cuerpo para estado de llamada", e);
        }
    }
    
    // Enviar actualización de estado del dispositivo
    public void sendDeviceStatus(String status, int batteryLevel, boolean isCharging, String networkType) {
        try {
            JSONObject body = new JSONObject();
            body.put("deviceId", deviceId);
            body.put("status", status);
            body.put("batteryLevel", batteryLevel);
            body.put("isCharging", isCharging);
            body.put("networkType", networkType);
            body.put("timestamp", System.currentTimeMillis());
            
            post("/api/public/device-status", body, new RestApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    Log.d(TAG, "Estado del dispositivo enviado correctamente");
                }
                
                @Override
                public void onFailure(String error) {
                    Log.e(TAG, "Error al enviar estado del dispositivo: " + error);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error al crear cuerpo para estado del dispositivo", e);
        }
    }
    
    // Realizar vinculación con código
    public void pairWithCode(String pairingCode, final PairingCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("deviceId", deviceId);
            body.put("pairingCode", pairingCode);
            body.put("deviceModel", android.os.Build.MODEL);
            body.put("androidVersion", android.os.Build.VERSION.RELEASE);
            
            post("/api/public/device-pairing", body, new RestApiCallback() {
                @Override
                public void onSuccess(JSONObject response) {
                    boolean success = false;
                    try {
                        success = response.getBoolean("success");
                    } catch (JSONException e) {
                        Log.e(TAG, "Error al procesar respuesta de emparejamiento", e);
                    }
                    callback.onResult(success);
                }
                
                @Override
                public void onFailure(String error) {
                    Log.e(TAG, "Error en emparejamiento: " + error);
                    callback.onResult(false);
                }
            });
        } catch (JSONException e) {
            Log.e(TAG, "Error al crear cuerpo para emparejamiento", e);
            callback.onResult(false);
        }
    }
    
    // Desconectar
    public void disconnect() {
        stopPolling();
        
        try {
            JSONObject body = new JSONObject();
            body.put("deviceId", deviceId);
            body.put("status", "offline");
            
            post("/api/public/device-status", body, null);
        } catch (JSONException e) {
            Log.e(TAG, "Error al crear cuerpo para desconexión", e);
        }
        
        notifyConnectionStatus(false, "Desconectado");
    }
    
    // Método POST genérico
    private void post(String endpoint, JSONObject body, final RestApiCallback callback) {
        String url = serverUrl + endpoint;
        
        RequestBody requestBody = RequestBody.create(body.toString(), JSON);
        Request request = new Request.Builder()
            .url(url)
            .post(requestBody)
            .build();
            
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Error en petición HTTP", e);
                if (callback != null) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        callback.onFailure(e.getMessage());
                    });
                }
            }
            
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    if (callback != null) {
                        final String errorMsg = "HTTP " + response.code();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            callback.onFailure(errorMsg);
                        });
                    }
                    return;
                }
                
                if (callback != null) {
                    try {
                        final String responseString = response.body().string();
                        final JSONObject jsonResponse = new JSONObject(responseString);
                        
                        new Handler(Looper.getMainLooper()).post(() -> {
                            callback.onSuccess(jsonResponse);
                        });
                    } catch (JSONException e) {
                        Log.e(TAG, "Error al parsear respuesta JSON", e);
                        final String errorMsg = "Error al parsear respuesta: " + e.getMessage();
                        
                        new Handler(Looper.getMainLooper()).post(() -> {
                            callback.onFailure(errorMsg);
                        });
                    }
                }
            }
        });
    }
    
    // Interfaz para callback de API REST
    private interface RestApiCallback {
        void onSuccess(JSONObject response);
        void onFailure(String error);
    }
    
    // Interfaz para callback de emparejamiento
    public interface PairingCallback {
        void onResult(boolean success);
    }
    
    // Interfaz para listeners
    public interface RestApiListener {
        void onConnectionStatus(boolean connected, String message);
        void onCommandReceived(String command, JSONObject data);
    }
    
    // Añadir listener
    public void addListener(RestApiListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }
    
    // Eliminar listener
    public void removeListener(RestApiListener listener) {
        listeners.remove(listener);
    }
    
    // Notificar estado de conexión a todos los listeners
    private void notifyConnectionStatus(boolean connected, String message) {
        for (RestApiListener listener : listeners) {
            listener.onConnectionStatus(connected, message);
        }
    }
    
    // Notificar comando recibido a todos los listeners
    private void notifyCommandReceived(String command, JSONObject data) {
        for (RestApiListener listener : listeners) {
            listener.onCommandReceived(command, data);
        }
    }
}