# 👁️ 04. Visión Artificial y OCR Local

Poker GTO Vision AI cuenta con dos subsistemas visuales complementarios:
1. **Generador Multirresolución para Gemini 3.8 Flash** en [`PokerImageProcessor.kt`](file:///c:/Users/HP/OneDrive/Documentos/GitHub/MotoGP/Gto/app/src/main/java/com/example/service/PokerImageProcessor.kt).
2. **Detector Óptico On-Device ML Kit con Análisis Cromático** en [`LocalCardOcrDetector.kt`](file:///c:/Users/HP/OneDrive/Documentos/GitHub/MotoGP/Gto/app/src/main/java/com/example/service/LocalCardOcrDetector.kt).

---

## 1. Pipeline de 3 Perspectivas Ópticas (Nube)

Para que Google Gemini 3.8 Flash identifique las cartas sin distorsiones por compresión JPEG, `PokerImageProcessor` divide el frame nativo en 3 recortes complementarios:

```
+-------------------------------------------------------------+
|                                                             |
|           VISTA 1: MACRO COMPLETO (sub-960p)                |
|   (Mesa, asientos, fichas, botón de dealer y pozo)          |
|                                                             |
|              +-------------------------------+              |
|              |   VISTA 2: MICRO-ZOOM MESA   |              |
|              |  (Comunitarias 1.25x contraste)|              |
|              +-------------------------------+              |
|                                                             |
|              +-------------------------------+              |
|              |   VISTA 3: MICRO-ZOOM HERO   |              |
|              |  (Hole Cards 1.25x contraste) |              |
|              +-------------------------------+              |
+-------------------------------------------------------------+
```

1. **Macro Panorámico:** Redimensionado proporcionalmente a un máximo de 960 píxeles. Realce sutil de contraste (1.15x) y brillo (+5f).
2. **Micro-Zoom Comunitarias:** Recorte exacto de la mesa ($x \in 8\% \dots 92\%$, $y \in 34\% \dots 65\%$) a resolución nativa con contraste 1.25x y brillo +8f.
3. **Micro-Zoom Hero:** Recorte de la mitad inferior ($x \in 0\% \dots 100\%$, $y \in 62\% \dots 97\%$) con contraste 1.25x y brillo +8f.

---

## 2. Visión Artificial On-Device (`LocalCardOcrDetector`)

Cuando se ejecuta localmente sin internet, el sistema realiza los siguientes pasos de análisis por computadora:

### A. Filtrado de Textos No Relacionados
Se descartan palabras clave que no corresponden a cartas ni botes (`ALL-IN`, `AUSENTE`, `BOTE`, `HOLDEM`, `CALL`, `RAISE`, `FOLD`).

### B. Muestreo Cromático de 4 Colores
Las salas modernas utilizan barajas de 4 colores para evitar confusiones entre picas y tréboles, o corazones y diamantes. El método `sampleCardSuitFromPixels()` muestrea la cuadrícula de píxeles alrededor del glifo del rango:

```kotlin
// Detección cromática en espacio de color RGB
if (blueCount > 6 && blueCount >= greenCount) {
    CardSuit.DIAMONDS  // ♦ Diamante Azul (4-color deck)
} else if (greenCount > 6) {
    CardSuit.CLUBS     // ♣ Trébol Verde (4-color deck)
} else if (redCount > 6) {
    CardSuit.HEARTS    // ♥ Corazón Rojo
} else {
    CardSuit.SPADES    // ♠ Pica Negra
}
```

### C. Agrupación Espacial y Deduplicación en X
- **Comunitarias:** Se ubican en el rango central ($y \in 30\% \dots 65\%$).
- **Deduplicación por Posición X:** En lugar de eliminar cartas con el mismo valor (lo cual rompería manos con parejas o tríos en la mesa como $K\spadesuit K\heartsuit K\diamondsuit$), se deduplican estrictamente por su separación en el eje horizontal $X$.
- **Hero Cards:** Se detectan como el par adyacente más cercano a la parte inferior de la mesa ($y > 55\%$).

### D. Validación Cruzada con Insignias de la Sala
Si el software de póker muestra un texto como *"un color con A"* o *"un full de 4s con As"*, el detector valida las cartas leídas contra esta verdad fundamental para corregir cualquier ambigüedad de lectura.
