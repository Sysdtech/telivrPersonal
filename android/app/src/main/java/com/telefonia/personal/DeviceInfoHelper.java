package com.telefonia.personal;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

/**
 * Clase de utilidad para obtener información del dispositivo.
 */
public class DeviceInfoHelper {
    private static final String TAG = "DeviceInfoHelper";

    /**
     * Obtiene el nivel de batería actual (0-100)
     */
    public static int getBatteryLevel(Context context) {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, filter);
            
            int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
            int scale = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1) : -1;
            
            if (level == -1 || scale == -1) {
                return 50; // Valor por defecto si no se puede obtener
            }
            
            return (int) ((level / (float) scale) * 100);
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener nivel de batería", e);
            return 50; // Valor por defecto en caso de error
        }
    }
    
    /**
     * Comprueba si el dispositivo está conectado a la corriente
     */
    public static boolean isCharging(Context context) {
        try {
            IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, filter);
            
            if (batteryStatus == null) {
                return false;
            }
            
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            return status == BatteryManager.BATTERY_STATUS_CHARGING || 
                   status == BatteryManager.BATTERY_STATUS_FULL;
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener estado de carga", e);
            return false;
        }
    }
    
    /**
     * Obtiene la fuerza de la señal telefónica (0-4)
     */
    public static int getSignalStrength(Context context) {
        try {
            // En Android moderno, obtener la fuerza de la señal requiere permisos especiales
            // y llamadas a APIs más complejas, por lo que simplificamos con un valor estimado
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm.getActiveNetworkInfo();
            
            if (info == null || !info.isConnected()) {
                return 0;
            }
            
            // Estimación en base a la tecnología de red
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                return 2;
            }
            
            switch (tm.getNetworkType()) {
                case TelephonyManager.NETWORK_TYPE_GPRS:
                case TelephonyManager.NETWORK_TYPE_EDGE:
                case TelephonyManager.NETWORK_TYPE_CDMA:
                case TelephonyManager.NETWORK_TYPE_1xRTT:
                case TelephonyManager.NETWORK_TYPE_IDEN:
                    return 1; // 2G
                case TelephonyManager.NETWORK_TYPE_UMTS:
                case TelephonyManager.NETWORK_TYPE_EVDO_0:
                case TelephonyManager.NETWORK_TYPE_EVDO_A:
                case TelephonyManager.NETWORK_TYPE_HSDPA:
                case TelephonyManager.NETWORK_TYPE_HSUPA:
                case TelephonyManager.NETWORK_TYPE_HSPA:
                case TelephonyManager.NETWORK_TYPE_EVDO_B:
                case TelephonyManager.NETWORK_TYPE_EHRPD:
                case TelephonyManager.NETWORK_TYPE_HSPAP:
                    return 3; // 3G
                case TelephonyManager.NETWORK_TYPE_LTE:
                    return 4; // 4G
                case TelephonyManager.NETWORK_TYPE_NR:
                    return 4; // 5G
                default:
                    return 2;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener fuerza de señal", e);
            return 2; // Valor medio por defecto
        }
    }
    
    /**
     * Obtiene el tipo de red (WiFi, 4G, 3G, etc.)
     */
    public static String getNetworkType(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo info = cm.getActiveNetworkInfo();
            
            if (info == null || !info.isConnected()) {
                return "Desconectado";
            }
            
            if (info.getType() == ConnectivityManager.TYPE_WIFI) {
                return "WiFi";
            }
            
            if (info.getType() == ConnectivityManager.TYPE_MOBILE) {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                
                switch (tm.getNetworkType()) {
                    case TelephonyManager.NETWORK_TYPE_GPRS:
                    case TelephonyManager.NETWORK_TYPE_EDGE:
                    case TelephonyManager.NETWORK_TYPE_CDMA:
                    case TelephonyManager.NETWORK_TYPE_1xRTT:
                    case TelephonyManager.NETWORK_TYPE_IDEN:
                        return "2G";
                    case TelephonyManager.NETWORK_TYPE_UMTS:
                    case TelephonyManager.NETWORK_TYPE_EVDO_0:
                    case TelephonyManager.NETWORK_TYPE_EVDO_A:
                    case TelephonyManager.NETWORK_TYPE_HSDPA:
                    case TelephonyManager.NETWORK_TYPE_HSUPA:
                    case TelephonyManager.NETWORK_TYPE_HSPA:
                    case TelephonyManager.NETWORK_TYPE_EVDO_B:
                    case TelephonyManager.NETWORK_TYPE_EHRPD:
                    case TelephonyManager.NETWORK_TYPE_HSPAP:
                        return "3G";
                    case TelephonyManager.NETWORK_TYPE_LTE:
                        return "4G";
                    case TelephonyManager.NETWORK_TYPE_NR:
                        return "5G";
                    default:
                        return "Celular";
                }
            }
            
            return "Desconocido";
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener tipo de red", e);
            return "Desconocido";
        }
    }
    
    /**
     * Obtiene el operador de la SIM
     */
    public static String getSimOperator(Context context) {
        try {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                return "Desconocido";
            }
            
            String operatorName = tm.getNetworkOperatorName();
            if (operatorName == null || operatorName.isEmpty()) {
                return "Desconocido";
            }
            
            return operatorName;
        } catch (Exception e) {
            Log.e(TAG, "Error al obtener operador SIM", e);
            return "Desconocido";
        }
    }
    
    /**
     * Obtiene la ranura de la SIM activa
     * (Solo es útil en dispositivos con Dual SIM)
     */
    public static int getSimSlot(Context context) {
        // La detección de la ranura SIM activa varía mucho según el fabricante
        // y la versión de Android, así que devolvemos 0 por defecto
        return 0;
    }
}