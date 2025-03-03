package com.telefonia.personal;

public class DeviceInfo {
    // Información del dispositivo
    public String manufacturer;
    public String model;
    public String osVersion;
    public String appVersion;
    public String deviceId;
    
    // Estado del dispositivo
    public int batteryLevel;
    public boolean isCharging;
    public String networkType;
    public int signalStrength;
    public String ipAddress;
    
    // Capacidades del dispositivo
    public boolean hasCamera;
    public boolean hasMicrophone;
    public boolean hasSpeaker;
    
    // Constructor vacío
    public DeviceInfo() {
        // Constructor vacío necesario para serialización
    }
    
    // Método para crear una representación de cadena
    @Override
    public String toString() {
        return "DeviceInfo{" +
               "manufacturer='" + manufacturer + '\'' +
               ", model='" + model + '\'' +
               ", osVersion='" + osVersion + '\'' +
               ", batteryLevel=" + batteryLevel +
               ", networkType='" + networkType + '\'' +
               '}';
    }
}
