package com.telefonia.personal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.telecom.TelecomManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.util.UUID;

public class CallService extends Service {
    private static final String TAG = "CallService";
    private static final String CHANNEL_ID = "TelefoniaPersonalChannel";
    private static final int NOTIFICATION_ID = 1001;

    private static CallService instance;
    private WebSocketManager webSocketManager;
    private String currentCallId;
    private String currentPhoneNumber;
    private String currentCallDirection;
    private long callStartTime;
    private boolean isCallActive = false;
    private MediaPlayer mediaPlayer;

    public static void initiateCall(Context context, String phoneNumber, String callId) {
        if (instance == null) {
            Log.e(TAG, "CallService no está en ejecución");
            return;
        }
        instance.makeCall(phoneNumber, callId);
    }

    public static void answerCall(Context context) {
        if (instance == null) {
            Log.e(TAG, "CallService no está en ejecución");
            return;
        }
        instance.answer();
    }

    public static void endCall(Context context) {
        if (instance == null) {
            Log.e(TAG, "CallService no está en ejecución");
            return;
        }
        instance.hangup();
    }

    public static void playAudio(Context context, int audioFileId) {
        if (instance == null) {
            Log.e(TAG, "CallService no está en ejecución");
            return;
        }
        instance.playAudioFile(audioFileId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Servicio de telefonía activo"));

        webSocketManager = WebSocketManager.getInstance(this);
        Log.i(TAG, "CallService iniciado");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; // Mantener el servicio en ejecución
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

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error al liberar MediaPlayer", e);
            }
        }

        Log.i(TAG, "CallService detenido");
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Telefonía Personal",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Canal para el servicio de telefonía");

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Telefonía Personal")
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void makeCall(String phoneNumber, String callId) {
        if (isCallActive) {
            hangup();
        }

        Log.i(TAG, "Iniciando llamada a: " + phoneNumber);

        try {
            currentCallId = (callId != null) ? callId : UUID.randomUUID().toString();
            currentPhoneNumber = phoneNumber;
            currentCallDirection = "outgoing";

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, createNotification("Llamando a: " + phoneNumber));
            }

            webSocketManager.sendCallStatus(currentCallId, "dialing", phoneNumber, "outgoing", 0);

            Uri uri = Uri.parse("tel:" + phoneNumber);
            Intent callIntent = new Intent(Intent.ACTION_CALL, uri);
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callIntent);

            callStartTime = System.currentTimeMillis();
            isCallActive = true;
        } catch (Exception e) {
            Log.e(TAG, "Error al iniciar llamada", e);
            webSocketManager.sendCallStatus(currentCallId, "failed", phoneNumber, "outgoing", 0);
            isCallActive = false;
        }
    }

    private void answer() {
        Log.i(TAG, "Contestando llamada entrante");

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                TelecomManager telecomManager = (TelecomManager) getSystemService(Context.TELECOM_SERVICE);
                if (telecomManager != null) {
                    telecomManager.acceptRingingCall();
                }
            }

            isCallActive = true;
            callStartTime = System.currentTimeMillis();
            currentCallDirection = "incoming";

            if (currentCallId != null && currentPhoneNumber != null) {
                webSocketManager.sendCallStatus(currentCallId, "active", currentPhoneNumber, "incoming", 0);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al contestar llamada", e);
        }
    }

    private void hangup() {
        Log.i(TAG, "Finalizando llamada");
        isCallActive = false;

        webSocketManager.sendCallStatus(currentCallId, "ended", currentPhoneNumber, currentCallDirection, 0);
        currentCallId = null;
        currentPhoneNumber = null;
        callStartTime = 0;

        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, createNotification("Servicio de telefonía activo"));
        }
    }

    private void playAudioFile(int audioFileId) {
        Log.i(TAG, "Reproduciendo archivo de audio ID: " + audioFileId);

        if (!isCallActive) {
            Log.w(TAG, "No hay llamada activa para reproducir audio");
            return;
        }

        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            }

            String serverUrl = getServerUrlBase();
            String audioUrl = serverUrl + "/api/audio-files/" + audioFileId + "/download";

            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build());

            mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(audioUrl));
            mediaPlayer.prepare();
            mediaPlayer.start();

            mediaPlayer.setOnCompletionListener(mp -> Log.i(TAG, "Reproducción de audio finalizada"));

        } catch (IOException e) {
            Log.e(TAG, "Error al cargar archivo de audio", e);
        } catch (Exception e) {
            Log.e(TAG, "Error al reproducir audio", e);
        }
    }

    private String getServerUrlBase() {
        String wsUrl = webSocketManager.getServerUrl();
        if (wsUrl != null) {
            return wsUrl.replace("ws://", "http://").replace("wss://", "https://").replace("/ws", "");
        }
        return "";
    }
}
