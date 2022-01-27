package me.okmanideep.flingvelocityissue


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalOverScrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import me.okmanideep.flingvelocityissue.ui.theme.FlingVelocityIssueTheme
import me.okmanideep.flingvelocityissue.ui.theme.PantherGray07
import kotlin.math.roundToInt


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BrowseSheetLayout(
    modifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val anchors = with(LocalDensity.current) {
            fromMaxHeight(maxHeight)
        }
        val browseableState = rememberBrowseableState()


        val offsetY = browseableState.offset
        val borderRadius = 12.dp
        val scale = derivedStateOf {
            scale(
                offset = offsetY.value,
                maxWidth = maxWidth,
                collapsedOffset = anchors.collapsedOffset,
                fullWidthOffset = anchors.fullWidthOffset
            )
        }
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .browseable(
                    state = browseableState,
                    anchors = anchors
                )
                .scale(scale.value)
                .absoluteOffset {
                    IntOffset(x = 0, y = offsetY.value.roundToInt())
                }
                .clip(RoundedCornerShape(borderRadius)),
            elevation = elevation
        ) {
            CompositionLocalProvider(LocalOverScrollConfiguration provides null) {
                content()
            }
        }
    }
}

private fun scale(
    offset: Float,
    maxWidth: Dp,
    collapsedOffset: Float,
    fullWidthOffset: Float,
): Float {
    val collapsedScale = (maxWidth - (2*12).dp) * 1.0f / maxWidth
    return when {
        (offset < fullWidthOffset) -> 1.0f
        (offset < collapsedOffset) ->
            collapsedScale + ((collapsedOffset - offset) / (collapsedOffset - fullWidthOffset))*(1.0f - collapsedScale)
        else -> collapsedScale
    }
}

@Preview
@Composable
fun BrowseSheetLayoutPreview() {
    FlingVelocityIssueTheme {
        Surface(
            color = MaterialTheme.colors.background,
            modifier = Modifier.fillMaxSize()
        ) {
            BrowseSheetLayout(elevation = 2.dp) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(100, key = {it}) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                                .height(144.dp)
                                .clip(shape = RoundedCornerShape(4.dp))
                                .background(PantherGray07)
                        )
                    }
                }
            }
        }
    }
}
