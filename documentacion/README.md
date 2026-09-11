# ♠️ Documentación Oficial: Poker GTO Vision AI 🃏
> **Versión de Referencia:** `852c19fa98e5c8b7acdee171de995c16b428b6e1`  
> **Arquitectura:** Android (Kotlin 2.2 + Jetpack Compose) | Google Gemini 3.8/3.7/3.6 Flash | Google ML Kit OCR | MediaProjection Overlay

Bienvenido a la carpeta de documentación completa de **Poker GTO Vision AI**. Este repositorio contiene la implementación de un asesor táctico y estratégico de póker Texas Hold'em en tiempo real, diseñado para operar sobre cualquier sala móvil o simulador mediante visión artificial y teoría de juegos óptima (GTO).

---

## 📚 Índice de Documentos

Para entender el proyecto a fondo, consulta los siguientes manuales organizados por especialidad:

1. **[01. Arquitectura y Flujo de Visión](file:///c:/Users/HP/OneDrive/Documentos/GitHub/MotoGP/Gto/documentacion/01_ARQUITECTURA_Y_VISION.md)**
   - ¿Qué es Poker GTO Vision y qué problema resuelve?
   - Diagrama de flujo de datos (de la pantalla al overlay pasando por la IA).
   - Secuencia de captura furtiva con auto-ocultamiento milimétrico (`alpha = 0f`).

2. **[02. Guía Detallada Archivo por Archivo](file:///c:/Users/HP/OneDrive/Documentos/GitHub/MotoGP/Gto/documentacion/02_GUIA_ARCHIVO_POR_ARCHIVO.md)**
   - Explicación de cada archivo fuente del proyecto (`data/`, `service/`, `ui/`, `theme/`).
   - Responsabilidades, clases, contratos y conexiones entre capas.

3. **[03. Motor Matemático y Teoría GTO](file:///c:/Users/HP/OneDrive/Documentos/GitHub/MotoGP/Gto/documentacion/03_MODELOS_MATEMATICOS_Y_GTO.md)**
   - Tablas de rangos preflop por posición (UTG, MP, CO, BTN, SB, BB).
   - Algoritmo de conteo de Outs (Flush draw, OESD, Gutshot, Top Pair, Set).
   - Cálculo de Pot Odds, SPR y porcentajes de Win Equity.

4. **[04. Visión Artificial y OCR Local](file:///c:/Users/HP/OneDrive/Documentos/GitHub/MotoGP/Gto/documentacion/04_VISION_ARTIFICIAL_Y_OCR.md)**
   - Pipeline óptico multirresolución (Macro 960p, Micro-Zoom mesa y Micro-Zoom hero).
   - Análisis cromático de barajas de 4 colores (Azul, Verde, Rojo, Negro).
   - Clustering espacial y deduplicación por coordenada horizontal X.

5. **[05. Guía de Instalación, APK y Uso en Vivo](file:///c:/Users/HP/OneDrive/Documentos/GitHub/MotoGP/Gto/documentacion/05_INSTALACION_Y_USO.md)**
   - Instalación del APK generado en celular físico o emulador (BlueStacks, LDPlayer, WSA).
   - Configuración de permisos Android (`SYSTEM_ALERT_WINDOW`, `MEDIA_PROJECTION`).
   - Modo de uso durante una partida real o en el simulador.

---

## 🛠️ Tecnologías Principales

- **Lenguaje:** Kotlin 2.2 con Kotlin Coroutines y StateFlow
- **UI:** Jetpack Compose (Material 3 con tema de lujo Mónaco VIP y Obsidiana 24K)
- **Captura:** Android `MediaProjectionManager` + `VirtualDisplay` + `ImageReader`
- **HUD Flotante:** Android `WindowManager` (`TYPE_APPLICATION_OVERLAY`)
- **IA en la Nube:** Google Gemini 3.8 Flash (`gemini-3.8-flash`) vía Ktor Client HTTP
- **Visión On-Device:** Google ML Kit Text Recognition + Detección Cromática por píxeles
- **Build System:** Gradle 9.3.1 con Android Gradle Plugin 8.9 y SDK 36
