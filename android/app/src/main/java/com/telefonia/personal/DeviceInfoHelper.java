package com.telefonia.personal;

import android.os.Build;
import android.provider.Settings;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

// Si tu módulo es de aplicación y tu applicationId es "com.telefonia.personal",
// BuildConfig debería generarse en ese paquete. Si no es así, o si DeviceInfoHelper no
// requiere BuildConfig, elimina o comenta la siguiente línea.
// import com.telefonia.personal.BuildConfig;

public class DeviceInfoHelper {
    
    /**
     * Recolecta información del dispositivo usando el contexto proporcionado.
     *
     * @param context El contexto de la aplicación o actividad.
     * @return Un objeto DeviceInfo con la información recolectada.
     */
    public DeviceInfo collectDeviceInfo(Context context) {
        DeviceInfo info = new DeviceInfo();
        
        // Asigna propiedades básicas del dispositivo
        info.manufacturer = Build.MANUFACTURER;
        info.model = Build.MODEL;
        info.osVersion = Build.VERSION.RELEASE;
        // Si BuildConfig no está disponible, comenta o asigna un valor predeterminado
        // info.appVersion = BuildConfig.VERSION_NAME;
        info.deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        
        // Obtener información de la batería
        BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        if (batteryManager != null) {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            if (batteryStatus != null) {
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                info.isCharging = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                   status == BatteryManager.BATTERY_STATUS_FULL);
            }
        }
        
        // Obtener información de red (Wi-Fi)
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            if (activeNetwork != null) {
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
                    WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wifiManager != null) {
                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                        info.signalStrength = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), 5);
                    }
                }
                // Puedes agregar lógica para redes móviles si es necesario
            }
        }
        
        // Obtener características del dispositivo
        PackageManager pm = context.getPackageManager();
        info.hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
        info.hasMicrophone = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        info.hasSpeaker = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
        
        return info;
    }
}
