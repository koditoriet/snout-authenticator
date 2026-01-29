package se.koditoriet.snout.ui.screens

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import se.koditoriet.snout.R
import se.koditoriet.snout.ui.theme.BACKGROUND_ICON_SIZE

@Composable
fun EmptyScreen(splashIcon: Boolean = false, content: @Composable () -> Unit = {}) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        if (splashIcon) {
            SplashIcon()
        }
        content()
    }
}

@Composable
private fun SplashIcon() {
    Box(
        modifier = Modifier
            .graphicsLayer(
                compositingStrategy = CompositingStrategy.Offscreen,
                alpha = 0.2f,
            )
            .size(BACKGROUND_ICON_SIZE)
            .clip(CircleShape)
            .background(colorResource(R.color.ic_launcher_background)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(splashIcon),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .scale(1.5f)
        )
    }
}

@DrawableRes
private val splashIcon = R.mipmap.ic_launcher_foreground
