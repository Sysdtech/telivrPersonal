package com.telefonia.personal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PhoneStateReceiver extends BroadcastReceiver {
    private static final String TAG = "PhoneStateReceiver";
    private String lastState = TelephonyManager.EXTRA_STATE_IDLE;
    private String lastPhoneNumber = "";
    private long callStartTime = 0;

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences preferences = context.getSharedPreferences("TelefoniaPersonal", Context.MODE_PRIVATE);
        String deviceId = preferences.getString("device_id", "");
        
        if (deviceId.isEmpty()) {
            return;
        }
        
        String action = intent.getAction();
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d(TAG, "Sistema iniciado, arrancando servicio");
            startService(context);
            return;
        }
        
        if (TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action) || Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
            handlePhoneStateChanged(context, intent);
        }
    }
    
    private void handlePhoneStateChanged(Context context, Intent intent) {
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        
        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(intent.getAction())) {
            String phoneNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            Log.d(TAG, "Llamada saliente a: " + phoneNumber);
            lastPhoneNumber = phoneNumber;
            notifyCallStarted(context, phoneNumber, "outgoing");
            return;
        }
        
        if (state == null) {
            return;
        }
        
        String phoneNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER);
        
        switch (state) {
            case TelephonyManager.EXTRA_STATE_RINGING:
                if (phoneNumber != null) {
                    Log.d(TAG, "Llamada entrante de: " + phoneNumber);
                    lastPhoneNumber = phoneNumber;
                    notifyCallStarted(context, phoneNumber, "incoming");
                }
                break;
                
            case TelephonyManager.EXTRA_STATE_OFFHOOK:
                if (lastState.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                    Log.d(TAG, "Llamada contestada");
                    notifyCallAnswered(context);
                }
                callStartTime = System.currentTimeMillis();
                break;
                
            case TelephonyManager.EXTRA_STATE_IDLE:
                if (!lastState.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                    int duration = 0;
                    if (callStartTime > 0) {
                        duration = (int) ((System.currentTimeMillis() - callStartTime) / 1000);
                    }
                    Log.d(TAG, "Llamada finalizada, duración: " + duration + " segundos");
                    notifyCallEnded(context, duration);
                    callStartTime = 0;
                }
                break;
        }
        
        lastState = state;
    }
    
    private void notifyCallStarted(Context context, String phoneNumber, String direction) {
        String callId = java.util.UUID.randomUUID().toString();
        SharedPreferences preferences = context.getSharedPreferences("TelefoniaPersonal", Context.MODE_PRIVATE);
        preferences.edit().putString("current_call_id", callId).apply();
        preferences.edit().putString("current_phone_number", phoneNumber).apply();
        preferences.edit().putString("current_call_direction", direction).apply();
        
        Intent serviceIntent = new Intent(context, CallService.class);
        serviceIntent.setAction("com.telefonia.personal.CALL_STARTED");
        serviceIntent.putExtra("callId", callId);
        serviceIntent.putExtra("phoneNumber", phoneNumber);
        serviceIntent.putExtra("direction", direction);
        startService(context, serviceIntent);
    }
    
    private void notifyCallAnswered(Context context) {
        SharedPreferences preferences = context.getSharedPreferences("TelefoniaPersonal", Context.MODE_PRIVATE);
        String callId = preferences.getString("current_call_id", "");
        
        if (!callId.isEmpty()) {
            Intent serviceIntent = new Intent(context, CallService.class);
            serviceIntent.setAction("com.telefonia.personal.CALL_ANSWERED");
            serviceIntent.putExtra("callId", callId);
            startService(context, serviceIntent);
        }
    }
    
    private void notifyCallEnded(Context context, int duration) {
        SharedPreferences preferences = context.getSharedPreferences("TelefoniaPersonal", Context.MODE_PRIVATE);
        String callId = preferences.getString("current_call_id", "");
        
        if (!callId.isEmpty()) {
            Intent serviceIntent = new Intent(context, CallService.class);
            serviceIntent.setAction("com.telefonia.personal.CALL_ENDED");
            serviceIntent.putExtra("callId", callId);
            serviceIntent.putExtra("duration", duration);
            startService(context, serviceIntent);
        }
    }
    
    private void startService(Context context) {
        Intent serviceIntent = new Intent(context, CallService.class);
        serviceIntent.setAction("com.telefonia.personal.START_SERVICE");
        startService(context, serviceIntent);
    }
    
    private void startService(Context context, Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }
}
