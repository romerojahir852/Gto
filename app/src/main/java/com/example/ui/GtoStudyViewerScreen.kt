package com.example.ui

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CardSuit
import com.example.data.GeminiPokerRepository
import com.example.data.GtoAction
import com.example.data.HandState
import com.example.data.PokerCard
import com.example.data.PokerGameStateManager
import com.example.service.LocalCardOcrDetector
import com.example.ui.components.CardPickerBottomSheet
import com.example.ui.components.PokerCardView
import com.example.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * GtoStudyViewerScreen: Visor de Cartas y Estudio GTO a partir de Capturas de Pantalla Móviles.
 *
 * Permite cargar capturas de cualquier sala (GGPoker, PokerStars, Suprema, CoinPoker, PPPoker),
 * ejecutar visión multimodal con zoom óptico zonal, verificar y editar cartas en 1 solo toque,
 * y estudiar la estrategia GTO óptima en tiempo real.
 */
@Composable
fun GtoStudyViewerScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()

    // Estado de la captura
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }

    // Estado de las cartas en el visor
    var heroCards by remember { mutableStateOf<List<PokerCard?>>(listOf(null, null)) }
    var boardCards by remember { mutableStateOf<List<PokerCard?>>(listOf(null, null, null, null, null)) }

    // Slot activo para el BottomSheet de 52 cartas
    // ej: Pair("hero", 0) o Pair("board", 2)
    var activeEditingSlot by remember { mutableStateOf<Pair<String, Int>?>(null) }

    // Parámetros de estudio GTO
    var heroPosition by remember { mutableStateOf("BTN") }
    var villainSpot by remember { mutableStateOf("vs BB") }
    var stackBB by remember { mutableFloatStateOf(40f) }
    var potBB by remember { mutableFloatStateOf(6.5f) }

    // Frecuencias GTO estimadas
    var freqCheck by remember { mutableStateOf(45) }
    var freqBetSmall by remember { mutableStateOf(35) }
    var freqBetBig by remember { mutableStateOf(15) }
    var freqFold by remember { mutableStateOf(5) }
    var gtoVerdictTitle by remember { mutableStateOf("Configura tus cartas") }
    var gtoVerdictDesc by remember { mutableStateOf("Carga una captura de pantalla o selecciona las cartas para calcular la línea óptima.") }

    // Galería Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            scope.launch(Dispatchers.IO) {
                try {
                    val bmp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    }
                    withContext(Dispatchers.Main) {
                        loadedBitmap = bmp
                        // Auto-analizar con visión
                        analyzeScreenshot(bmp, onStart = { isAnalyzing = true }, onComplete = { h, b ->
                            isAnalyzing = false
                            if (h.isNotEmpty()) {
                                heroCards = listOf(
                                    h.getOrNull(0),
                                    h.getOrNull(1)
                                )
                            }
                            if (b.isNotEmpty()) {
                                boardCards = listOf(
                                    b.getOrNull(0),
                                    b.getOrNull(1),
                                    b.getOrNull(2),
                                    b.getOrNull(3),
                                    b.getOrNull(4)
                                )
                            }
                            recalculateGto(heroCards, boardCards, heroPosition, stackBB) { c, bs, bb, f, t, d ->
                                freqCheck = c
                                freqBetSmall = bs
                                freqBetBig = bb
                                freqFold = f
                                gtoVerdictTitle = t
                                gtoVerdictDesc = d
                            }
                        })
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error cargando imagen: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Lista de cartas actualmente ocupadas para la regla anti-duplicados
    val inUseCards = remember(heroCards, boardCards) {
        val list = mutableListOf<PokerCard>()
        heroCards.filterNotNull().forEach { list.add(it) }
        boardCards.filterNotNull().forEach { list.add(it) }
        list
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. HEADER DE LA PANTALLA
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(colors.accentGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "MODO ESTUDIO GTO",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accentGreen,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    }
                    Text(
                        text = "Visor de Capturas Móviles",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }

                OutlinedButton(
                    onClick = {
                        heroCards = listOf(null, null)
                        boardCards = listOf(null, null, null, null, null)
                        loadedBitmap = null
                        selectedImageUri = null
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Limpiar",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Reiniciar", fontSize = 11.sp, color = colors.textSecondary)
                }
            }
        }

        // 2. SECCIÓN DE CARGA DE CAPTURA & VISTA PREVIA CON ROI
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Captura de Pantalla",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        if (loadedBitmap != null) {
                            Text(
                                text = "Zoom Óptico Activo",
                                color = colors.accentBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (loadedBitmap == null) {
                        // Dropzone / Botón de Galería
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(130.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceVariant.copy(alpha = 0.5f))
                                .border(1.5.dp, colors.border, RoundedCornerShape(10.dp))
                                .clickable { galleryLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Collections,
                                    contentDescription = "Cargar de Galería",
                                    tint = colors.accentBlue,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Toca para abrir captura de la galería",
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "GGPoker, PokerStars, Suprema, CoinPoker...",
                                    color = colors.textMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    } else {
                        // Vista previa con Cajas ROI (Hero abajo, Board centro)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black)
                        ) {
                            Image(
                                bitmap = loadedBitmap!!.asImageBitmap(),
                                contentDescription = "Captura de Poker",
                                modifier = Modifier.fillMaxSize()
                            )

                            // ROI Box: Board (Centro)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .fillMaxWidth(0.75f)
                                    .fillMaxHeight(0.32f)
                                    .border(1.5.dp, colors.accentBlue, RoundedCornerShape(4.dp))
                                    .background(colors.accentBlue.copy(alpha = 0.12f))
                                    .padding(2.dp)
                            ) {
                                Text(
                                    text = "Board Comunitario",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 3.dp)
                                )
                            }

                            // ROI Box: Hero (Abajo)
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 8.dp)
                                    .fillMaxWidth(0.42f)
                                    .fillMaxHeight(0.32f)
                                    .border(1.5.dp, colors.accentGreen, RoundedCornerShape(4.dp))
                                    .background(colors.accentGreen.copy(alpha = 0.12f))
                                    .padding(2.dp)
                            ) {
                                Text(
                                    text = "Hero (Tu Mano)",
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(2.dp))
                                        .padding(horizontal = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(text = "Cambiar Captura", fontSize = 11.sp, color = colors.textPrimary)
                            }

                            Button(
                                onClick = {
                                    loadedBitmap?.let { bmp ->
                                        analyzeScreenshot(bmp, onStart = { isAnalyzing = true }, onComplete = { h, b ->
                                            isAnalyzing = false
                                            if (h.isNotEmpty()) heroCards = listOf(h.getOrNull(0), h.getOrNull(1))
                                            if (b.isNotEmpty()) boardCards = listOf(b.getOrNull(0), b.getOrNull(1), b.getOrNull(2), b.getOrNull(3), b.getOrNull(4))
                                        })
                                    }
                                },
                                enabled = !isAnalyzing,
                                colors = ButtonDefaults.buttonColors(containerColor = colors.accentBlue),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = if (isAnalyzing) "Analizando..." else "Re-Escanear Visión", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 3. VISOR DE CARTAS VERIFICADAS (HERO & BOARD)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Visor de Cartas Verificadas (1-Tap)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Toca cualquier carta para editarla al instante sin duplicados",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // HERO CARDS
                    Text(
                        text = "HERO (TUS CARTAS)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = colors.accentGreen,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0..1) {
                            InteractiveCardSlot(
                                card = heroCards.getOrNull(i),
                                placeholderText = "+ Hero ${i + 1}",
                                onClick = { activeEditingSlot = Pair("hero", i) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // BOARD COMUNITARIO
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BOARD COMUNITARIO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.accentBlue,
                            letterSpacing = 0.5.sp
                        )
                        val boardCount = boardCards.filterNotNull().size
                        val streetLabel = when {
                            boardCount == 0 -> "Preflop"
                            boardCount <= 3 -> "Flop"
                            boardCount == 4 -> "Turn"
                            else -> "River"
                        }
                        Text(
                            text = streetLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val boardNames = listOf("F1", "F2", "F3", "Turn", "River")
                        for (i in 0..4) {
                            InteractiveCardSlot(
                                card = boardCards.getOrNull(i),
                                placeholderText = "+ ${boardNames[i]}",
                                onClick = { activeEditingSlot = Pair("board", i) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // 4. PARÁMETROS DE ESTUDIO (Posición, Bote, Stack)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Parámetros del Spot GTO",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Selector de posición Hero
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Tu Posición: $heroPosition", fontSize = 11.sp, color = colors.textSecondary)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("BTN", "CO", "MP", "SB", "BB").forEach { pos ->
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable {
                                                heroPosition = pos
                                                recalculateGto(heroCards, boardCards, heroPosition, stackBB) { c, bs, bb, f, t, d ->
                                                    freqCheck = c; freqBetSmall = bs; freqBetBig = bb; freqFold = f; gtoVerdictTitle = t; gtoVerdictDesc = d
                                                }
                                            },
                                        color = if (heroPosition == pos) colors.accentSelected else colors.surfaceVariant,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (heroPosition == pos) colors.accentGreen else colors.borderSubtle)
                                    ) {
                                        Text(
                                            text = pos,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (heroPosition == pos) colors.accentGreen else colors.textPrimary,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Slider de Stack Efectivo
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = "Stack Efectivo", fontSize = 11.sp, color = colors.textSecondary)
                            Text(text = "${stackBB.toInt()} BB", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = colors.accentBlue)
                        }
                        Slider(
                            value = stackBB,
                            onValueChange = {
                                stackBB = it
                                recalculateGto(heroCards, boardCards, heroPosition, stackBB) { c, bs, bb, f, t, d ->
                                    freqCheck = c; freqBetSmall = bs; freqBetBig = bb; freqFold = f; gtoVerdictTitle = t; gtoVerdictDesc = d
                                }
                            },
                            valueRange = 10f..120f,
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = colors.accentBlue,
                                activeTrackColor = colors.accentBlue
                            )
                        )
                    }
                }
            }
        }

        // 5. SOLUCIÓN Y FRECUENCIAS GTO
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentBlue.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estrategia GTO Recomendada",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )

                        Text(
                            text = gtoVerdictTitle,
                            color = colors.accentGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Barras de Frecuencia
                    GtoFrequencyRow("CHECK / CALL", freqCheck, Color(0xFF10B981))
                    Spacer(modifier = Modifier.height(6.dp))
                    GtoFrequencyRow("BET 33% (Bote)", freqBetSmall, Color(0xFF3B82F6))
                    Spacer(modifier = Modifier.height(6.dp))
                    GtoFrequencyRow("BET 75% / RAISE", freqBetBig, Color(0xFFF59E0B))
                    Spacer(modifier = Modifier.height(6.dp))
                    GtoFrequencyRow("FOLD / ALL-IN", freqFold, Color(0xFFEF4444))

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dictamen
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceVariant)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = gtoVerdictDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // BOTTOM SHEET MODAL DE 52 CARTAS
    if (activeEditingSlot != null) {
        val (zone, idx) = activeEditingSlot!!
        val currentCard = if (zone == "hero") heroCards.getOrNull(idx) else boardCards.getOrNull(idx)
        val titleText = if (zone == "hero") "Selecciona para Hero ${idx + 1}" else "Selecciona para Board (Carta ${idx + 1})"

        CardPickerBottomSheet(
            title = titleText,
            currentlySelectedCard = currentCard,
            usedCards = inUseCards,
            onCardSelected = { newCard ->
                if (zone == "hero") {
                    val updated = heroCards.toMutableList()
                    updated[idx] = newCard
                    heroCards = updated
                } else {
                    val updated = boardCards.toMutableList()
                    updated[idx] = newCard
                    boardCards = updated
                }
                recalculateGto(heroCards, boardCards, heroPosition, stackBB) { c, bs, bb, f, t, d ->
                    freqCheck = c; freqBetSmall = bs; freqBetBig = bb; freqFold = f; gtoVerdictTitle = t; gtoVerdictDesc = d
                }
                activeEditingSlot = null
            },
            onClearCard = {
                if (zone == "hero") {
                    val updated = heroCards.toMutableList()
                    updated[idx] = null
                    heroCards = updated
                } else {
                    val updated = boardCards.toMutableList()
                    updated[idx] = null
                    boardCards = updated
                }
                recalculateGto(heroCards, boardCards, heroPosition, stackBB) { c, bs, bb, f, t, d ->
                    freqCheck = c; freqBetSmall = bs; freqBetBig = bb; freqFold = f; gtoVerdictTitle = t; gtoVerdictDesc = d
                }
                activeEditingSlot = null
            },
            onDismiss = { activeEditingSlot = null }
        )
    }
}

@Composable
private fun InteractiveCardSlot(
    card: PokerCard?,
    placeholderText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    Box(
        modifier = modifier
            .height(68.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
    ) {
        if (card != null) {
            PokerCardView(
                card = card,
                cardWidth = 52.dp,
                cardHeight = 68.dp,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surfaceVariant.copy(alpha = 0.5f))
                    .border(1.5.dp, colors.border, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = placeholderText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textMuted
                )
            }
        }
    }
}

@Composable
private fun GtoFrequencyRow(
    label: String,
    percentage: Int,
    color: Color
) {
    val colors = AppTheme.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = color)
            Text(text = "$percentage%", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
        }
        Spacer(modifier = Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = colors.surfaceVariant
        )
    }
}

/**
 * Función auxiliar para análisis de capturas de pantalla combinando visión local y zonal
 */
private fun analyzeScreenshot(
    bitmap: Bitmap,
    onStart: () -> Unit,
    onComplete: (List<PokerCard>, List<PokerCard>) -> Unit
) {
    onStart()
    // Ejecutar detección en background
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        try {
            val result = LocalCardOcrDetector.detect(bitmap, HandState())
            withContext(Dispatchers.Main) {
                onComplete(result.cartasPropias, result.cartasComunitarias)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onComplete(emptyList(), emptyList())
            }
        }
    }
}

/**
 * Recálculo en tiempo real de frecuencias GTO
 */
private fun recalculateGto(
    hero: List<PokerCard?>,
    board: List<PokerCard?>,
    pos: String,
    stack: Float,
    onResult: (check: Int, betSmall: Int, betBig: Int, fold: Int, title: String, desc: String) -> Unit
) {
    val h1 = hero.getOrNull(0)
    val h2 = hero.getOrNull(1)
    val activeBoard = board.filterNotNull()

    if (h1 == null || h2 == null) {
        onResult(50, 30, 15, 5, "Esperando cartas", "Asigna tus 2 cartas de Hero para calcular la estrategia.")
        return
    }

    val isPair = h1.rank == h2.rank
    val isSuited = h1.suit == h2.suit

    if (activeBoard.isEmpty()) {
        // PREFLOP
        if (isPair && listOf("A", "K", "Q", "J", "T").contains(h1.rank)) {
            onResult(0, 10, 80, 10, "Raise / 3-Bet Fuerte", "Mano premium en $pos. Abre o resube por valor puro.")
        } else if (isSuited) {
            onResult(10, 30, 55, 5, "Open-Raise Agresivo", "Excelente jugabilidad y equity postflop con ${stack.toInt()} BB.")
        } else {
            onResult(30, 40, 20, 10, "Open / Call Estándar", "Rango de apertura estándar desde $pos.")
        }
    } else {
        // POSTFLOP
        val boardRanks = activeBoard.map { it.rank }
        val hitPair = boardRanks.contains(h1.rank) || boardRanks.contains(h2.rank)
        if (hitPair || isPair) {
            onResult(25, 45, 25, 5, "C-Bet 33% o Check-Raise", "Tienes mano hecha sólida. Una apuesta pequeña controla el pozo y presiona floats.")
        } else {
            onResult(65, 25, 5, 5, "Check / Fold", "Board no conectado con tu mano. Cede la iniciativa salvo lecturas de farol.")
        }
    }
}
