public DeviceInfo collectDeviceInfo() {
    DeviceInfo info = new DeviceInfo();
    
    // Llenar la información del dispositivo
    info.manufacturer = Build.MANUFACTURER;
    info.model = Build.MODEL;
    info.osVersion = Build.VERSION.RELEASE;
    info.appVersion = BuildConfig.VERSION_NAME;
    info.deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    
    // Estado de batería
    BatteryManager batteryManager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
    if (batteryManager != null) {
        info.batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        info.isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                         status == BatteryManager.BATTERY_STATUS_FULL;
    }
    
    // Información de red
    ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
    if (activeNetwork != null) {
        info.networkType = activeNetwork.getTypeName();
        
        // Obtenemos fuerza de señal
        if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {
            WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            info.signalStrength = WifiManager.calculateSignalLevel(wifiInfo.getRssi(), 5);
        } else if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
            if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    SignalStrength signalStrength = tm.getSignalStrength();
                    if (signalStrength != null) {
                        info.signalStrength = signalStrength.getLevel();
                    }
                }
            }
        }
    }
    
    // Capacidades del dispositivo
    PackageManager pm = context.getPackageManager();
    info.hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA);
    info.hasMicrophone = pm.hasSystemFeature(PackageManager.FEATURE_MICROPHONE);
    info.hasSpeaker = pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT);
    
    return info;
}
