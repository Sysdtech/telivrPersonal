package com.telefonia.personal;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private TextView statusTextView;
    private Button connectButton;
    private Button settingsButton;
    private TextInputEditText serverUrlInput;
    private TextInputEditText pairingCodeInput;
    private Button pairButton;
    private View pairingView;
    private View statusView;
    
    private WebSocketManager webSocketManager;
    private DeviceInfoHelper deviceInfoHelper;
    private SharedPreferences preferences;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Inicializar preferencias
        preferences = getSharedPreferences("TelefoniaPersonal", MODE_PRIVATE);
        
        // Inicializar vistas
        statusTextView = findViewById(R.id.statusTextView);
        connectButton = findViewById(R.id.connectButton);
        settingsButton = findViewById(R.id.settingsButton);
        serverUrlInput = findViewById(R.id.serverUrlInput);
        pairingCodeInput = findViewById(R.id.pairingCodeInput);
        pairButton = findViewById(R.id.pairButton);
        pairingView = findViewById(R.id.pairingLayout);
        statusView = findViewById(R.id.statusLayout);
        
        // Inicializar helpers
        deviceInfoHelper = new DeviceInfoHelper(this);
        webSocketManager = WebSocketManager.getInstance(this);
        
        // Verificar permisos
        checkPermissions();
        
        // Cargar URL del servidor
        String savedUrl = preferences.getString("serverUrl", "");
        if (!savedUrl.isEmpty()) {
            serverUrlInput.setText(savedUrl);
        }
        
        // Verificar si ya está emparejado
        String deviceId = preferences.getString("deviceId", "");
        if (!deviceId.isEmpty()) {
            pairingView.setVisibility(View.GONE);
            statusView.setVisibility(View.VISIBLE);
            
            // Iniciar servicio de websocket
            startWebSocketService();
        } else {
            pairingView.setVisibility(View.VISIBLE);
            statusView.setVisibility(View.GONE);
        }
        
        // Configurar listeners
        connectButton.setOnClickListener(v -> toggleConnection());
        settingsButton.setOnClickListener(v -> openSettings());
        pairButton.setOnClickListener(v -> pairDevice());
        
        // Observar estado de conexión
        observeConnectionStatus();
        
        // Procesar intent para emparejamiento
        processDeepLink(getIntent());
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        processDeepLink(intent);
    }
    
    private void processDeepLink(Intent intent) {
        if (intent != null && intent.getData() != null) {
            Uri data = intent.getData();
            if (data.getScheme().equals("telefoniaapp") && data.getHost().equals("pairing")) {
                String code = data.getQueryParameter("code");
                if (code != null && !code.isEmpty()) {
                    pairingCodeInput.setText(code);
                    
                    String server = data.getQueryParameter("server");
                    if (server != null && !server.isEmpty()) {
                        serverUrlInput.setText(server);
                    }
                    
                    Toast.makeText(this, "Código de emparejamiento cargado: " + code, Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
    
    private void observeConnectionStatus() {
        WebSocketManager.connectionStatus.observe(this, status -> {
            switch (status) {
                case CONNECTED:
                    updateStatus("Conectado al servidor", true);
                    connectButton.setText("Desconectar");
                    break;
                case CONNECTING:
                    updateStatus("Conectando...", false);
                    connectButton.setText("Cancelar");
                    break;
                case DISCONNECTED:
                    updateStatus("Desconectado", false);
                    connectButton.setText("Conectar");
                    break;
                case ERROR:
                    updateStatus("Error de conexión", false);
                    connectButton.setText("Reintentar");
                    break;
            }
        });
    }
    
    private void updateStatus(String status, boolean isConnected) {
        statusTextView.setText(status);
        statusTextView.setTextColor(ContextCompat.getColor(this, 
                isConnected ? R.color.success_green : R.color.error_red));
    }
    
    private void toggleConnection() {
        if (WebSocketManager.isConnected() || WebSocketManager.isConnecting()) {
            stopWebSocketService();
        } else {
            startWebSocketService();
        }
    }
    
    private void startWebSocketService() {
        Intent serviceIntent = new Intent(this, WebSocketService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
    }
    
    private void stopWebSocketService() {
        Intent serviceIntent = new Intent(this, WebSocketService.class);
        stopService(serviceIntent);
    }
    
    private void openSettings() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Configuración")
            .setItems(new String[]{"Configurar URL del servidor", "Borrar datos de emparejamiento", "Optimización de batería", "Notificaciones"}, (dialog, which) -> {
                switch (which) {
                    case 0:
                        showServerUrlDialog();
                        break;
                    case 1:
                        confirmUnpair();
                        break;
                    case 2:
                        openBatteryOptimizationSettings();
                        break;
                    case 3:
                        openNotificationSettings();
                        break;
                }
            })
            .show();
    }
    
    private void showServerUrlDialog() {
        View view = getLayoutInflater().inflate(R.layout.dialog_server_url, null);
        TextInputEditText urlInput = view.findViewById(R.id.urlInput);
        urlInput.setText(preferences.getString("serverUrl", ""));
        
        new MaterialAlertDialogBuilder(this)
            .setTitle("URL del Servidor")
            .setView(view)
            .setPositiveButton("Guardar", (dialog, which) -> {
                String url = urlInput.getText().toString().trim();
                if (!url.isEmpty()) {
                    preferences.edit().putString("serverUrl", url).apply();
                    Toast.makeText(this, "URL guardada", Toast.LENGTH_SHORT).show();
                    
                    // Reiniciar conexión si estaba conectado
                    if (WebSocketManager.isConnected()) {
                        stopWebSocketService();
                        startWebSocketService();
                    }
                }
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }
    
    private void confirmUnpair() {
        new MaterialAlertDialogBuilder(this)
            .setTitle("Confirmar desemparejamiento")
            .setMessage("¿Está seguro que desea borrar todos los datos de emparejamiento? Esto desconectará el dispositivo del sistema.")
            .setPositiveButton("Confirmar", (dialog, which) -> {
                // Detener servicios
                stopWebSocketService();
                stopService(new Intent(this, CallService.class));
                
                // Borrar datos de emparejamiento
                preferences.edit()
                    .remove("deviceId")
                    .remove("serverToken")
                    .apply();
                
                // Mostrar vista de emparejamiento
                pairingView.setVisibility(View.VISIBLE);
                statusView.setVisibility(View.GONE);
                
                Toast.makeText(this, "Dispositivo desemparejado", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancelar", null)
            .show();
    }
    
    private void openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } else {
            Toast.makeText(this, "No disponible en esta versión de Android", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openNotificationSettings() {
        Intent intent = new Intent();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        } else {
            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
            intent.putExtra("app_package", getPackageName());
            intent.putExtra("app_uid", getApplicationInfo().uid);
        }
        startActivity(intent);
    }
    
    private void pairDevice() {
        String serverUrl = serverUrlInput.getText().toString().trim();
        String pairingCode = pairingCodeInput.getText().toString().trim();
        
        if (serverUrl.isEmpty()) {
            Snackbar.make(pairingView, "Debe ingresar la URL del servidor", Snackbar.LENGTH_SHORT).show();
            return;
        }
        
        if (pairingCode.isEmpty()) {
            Snackbar.make(pairingView, "Debe ingresar el código de emparejamiento", Snackbar.LENGTH_SHORT).show();
            return;
        }
        
        // Guardar URL del servidor
        preferences.edit().putString("serverUrl", serverUrl).apply();
        
        // Mostrar progreso de emparejamiento
        pairButton.setEnabled(false);
        pairButton.setText("Emparejando...");
        
        // Obtener información del dispositivo
        DeviceInfo deviceInfo = deviceInfoHelper.collectDeviceInfo();
        
        // Crear petición de emparejamiento
        PairingRequest request = new PairingRequest();
        request.code = pairingCode;
        request.deviceInfo = deviceInfo;
        
        // Enviar petición al servidor (simulado aquí)
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Simular tiempo de petición
                
                // Simular respuesta exitosa
                PairingResponse response = new PairingResponse();
                response.success = true;
                response.deviceId = "DEV" + System.currentTimeMillis();
                response.serverToken = "TOKEN" + System.currentTimeMillis();
                
                runOnUiThread(() -> handlePairingResponse(response));
            } catch (Exception e) {
                runOnUiThread(() -> {
                    pairButton.setEnabled(true);
                    pairButton.setText("Emparejar");
                    Snackbar.make(pairingView, "Error al emparejar: " + e.getMessage(), Snackbar.LENGTH_LONG).show();
                });
            }
        }).start();
    }
    
    private void handlePairingResponse(PairingResponse response) {
        if (response.success) {
            // Guardar datos de emparejamiento
            preferences.edit()
                .putString("deviceId", response.deviceId)
                .putString("serverToken", response.serverToken)
                .apply();
            
            // Mostrar vista de estado
            pairingView.setVisibility(View.GONE);
            statusView.setVisibility(View.VISIBLE);
            
            // Iniciar servicio de websocket
            startWebSocketService();
            
            Toast.makeText(this, "Dispositivo emparejado con éxito", Toast.LENGTH_SHORT).show();
        } else {
            pairButton.setEnabled(true);
            pairButton.setText("Emparejar");
            Snackbar.make(pairingView, "Error: " + response.message, Snackbar.LENGTH_LONG).show();
        }
    }
    
    private void checkPermissions() {
        Dexter.withContext(this)
            .withPermissions(
                Manifest.permission.READ_PHONE_STATE,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            .withListener(new MultiplePermissionsListener() {
                @Override
                public void onPermissionsChecked(MultiplePermissionsReport report) {
                    if (report.areAllPermissionsGranted()) {
                        // Todos los permisos concedidos
                    } else {
                        new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle("Permisos requeridos")
                            .setMessage("Esta aplicación necesita permisos para funcionar correctamente. Por favor, conceda todos los permisos solicitados.")
                            .setPositiveButton("Configuración", (dialog, which) -> {
                                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                intent.setData(Uri.fromParts("package", getPackageName(), null));
                                startActivity(intent);
                            })
                            .setNegativeButton("Cancelar", null)
                            .show();
                    }
                }

                @Override
                public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                    token.continuePermissionRequest();
                }
            })
            .check();
    }
    
    // Clases auxiliares para el emparejamiento
    private static class PairingRequest {
        String code;
        DeviceInfo deviceInfo;
    }
    
    private static class PairingResponse {
        boolean success;
        String message;
        String deviceId;
        String serverToken;
    }
}