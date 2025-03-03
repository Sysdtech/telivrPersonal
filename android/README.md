# Aplicación Android para Telefonía Personal

Esta es la aplicación Android que acompaña al sistema de Telefonía Personal. Permite a los usuarios conectar dispositivos móviles Android al sistema central para realizar y recibir llamadas, enviar tonos DTMF y reproducir archivos de audio durante las llamadas.

## Características

- Emparejamiento seguro mediante códigos generados desde el panel web
- Comunicación WebSocket en tiempo real con el servidor
- Integración con el sistema de telefonía del dispositivo (llamadas entrantes/salientes)
- Detección y envío de tonos DTMF
- Reproducción de archivos de audio durante llamadas
- Monitoreo del estado del dispositivo (batería, señal, etc.)
- Reconexión automática ante pérdidas de conexión
- Soporte para múltiples versiones de Android (API 24+)

## Compilación

### Requisitos previos

- Android Studio Arctic Fox (2021.3.1) o superior
- JDK 17 o superior
- Gradle 8.0+
- Android SDK con API 34 (Android 14)

### Pasos para compilar

1. Clonar el repositorio
2. Abrir el proyecto en Android Studio
3. Sincronizar con Gradle
4. Ejecutar la tarea `assembleRelease` para generar el APK firmado

### Firma del APK

Para generar un APK firmado para producción:

1. Configurar las variables de firma en `gradle.properties` o como variables de entorno:
   - `KEYSTORE_FILE`: Ruta al archivo keystore
   - `KEYSTORE_PASSWORD`: Contraseña del keystore
   - `KEY_ALIAS`: Alias de la clave
   - `KEY_PASSWORD`: Contraseña de la clave

2. Ejecutar el siguiente comando:
   ```
   ./gradlew assembleRelease
   ```

## Integración con el servidor

La aplicación se conecta al servidor mediante WebSockets y utiliza un protocolo JSON para la comunicación. Cuando se establece una conexión, la aplicación envía información del dispositivo y espera comandos del servidor para:

- Iniciar llamadas
- Terminar llamadas
- Enviar tonos DTMF
- Reproducir archivos de audio
- Reportar estado del dispositivo

## Permisos necesarios

La aplicación requiere los siguientes permisos para funcionar correctamente:

- `READ_PHONE_STATE`: Para detectar llamadas entrantes
- `CALL_PHONE`: Para realizar llamadas salientes
- `READ_CALL_LOG`: Para acceder al registro de llamadas
- `RECORD_AUDIO`: Para grabar audio durante las llamadas
- `MODIFY_AUDIO_SETTINGS`: Para modificar la configuración de audio
- `FOREGROUND_SERVICE`: Para mantener el servicio activo
- `INTERNET` y `ACCESS_NETWORK_STATE`: Para la comunicación con el servidor

## Estructura del código

- `MainActivity.java`: Actividad principal y UI de la aplicación
- `WebSocketManager.java`: Gestiona la conexión WebSocket con el servidor
- `CallService.java`: Servicio en primer plano para gestionar llamadas
- `PhoneStateReceiver.java`: Receptor para eventos de telefonía
- `DeviceInfoHelper.java`: Recopila información del dispositivo

## Compilación automática

Este proyecto incluye un flujo de trabajo de GitHub Actions para compilar automáticamente el APK cuando se realizan cambios en el código. El APK resultante se publica como artefacto de GitHub y se copia al directorio de descargas del servidor para su distribución.

## Solución de problemas

### La aplicación se desconecta frecuentemente
- Verificar que la batería no esté optimizada para esta aplicación
- Comprobar que hay una conexión estable a Internet

### No se pueden realizar llamadas
- Verificar que todos los permisos estén concedidos
- Comprobar que la aplicación esté emparejada correctamente con el servidor