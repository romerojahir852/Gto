package com.example.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppTheme

@Composable
fun BottomActionBar(
    isServiceRunning: Boolean,
    isAnalyzing: Boolean,
    onAnalyzeNow: () -> Unit
) {
    val colors = AppTheme.colors

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.5.dp, colors.border),
        color = colors.surface,
        shadowElevation = if (colors.isDark) 0.dp else 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "ASISTENTE EN VIVO",
                    color = colors.textPrimary,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Lectura de mesa en tiempo real • Cálculo GTO",
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    letterSpacing = 0.08.sp
                )
            }
            Button(
                onClick = onAnalyzeNow,
                enabled = !isAnalyzing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentSelected,
                    contentColor = colors.accentSelectedText
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentGold),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = if (isAnalyzing) "ANALIZANDO..." else "CAPTURAR",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 0.14.sp
                )
            }
        }
    }
}
