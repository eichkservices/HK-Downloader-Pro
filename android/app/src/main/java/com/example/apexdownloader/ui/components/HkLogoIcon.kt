package com.example.apexdownloader.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.example.apexdownloader.theme.AccentCyan
import com.example.apexdownloader.theme.AccentOrange

@Composable
fun HkLogoIcon(
    modifier: Modifier = Modifier.size(28.dp)
) {
    Canvas(modifier = modifier) {
        val scaleX = size.width / 108f
        val scaleY = size.height / 108f

        // Top Cyan Chevron
        val topChevron = Path().apply {
            moveTo(37f * scaleX, 38f * scaleY)
            lineTo(42.5f * scaleX, 38f * scaleY)
            lineTo(54f * scaleX, 52.8f * scaleY)
            lineTo(65.5f * scaleX, 38f * scaleY)
            lineTo(71f * scaleX, 38f * scaleY)
            lineTo(54f * scaleX, 58.3f * scaleY)
            close()
        }
        drawPath(topChevron, color = AccentCyan)

        // Bottom Orange Chevron
        val bottomChevron = Path().apply {
            moveTo(37f * scaleX, 58f * scaleY)
            lineTo(42.5f * scaleX, 58f * scaleY)
            lineTo(54f * scaleX, 72.8f * scaleY)
            lineTo(65.5f * scaleX, 58f * scaleY)
            lineTo(71f * scaleX, 58f * scaleY)
            lineTo(54f * scaleX, 78.3f * scaleY)
            close()
        }
        drawPath(bottomChevron, color = AccentOrange)
    }
}
