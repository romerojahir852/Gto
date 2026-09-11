# 🧮 03. Motor Matemático y Teoría GTO

El motor de decisión de Poker GTO Vision AI utiliza principios de **Teoría de Juegos Óptima (Game Theory Optimal)** combinados con evaluación determinista offline en [`PokerGtoEngine.kt`](file:///c:/Users/HP/OneDrive/Documentos/GitHub/MotoGP/Gto/app/src/main/java/com/example/data/PokerGtoEngine.kt).

---

## 1. Estrategia Preflop por Posición

La clasificación preflop evalúa tres factores:
1. **Fuerza intrínseca de la mano:** Cartas altas, conectores del mismo palo (*suited connectors*) y parejas en mano (*pocket pairs*).
2. **Posición relativa en la mesa:**
   - **Posición temprana (UTG, MP):** Rangos lineales y estrictos. Se descartan conectores especulativos o broadways débiles fuera de palo.
   - **Posición tardía (CO, BTN):** Rangos de apertura amplios (2.2x - 2.5x). Mayor presión sobre las ciegas.
   - **Ciegas (SB, BB):** Defensa polarizada según pot odds.
3. **Número de rivales:** Ajuste dinámico de rangos para mesas de 2 a 9 jugadores.

### Matriz de Decisiones Preflop:

| Mano | Posición Recomendada | Acción GTO | Sizing Sugerido | Win Rate Estimado |
| :--- | :--- | :--- | :--- | :--- |
| **AA, KK, QQ, AKs** | Cualquier posición | `3-BET` / `RAISE` | 3.5x | 82% |
| **JJ, TT, AKo, AQs** | UTG a BTN | `RAISE` | 2.5x - 3x | 71% |
| **Parejas medias (99 - 66)** | Late (BTN/CO) / Heads-Up | `RAISE` | 2.5x | 58% |
| **Parejas medias (99 - 22)** | Early (UTG/MP) con 6+ jugadores | `CALL` (*Set Mining*) | 1x | 52% |
| **Suited Connectors (T9s, 98s, 87s)** | Late (BTN/CO) | `RAISE` | 2.2x | 54% |
| **Suited Connectors (87s, 76s)** | Early / Multiway | `CALL` (*Especulativo*) | 1x | 48% |
| **Broadways Offsuit (KJo, QTo)** | Early (UTG) | `FOLD` | — | 24% - 35% |

---

## 2. Evaluación Postflop y Conteo de Outs

En las calles postflop (**Flop, Turn y River**), el motor evalúa las 5 o 7 cartas disponibles (Hero + Comunitarias) para calcular los outs reales que mejoran la mano a una combinación ganadora:

```
Total de Outs = Cartas restantes en la baraja que completan una mano superior
```

### Reglas de Detección de Proyectos y Outs:

1. **Color Conectado (*Made Flush*):** $\ge 5$ cartas del mismo palo.  
   - *Acción:* `ALL-IN MAX` | Win Rate: 96%
2. **Set / Trío Conectado (*Made Set*):** Tres cartas del mismo valor.  
   - *Acción:* `BET 75% Pot` | Win Rate: 88%
3. **Proyecto Monstruo (*Combo Draw* - Color + Pareja):**  
   - 9 outs a color + 5 outs a dobles/trío = **14 Outs**.  
   - *Acción:* `RAISE 3.5x` (Semi-farol agresivo) | Win Rate: 65%
4. **Proyecto de Color (*Flush Draw*):** 4 cartas del mismo palo.  
   - **9 Outs** restantes en la baraja.  
   - *Acción:* `CALL 1 Pot` | Win Rate: 45%
5. **Top Pair (Pareja Mayor con la mesa):**  
   - En bote corto ($\le 3$ jugadores): `BET 50% Pot` | Win Rate: 68%  
   - En bote multiway ($> 3$ jugadores): `CALL 1x` | Win Rate: 58%
6. **Segunda Pareja (*Second Pair / Pot Control*):**  
   - *Acción:* `CHECK` | Win Rate: 42%
7. **Sin Mano en el River:**  
   - *Acción:* `FOLD` | Win Rate: 12%

---

## 3. Métricas de Pot Odds y SPR

El motor calcula el valor esperado considerando:
- **Pot Odds:** Relación entre el tamaño de la apuesta rival y el tamaño total del bote después de pagar:
$$\text{Pot Odds} = \frac{\text{Apuesta a Pagar}}{\text{Bote Total} + \text{Apuesta a Pagar}}$$
- **Stack-to-Pot Ratio (SPR):** Nivel de compromiso con el bote para decidir si apostar por valor o controlar el bote (*Pot Control*).
