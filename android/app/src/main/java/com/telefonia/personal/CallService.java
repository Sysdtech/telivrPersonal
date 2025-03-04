package com.telefonia.personal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.telecom.TelecomManager;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.util.UUID;

public class CallService extends Service {
    private static final String TAG = "CallService";
    private static final String CHANNEL_ID = "TelefoniaPersonalChannel";
    private static final int NOTIFICATION_ID = 1001;
    
    private static CallService instance;
    private WebSocketManager webSocketManager;
    private String currentCallId;
    private String currentPhoneNumber;
    private boolean isCallActive = false;
    
    public static void initiateCall(Context context, String phoneNumber, String callId) {
        if (instance == null) {
            Log.e(TAG, "CallService no está en ejecución");
            return;
        }
        instance.makeCall(phoneNumber, callId);
    }
    
    public static void endCall(Context context) {
        if (instance == null) {
            Log.e(TAG, "CallService no está en ejecución");
            return;
        }
        instance.hangup();
    }
    
    public static void restart(Context context) {
        Intent intent = new Intent(context, CallService.class);
        context.stopService(intent);
        context.startForegroundService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Servicio activo"));
        webSocketManager = WebSocketManager.getInstance(this);
        Log.i(TAG, "CallService iniciado");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.i(TAG, "CallService detenido");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Telefonía Personal",
                    NotificationManager.IMPORTANCE_LOW);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String contentText) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Telefonía Personal")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void makeCall(String phoneNumber, String callId) {
        if (isCallActive) {
            hangup();
        }
        Log.i(TAG, "Llamando a: " + phoneNumber);
        try {
            currentCallId = callId != null ? callId : UUID.randomUUID().toString();
            currentPhoneNumber = phoneNumber;
            isCallActive = true;
            Uri uri = Uri.parse("tel:" + phoneNumber);
            Intent callIntent = new Intent(Intent.ACTION_CALL, uri);
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callIntent);
            webSocketManager.sendCallStatus(currentCallId, "dialing", phoneNumber, "outgoing", 0);
        } catch (Exception e) {
            Log.e(TAG, "Error al iniciar llamada", e);
            isCallActive = false;
        }
    }
    
    private void hangup() {
        Log.i(TAG, "Finalizando llamada");
        try {
            TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && telecomManager != null) {
                telecomManager.endCall();
            }
            isCallActive = false;
            currentCallId = null;
            currentPhoneNumber = null;
        } catch (Exception e) {
            Log.e(TAG, "Error al finalizar llamada", e);
        }
    }
}
