package com.example.ui.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BgWhite
import com.example.ui.theme.Ink

@Composable
fun BottomActionBar(
    isServiceRunning: Boolean,
    isAnalyzing: Boolean,
    onAnalyzeNow: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Ink,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 13.dp, bottom = 13.dp + 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "ANALISIS GTO",
                    color = BgWhite,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    letterSpacing = 0.14.sp
                )
                Text(
                    text = "Latencia < 2s - Gemini AI",
                    color = BgWhite.copy(alpha = 0.65f),
                    fontSize = 10.5.sp,
                    letterSpacing = 0.08.sp
                )
            }
            Button(
                onClick = onAnalyzeNow,
                enabled = !isAnalyzing,
                colors = ButtonDefaults.buttonColors(containerColor = BgWhite, contentColor = Ink),
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text(
                    text = if (isAnalyzing) "ANALIZANDO" else "CAPTURAR",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    letterSpacing = 0.14.sp
                )
            }
        }
    }
}
