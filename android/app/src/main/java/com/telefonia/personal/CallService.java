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
import android.telephony.TelephonyManager;
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
    
    // Métodos estáticos para comunicación desde WebSocketManager
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
    
    public static void sendDtmf(Context context, String digit) {
        if (instance == null) {
            Log.e(TAG, "CallService no está en ejecución");
            return;
        }
        
        instance.sendDtmfTone(digit);
    }
    
    public static void playAudio(Context context, int audioFileId) {
        if (instance == null) {
            Log.e(TAG, "CallService no está en ejecución");
            return;
        }
        
        instance.playAudioFile(audioFileId);
    }
    
    public static void restart(Context context) {
        Intent intent = new Intent(context, CallService.class);
        context.stopService(intent);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification("Servicio de telefonía activo"));
        
        webSocketManager = WebSocketManager.getInstance();
        webSocketManager.init(this);
        
        Log.i(TAG, "CallService iniciado");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Mantener el servicio en ejecución
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
        
        // Liberar recursos
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
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
                    NotificationManager.IMPORTANCE_LOW);
            
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
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);
        
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
            // Ya hay una llamada en curso, finalizarla primero
            hangup();
        }
        
        Log.i(TAG, "Iniciando llamada a: " + phoneNumber);
        
        try {
            currentCallId = callId != null ? callId : UUID.randomUUID().toString();
            currentPhoneNumber = phoneNumber;
            currentCallDirection = "outgoing";
            
            // Actualizar notificación
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, createNotification("Llamando a: " + phoneNumber));
            }
            
            // Enviar update de estado "calling"
            webSocketManager.sendCallStatus(currentCallId, "dialing", phoneNumber, "outgoing", 0);
            
            // Iniciar llamada usando Intent
            Uri uri = Uri.parse("tel:" + phoneNumber);
            Intent callIntent = new Intent(Intent.ACTION_CALL, uri);
            callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(callIntent);
            
            // Registrar tiempo de inicio
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
            
            // Actualizar estado
            isCallActive = true;
            callStartTime = System.currentTimeMillis();
            currentCallDirection = "incoming";
            
            // Enviar update de estado
            if (currentCallId != null && currentPhoneNumber != null) {
                webSocketManager.sendCallStatus(currentCallId, "active", currentPhoneNumber, "incoming", 0);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error al contestar llamada", e);
        }
    }

    private void hangup() {
        Log.i(TAG, "Finalizando llamada");
        
        try {
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            
            // Método indirecto: la forma recomendada es usar TelecomManager en API 26+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (telephonyManager != null) {
                    telephonyManager.endCall();
                }
            } else {
                // Fallback para versiones anteriores: mostrar pantalla de teléfono
                // para que el usuario cuelgue manualmente
                Intent intent = new Intent(Intent.ACTION_MAIN);
                intent.addCategory(Intent.CATEGORY_HOME);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
            
            // Detener reproducción de audio si está activa
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            
            // Calcular duración
            int duration = 0;
            if (callStartTime > 0) {
                duration = (int) ((System.currentTimeMillis() - callStartTime) / 1000);
            }
            
            // Enviar update de estado
            if (currentCallId != null && currentPhoneNumber != null) {
                webSocketManager.sendCallStatus(currentCallId, "ended", currentPhoneNumber, 
                        currentCallDirection, duration);
            }
            
            // Resetear estado
            isCallActive = false;
            currentCallId = null;
            currentPhoneNumber = null;
            callStartTime = 0;
            
            // Actualizar notificación
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                notificationManager.notify(NOTIFICATION_ID, createNotification("Servicio de telefonía activo"));
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error al finalizar llamada", e);
        }
    }

    private void sendDtmfTone(String digit) {
        Log.i(TAG, "Enviando tono DTMF: " + digit);
        
        if (!isCallActive) {
            Log.w(TAG, "No hay llamada activa para enviar tono DTMF");
            return;
        }
        
        try {
            // Usar AudioManager para enviar tonos DTMF
            AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audioManager != null) {
                // Convertir digit a int
                int dtmfTone;
                switch (digit) {
                    case "0": dtmfTone = ToneGenerator.TONE_DTMF_0; break;
                    case "1": dtmfTone = ToneGenerator.TONE_DTMF_1; break;
                    case "2": dtmfTone = ToneGenerator.TONE_DTMF_2; break;
                    case "3": dtmfTone = ToneGenerator.TONE_DTMF_3; break;
                    case "4": dtmfTone = ToneGenerator.TONE_DTMF_4; break;
                    case "5": dtmfTone = ToneGenerator.TONE_DTMF_5; break;
                    case "6": dtmfTone = ToneGenerator.TONE_DTMF_6; break;
                    case "7": dtmfTone = ToneGenerator.TONE_DTMF_7; break;
                    case "8": dtmfTone = ToneGenerator.TONE_DTMF_8; break;
                    case "9": dtmfTone = ToneGenerator.TONE_DTMF_9; break;
                    case "*": dtmfTone = ToneGenerator.TONE_DTMF_S; break;
                    case "#": dtmfTone = ToneGenerator.TONE_DTMF_P; break;
                    default: dtmfTone = -1;
                }
                
                if (dtmfTone != -1) {
                    // Este método está marcado como obsoleto, pero es necesario
                    // para enviar DTMF durante una llamada
                    ToneGenerator toneGenerator = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100);
                    toneGenerator.startTone(dtmfTone, 150);
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Error al enviar tono DTMF", e);
        }
    }

    private void playAudioFile(int audioFileId) {
        Log.i(TAG, "Reproduciendo archivo de audio ID: " + audioFileId);
        
        if (!isCallActive) {
            Log.w(TAG, "No hay llamada activa para reproducir audio");
            return;
        }
        
        try {
            // Detener reproducción anterior si existe
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
            }
            
            // Construir URL para obtener el archivo
            String serverUrl = getServerUrlBase();
            String audioUrl = serverUrl + "/api/audio-files/" + audioFileId + "/download";
            
            // Crear nuevo MediaPlayer
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build());
            
            // Configurar para reproducir en llamada
            mediaPlayer.setDataSource(getApplicationContext(), Uri.parse(audioUrl));
            mediaPlayer.prepare();
            mediaPlayer.start();
            
            // Evento de finalización
            mediaPlayer.setOnCompletionListener(mp -> {
                Log.i(TAG, "Reproducción de audio finalizada");
            });
            
        } catch (IOException e) {
            Log.e(TAG, "Error al cargar archivo de audio", e);
        } catch (Exception e) {
            Log.e(TAG, "Error al reproducir audio", e);
        }
    }

    private String getServerUrlBase() {
        String wsUrl = webSocketManager.getServerUrl();
        if (wsUrl != null) {
            // Convertir ws:// a http:// o wss:// a https://
            return wsUrl.replace("ws://", "http://").replace("wss://", "https://").replace("/ws", "");
        }
        return null;
    }
    
    // Clase auxiliar para generar tonos DTMF
    private static class ToneGenerator {
        public static final int TONE_DTMF_0 = 0;
        public static final int TONE_DTMF_1 = 1;
        public static final int TONE_DTMF_2 = 2;
        public static final int TONE_DTMF_3 = 3;
        public static final int TONE_DTMF_4 = 4;
        public static final int TONE_DTMF_5 = 5;
        public static final int TONE_DTMF_6 = 6;
        public static final int TONE_DTMF_7 = 7;
        public static final int TONE_DTMF_8 = 8;
        public static final int TONE_DTMF_9 = 9;
        public static final int TONE_DTMF_S = 10; // *
        public static final int TONE_DTMF_P = 11; // #
        
        public ToneGenerator(int streamType, int volume) {
            // Constructor vacío, implementación simulada
        }
        
        public void startTone(int tone, int durationMs) {
            // Implementación simulada
            Log.d(TAG, "Enviando tono DTMF: " + tone + " durante " + durationMs + "ms");
        }
    }
}