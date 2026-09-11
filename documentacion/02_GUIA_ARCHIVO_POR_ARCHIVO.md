# 📁 02. Guía Detallada Archivo por Archivo

A continuación se detalla la función, responsabilidades y conexiones de cada archivo del proyecto en la versión `852c19fa98e5c8b7acdee171de995c16b428b6e1`:

---

## 1. Capa de Datos y Modelado (`app/src/main/java/com/example/data/`)

### `ApiKeyManager.kt`
- **Responsabilidad:** Almacén persistente y seguro de la clave API de Google Gemini.
- **Detalle:** Utiliza `SharedPreferences` para guardar la clave introducida por el usuario desde la UI. Si no existe ninguna guardada, toma por defecto la clave configurada en tiempo de compilación (`BuildConfig.GEMINI_API_KEY`). Proporciona funciones para enmascarar la clave en pantalla (`getMaskedKey`).

### `PokerModels.kt`
- **Responsabilidad:** Modelos de datos centrales y definiciones de tipos para Texas Hold'em.
- **Detalle:**
  - `PokerCard`: Modela un naipe con rango (`2..A`) y palo (`CardSuit`). Incluye parsers automáticos mediante expresiones regulares para textos como `"Ah Kd"`.
  - `CardSuit`: Enumeración con soporte cromático para barajas de 4 colores (Corazones rojo, Diamantes azul, Tréboles verde, Picas negro).
  - `GtoAction`: Acciones estratégicas (`FOLD`, `CHECK`, `CALL`, `BET`, `RAISE`, `THREE_BET`, `ALL_IN`, `ERROR`).
  - `HandState`: Estado reactivo e inmutable de la mano viva (cartas, bote, outs, equidad, acción GTO, etc.).
  - `PokerGameStateManager`: Singleton que contiene el `StateFlow<HandState>` consumido por toda la UI.

### `PokerGtoEngine.kt`
- **Responsabilidad:** Motor determinista local de cálculo GTO.
- **Detalle:** No depende de red. Analiza la fuerza de la mano preflop considerando posición y número de jugadores. En postflop identifica parejas, sets, full house y proyectos (color, escalera abierta, gutshots), estimando los outs exactos y la acción recomendada con su tamaño de apuesta.

### `GTOStateManager.kt`
- **Responsabilidad:** Control de las variables del entorno de juego.
- **Detalle:** Monitorea el número de jugadores activos (2 a 9), calcula la lista ordenada de posiciones (`computeActivePositions`), gestiona la rotación del botón de Dealer y mantiene sincronizado el bote con `PokerGameStateManager`.

### `GeminiPokerRepository.kt`
- **Responsabilidad:** Cliente de inferencia multimodal con Google Gemini 3.8 Flash.
- **Detalle:** Realiza peticiones POST directas usando Ktor Client con `temperature = 0.0` y esquema de respuesta JSON estricto (`responseSchema`). Si la imagen no es una mesa de póker, lo reporta con `isPoker = false`. Si la consulta a la nube falla o excede el tiempo límite (10s), activa de forma transparente el escáner local `LocalCardOcrDetector`.

---

## 2. Capa de Servicios y Visión (`app/src/main/java/com/example/service/`)

### `ScreenCaptureService.kt`
- **Responsabilidad:** Foreground Service para captura de pantalla continua.
- **Detalle:** Mantiene un canal de notificaciones permanente (`poker_gto_screen_capture_channel`). Crea el `VirtualDisplay` asociado a `MediaProjection` y provee `captureFreshFrame()` con purga de buffers para capturar la pantalla sin rastros del HUD.

### `FloatingOverlayManager.kt`
- **Responsabilidad:** Controlador de la ventana flotante en `WindowManager`.
- **Detalle:** Dibuja la ficha flotante sobre la pantalla con permisos `SYSTEM_ALERT_WINDOW`. Controla el arrastre, la animación de adherencia al borde (*snap-to-edge*), el área de eliminación inferior (*trash target*) y la secuencia de auto-ocultamiento milimétrico antes de cada captura.

### `ServiceLifecycleOwner.kt`
- **Responsabilidad:** Ciclo de vida para Compose en segundo plano.
- **Detalle:** Implementa `LifecycleOwner`, `ViewModelStoreOwner` y `SavedStateRegistryOwner` para permitir montar árboles de Jetpack Compose dentro de un servicio de Android.

### `PokerImageProcessor.kt`
- **Responsabilidad:** Procesamiento visual y realce de contraste.
- **Detalle:** Toma el fotograma original y genera 3 recortes de alta fidelidad:
  1. **Macro:** Mesa completa escalada a 960p para lectura general.
  2. **Micro-Zoom Mesa:** Recorte central del Flop/Turn/River con contraste elevado (1.25x).
  3. **Micro-Zoom Hero:** Recorte de la zona inferior de cartas de Hero con contraste elevado (1.25x).

### `LocalCardOcrDetector.kt`
- **Responsabilidad:** Motor de visión artificial y OCR on-device.
- **Detalle:** Usa `Google ML Kit TextRecognition` para extraer textos de la mesa. Agrupa cartas por coordenadas espaciales ($y \in 30\% \dots 65\%$ comunitarias, $y > 55\%$ hero), realiza análisis cromático de los píxeles del naipe para identificar el palo y valida la lectura contra las insignias de texto de la sala (ej. "full de 4s", "color con A").

---

## 3. Capa de Presentación e Interfaz (`app/src/main/java/com/example/ui/`)

### `MainActivity.kt`
- **Responsabilidad:** Actividad de inicio y gestión de permisos.
- **Detalle:** Inicializa `ApiKeyManager` y `AppThemeManager`, solicita permisos de notificación y consentimiento de `MediaProjection`, y monta el composable `PokerScreen`.

### `PokerViewModel.kt`
- **Responsabilidad:** ViewModel de la pantalla principal (MVVM).
- **Detalle:** Administra los flujos de estado `uiState`, el historial de jugadas recientes, los presets del simulador y orquesta los análisis de capturas.

### `PokerScreen.kt`
- **Responsabilidad:** Pantalla interactiva principal de la aplicación.
- **Detalle:** Dispone de un sistema de navegación por pestañas (*En Vivo*, *Mesa GTO*, *Ajustes*), visualizador de capturas, tarjeta de recomendación GTO en tiempo real, selector de unidades ($ / BB) y modales informativos de glosario y ayuda.

### `MainPokerScreen.kt`
- **Responsabilidad:** Dashboard simplificado alternativo.
- **Detalle:** Vista condensada para pruebas directas de captura y visor de respuestas JSON crudas de la IA.

### `overlay/FloatingPokerHud.kt`
- **Responsabilidad:** Layout Compose del overlay flotante.
- **Detalle:** Representa la ficha de póker de 46 dp con gradiente y borde dorado, y el panel desplegable *"La Nube"* con la acción GTO semántica, barra de porcentaje de victoria, outs y naipes detectados.

### `components/PokerTableSimulator.kt`
- **Responsabilidad:** Simulador de mesa offline Texas Hold'em.
- **Detalle:** Dibuja sobre un Canvas una mesa completa de póker con fieltro verde, bote, ciegas y naipes. Permite probar situaciones complejas (*AKs en BTN*, *Flush Draw + Gutshot*, *Set en Flop*) y generar un Bitmap para probar la IA sin abrir una sala real.

### `components/GtoHudCard.kt`
- **Responsabilidad:** Tarjeta detallada de resultados GTO para la app principal.

### `components/PokerCardView.kt`
- **Responsabilidad:** Componentes de renderizado visual de naipes individuales y filas de cartas.

### `components/BackgroundGeometry.kt`
- **Responsabilidad:** Fondo animado continuo con ondas de oro líquido y pulsos geométricos.

---

## 4. Capa de Tema y Estilos (`app/src/main/java/com/example/ui/theme/`)

### `AppThemeManager.kt`
- **Responsabilidad:** Gestor reactivo para alternar entre tema claro y tema oscuro con persistencia en `SharedPreferences`.

### `Color.kt`
- **Responsabilidad:** Paletas de lujo de alta definición:
  - *Royal Ivory & Liquid Champagne* (Claro).
  - *Obsidian & 24K Liquid Gold* (Oscuro).

### `Theme.kt` y `Type.kt`
- **Responsabilidad:** Definición del tema `MyApplicationTheme` y estilos tipográficos de Material 3.
