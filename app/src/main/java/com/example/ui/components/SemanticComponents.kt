package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AppTheme

@Composable
fun Eyebrow(
    text: String,
    center: Boolean = false,
    light: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val color = when {
        light -> colors.textMuted
        else -> colors.textSecondary
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start
    ) {
        if (!center) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(colors.accentGreen)
            )
            Spacer(modifier = Modifier.size(10.dp))
        }
        Text(
            text = text.uppercase(),
            color = color,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            letterSpacing = 0.35.sp
        )
    }
}

@Composable
fun SectionTitle(
    text: String,
    center: Boolean = false,
    light: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Text(
        text = text.uppercase(),
        color = if (light) colors.textPrimary else colors.textPrimary,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        modifier = modifier,
        textAlign = if (center) TextAlign.Center else TextAlign.Start
    )
}

@Composable
fun SectionSub(
    text: String,
    center: Boolean = false,
    light: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    Text(
        text = text,
        color = if (light) colors.textMuted else colors.textSecondary,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        modifier = modifier,
        textAlign = if (center) TextAlign.Center else TextAlign.Start
    )
}

@Composable
fun OptCard(
    num: String,
    icon: String,
    title: String,
    free: String? = null,
    text: String,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.5.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = num,
                color = colors.textMuted,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.2.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            Text(
                text = icon,
                fontSize = 28.sp,
                color = colors.textPrimary,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
        Text(
            text = title,
            color = colors.textPrimary,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            letterSpacing = 0.03.sp
        )
        if (free != null) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = colors.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.border)
            ) {
                Text(
                    text = free.uppercase(),
                    color = colors.accentGreen,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.18.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        Text(
            text = text,
            color = colors.textSecondary,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}
