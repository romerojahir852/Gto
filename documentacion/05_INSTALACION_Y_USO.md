# 🚀 05. Guía de Instalación, APK y Uso en Vivo

Este manual explica cómo instalar y utilizar el archivo **APK** de Poker GTO Vision AI en teléfonos Android o en emuladores de PC.

---

## 1. Métodos de Instalación del APK

### Método A: En Celular Android Físico (Vía USB)
1. Conecta tu celular a la PC con cable USB.
2. Asegúrate de tener activada la **Depuración USB** (*Ajustes > Opciones de Desarrollador > Depuración USB*).
3. Abre una terminal en la carpeta del proyecto y ejecuta:
   ```powershell
   $env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
   adb install -r PokerGTO.apk
   ```
4. O alternativamente, copia el archivo `PokerGTO.apk` a la memoria de tu teléfono e instálalo tocándolo desde el explorador de archivos de Android.

---

### Método B: En Emuladores de PC (BlueStacks, LDPlayer, MuMu o WSA)
1. Abre tu emulador de Android favorito.
2. Arrastra el archivo **`PokerGTO.apk`** dentro de la ventana del emulador.
3. La aplicación se instalará automáticamente en segundos.

---

## 2. Permisos Críticos en Android

Para que el asistente funcione sobre tus partidas de póker, la app requiere dos permisos especiales que debes conceder la primera vez:

1. **Mostrar sobre otras aplicaciones (`SYSTEM_ALERT_WINDOW`):**
   - *Ajustes > Aplicaciones > Poker GTO > Mostrar sobre otras apps > Permitir*.
   - Esto permite que la ficha de póker flotante permanezca visible mientras juegas en GGPoker, PokerStars o tu sala preferida.
2. **Captura de pantalla (`MediaProjection`):**
   - Al pulsar *"Iniciar Servicio"* dentro de la app, Android mostrará un cuadro de diálogo del sistema: *"¿Iniciar grabación o proyección con Poker GTO?"*.
   - Pulsa **"Iniciar ahora"**.

---

## 3. Guía Paso a Paso para Jugar en Vivo

1. **Abre Poker GTO:**
   - En la pestaña *Ajustes*, ingresa tu clave API de Google Gemini (o déjala por defecto si fue inyectada en la compilación).
   - Elige tu tema preferido (Marfil Real o 24K Obsidiana).
2. **Inicia el Asistente:**
   - En la pestaña *En Vivo*, presiona el botón verde para activar el servicio flotante.
   - Aparecerá la pequeña ficha dorada flotante (46 dp) en una esquina de tu pantalla.
3. **Abre tu Sala de Póker Favorita:**
   - Inicia tu partida de póker habitual. La ficha flotante permanecerá visible sin estorbar.
   - Puedes arrastrar la ficha a cualquier lugar de la pantalla; al soltarla se adherirá suavemente al borde más cercano.
4. **Analizar la Mano en Curso:**
   - En cuanto recibas cartas o se reparta el Flop/Turn/River, toca la ficha flotante.
   - La ficha se desvanecerá en un instante, capturará la mesa limpia y desplegará el panel *"La Nube"*:
     - **Acción GTO:** Insignia de color (*RAISE 2.5x*, *CALL*, *CHECK*, *FOLD*, *ALL-IN*).
     - **Win Rate:** Porcentaje de victoria estimado contra el rango rival.
     - **Outs:** Conteo exacto de cartas salvadoras (ej. 9 Outs a Color, 8 Outs a Escalera).
     - **Verificación:** Muestra visual de las cartas detectadas en tu mano y en el tablero para tu total tranquilidad.
5. **Cerrar el Panel:**
   - Toca la ficha nuevamente para replegar *"La Nube"* y continuar jugando.
   - Si deseas quitar el overlay flotante por completo, arrastra la ficha hacia la parte inferior de la pantalla sobre el círculo rojo de la papelera (*Trash Target*).
