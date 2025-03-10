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

// Ajusta la importación de BuildConfig según tu applicationId. 
// Si tu applicationId es "com.telefonia.personal", BuildConfig se generará ahí.
import com.telefonia.personal.BuildConfig;

public class DeviceInfoHelper {
    
    private Context context;
    
    public DeviceInfoHelper(Context context) {
        this.context = context;
    }
    
    public DeviceInfo collectDeviceInfo() {
        DeviceInfo info = new DeviceInfo();
        
        info.manufacturer = Build.MANUFACTURER;
        info.model = Build.MODEL;
        info.osVersion = Build.VERSION.RELEASE;
        // Si BuildConfig no está disponible en este paquete, ajusta la importación.
        info.appVersion = BuildConfig.VERSION_NAME;
        info.deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        
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
            }
        }
        
        PackageManager pm = context.getPackageManager();
        info.hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
        info.hasMicrophone = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
        info.hasSpeaker = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
        
        return info;
    }
}
