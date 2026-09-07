package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.example.ui.theme.Ink
import com.example.ui.theme.Ink2
import com.example.ui.theme.Ink3
import com.example.ui.theme.Line
import com.example.ui.theme.Line2

@Composable
fun Eyebrow(
    text: String,
    center: Boolean = false,
    light: Boolean = false,
    modifier: Modifier = Modifier
) {
    val color = when {
        light -> Ink3
        else -> Ink2
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (center) Arrangement.Center else Arrangement.Start
    ) {
        if (!center) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Ink)
            )
            Spacer(modifier = Modifier.size(10.dp))
        }
        Text(
            text = text.uppercase(),
            color = color,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
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
    Text(
        text = text.uppercase(),
        color = if (light) androidx.compose.ui.graphics.Color.White else Ink,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = androidx.compose.ui.unit.TextUnit.Unspecified,
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
    Text(
        text = text,
        color = if (light) androidx.compose.ui.graphics.Color(0xFFB5B5B5) else Ink2,
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color.White)
            .border(1.dp, Line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = num,
                color = Ink3,
                fontFamily = FontFamily.Default,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                letterSpacing = 0.2.sp,
                modifier = Modifier.align(Alignment.TopEnd)
            )
            Text(
                text = icon,
                fontSize = 28.sp,
                color = Ink,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
        Text(
            text = title,
            color = Ink,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            letterSpacing = 0.03.sp
        )
        if (free != null) {
            androidx.compose.material3.Surface(
                shape = RoundedCornerShape(999.dp),
                color = androidx.compose.ui.graphics.Color(0xFFF8F9FA),
                border = androidx.compose.foundation.BorderStroke(1.dp, Line)
            ) {
                Text(
                    text = free.uppercase(),
                    color = Ink3,
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 9.5.sp,
                    letterSpacing = 0.18.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                )
            }
        }
        Text(
            text = text,
            color = Ink2,
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}
