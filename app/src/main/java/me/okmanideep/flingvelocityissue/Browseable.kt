package me.okmanideep.flingvelocityissue

import androidx.compose.animation.core.*
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import kotlin.math.abs
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

fun Density.fromMaxHeight(maxHeight: Dp): BrowseAnchors {
    val maxOffset = maxHeight.toPx()
    return BrowseAnchors(
        minOffset = 0f,
        fullWidthOffset = 0.2f * maxOffset,
        collapsedOffset = 0.3f * maxOffset,
        maxOffset = maxOffset
    )
}

data class BrowseAnchors(
    val minOffset: Float, // fully expanded
    val fullWidthOffset: Float, // slightly expanded (sheet is now full width)
    val collapsedOffset: Float, // initial collapsed state
    val maxOffset: Float, // dismissed offset
) {
    companion object {
        val EMPTY = BrowseAnchors(Float.MIN_VALUE, 0f, 0f, Float.MAX_VALUE)
    }
}

private fun correspondingNewOffset(
    oldOffset: Float,
    oldAnchors: BrowseAnchors,
    newAnchors: BrowseAnchors
): Float {
    return when (oldOffset) {
        oldAnchors.minOffset -> newAnchors.minOffset
        oldAnchors.fullWidthOffset -> newAnchors.fullWidthOffset
        oldAnchors.collapsedOffset -> newAnchors.collapsedOffset
        oldAnchors.maxOffset -> newAnchors.maxOffset
        else -> oldOffset
    }
}


/**
 * The min velocity threshold for decay fling
 */
private val MinFlingVelocity = 200.dp

@Composable
fun rememberBrowseableState(
    onDismiss: () -> Unit = {}
): BrowseableState {
    val flingDecaySpec = rememberSplineBasedDecay<Float>()
    val minFlingVelocity = with(LocalDensity.current) {
        MinFlingVelocity.toPx()
    }

    return remember {
        BrowseableState(
            onDismiss = onDismiss,
            flingDecaySpec = flingDecaySpec,
            minFlingVelocity = minFlingVelocity
        )
    }
}

fun Modifier.browseable(
    state: BrowseableState,
    anchors: BrowseAnchors,
    interactionSource: MutableInteractionSource? = null
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "browseable"
        properties["state"] = state
        properties["anchors"] = anchors
        properties["interactionSource"] = interactionSource
    }
) {
    val dragDelegate = remember(state) {
        BrowseableDragDelegate(state)
    }

    val context = LocalContext.current

    val onPreFling: (Float) -> Unit = {velocity: Float ->
        Toast.makeText(context, "Velocity: $velocity", Toast.LENGTH_SHORT).show()
    }

    val nestedScrollConnection = remember(state) {
        BrowseableNestedScrollConnection(state, onPreFling)
    }

    state.ensureInit(anchors)

    LaunchedEffect(anchors) {
        val oldAnchors = state.anchors
        state.anchors = anchors
        state.processNewAnchors(oldAnchors, anchors)
    }

    draggable(
        state = dragDelegate.draggableState,
        orientation = Orientation.Vertical,
        interactionSource = interactionSource,
        reverseDirection = false,
        onDragStopped = { velocity -> dragDelegate.onDragStopped(velocity) }
    ).nestedScroll(nestedScrollConnection)
}

class BrowseableDragDelegate(
    private val state: BrowseableState,
) {
    val draggableState = DraggableState {
        state.performDrag(it)
    }

    suspend fun onDragStopped(axisVelocity: Float) {
        Log.d("BROWSEABLE", "Delegate.onDragStopped($axisVelocity)")
        state.performFling(axisVelocity)
    }
}

private fun Float.toVelocity(): Velocity {
    return Velocity(x = 0f, y = this)
}

private fun Float.toOffset(): Offset {
    return Offset(x = 0f, y = this)
}

private fun Offset.toFloat() = this.y

private class BrowseableNestedScrollConnection(
    val state: BrowseableState,
    val onPreFling: (velocity: Float) -> Unit,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        val delta = available.y
        return if (delta < 0 && state.offset.value > state.anchors.minOffset) {
            state.performDrag(delta).toOffset()
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        val delta = available.y
        return state.performDrag(delta).toOffset()
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        val velocity = available.y
        onPreFling(velocity)
        if (velocity < 0 && state.offset.value > state.anchors.minOffset) {
            return state.performFling(velocity).toVelocity()
        }
        return super.onPreFling(available)
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val velocity = Offset(available.x, available.y).toFloat()
        return state.performFling(velocity).toVelocity()
    }
}

class BrowseableState(
    flingDecaySpec: DecayAnimationSpec<Float>,
    minFlingVelocity: Float,
    internal val onDismiss: () -> Unit,
    private val animationSpec: AnimationSpec<Float> = SpringSpec(),
) {
    private val flingBehavior = DefaultFlingBehavior(flingDecaySpec, minFlingVelocity)
    /**
     * Whether the state is currently animating.
     */
    var isAnimationRunning: Boolean by mutableStateOf(false)
        private set

    // resistance, bounds applied
    val offset: State<Float> get() = offsetState
    private val offsetState = mutableStateOf(0f)

    private val absoluteOffset = mutableStateOf(0f)

    // current animation target, if animating, otherwise null
    private val animationTarget = mutableStateOf<Float?>(null)

    internal var anchors by mutableStateOf(BrowseAnchors.EMPTY)

    internal fun ensureInit(newAnchors: BrowseAnchors) {
        if (anchors == BrowseAnchors.EMPTY) {
            offsetState.value = newAnchors.collapsedOffset
        }
    }

    internal fun processNewAnchors(
        oldAnchors: BrowseAnchors,
        newAnchors: BrowseAnchors
    ) {
        if (oldAnchors != BrowseAnchors.EMPTY &&
            newAnchors != oldAnchors) {
            val animationTargetValue = animationTarget.value
            val targetOffset = if (animationTargetValue != null) {
                correspondingNewOffset(animationTargetValue, oldAnchors, newAnchors)
            } else {
                correspondingNewOffset(offset.value, oldAnchors, newAnchors)
            }

            snapToOffset(targetOffset)
        }
    }

    suspend fun dismiss() {
        animateToOffset(anchors.maxOffset)
    }

    fun performDrag(delta: Float): Float {
        val potentialOffset = offsetState.value + delta
        val clamped = potentialOffset.coerceIn(anchors.minOffset, anchors.maxOffset)
        val consumed = clamped - offsetState.value
        offsetState.value = clamped
        return consumed
    }

    suspend fun performFling(velocity: Float): Float {
        val scope = object: ScrollScope {
            override fun scrollBy(pixels: Float): Float {
                return performDrag(pixels)
            }
        }
        val remainingVelocity = with (scope) {
            with(flingBehavior) {
                performFling(velocity)
            }
        }

        return velocity - remainingVelocity
    }

    private suspend fun animateToOffset(target: Float) {
        val scope = object: DragScope {
            override fun dragBy(pixels: Float) {
                performDrag(pixels)
            }
        }
        with(scope) {
            var prevValue = offsetState.value
            isAnimationRunning = true
            try {
                Animatable(prevValue).animateTo(target, animationSpec) {
                    dragBy(this.value - prevValue)
                    prevValue = this.value
                }
            } finally {
                isAnimationRunning = false
            }
        }
    }

    private fun snapToOffset(target: Float) {
        offsetState.value = target
        absoluteOffset.value = target
    }

    private fun computeTargetAnchorOffset(): Float {
        val curOffset = offsetState.value
        val targetOffset = with(anchors) {
            listOf(minOffset, fullWidthOffset, collapsedOffset, maxOffset)
                .map { it to abs(curOffset - it) }
                .minByOrNull { it.second }
                ?.first ?: minOffset
        }

        return targetOffset
    }
}


private class DefaultFlingBehavior(
    private val flingDecay: DecayAnimationSpec<Float>,
    private val minFlingVelocity: Float,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        // come up with the better threshold, but we need it since spline curve gives us NaNs
        return if (abs(initialVelocity) > 1f) {
            var velocityLeft = initialVelocity
            var lastValue = 0f
            AnimationState(
                initialValue = 0f,
                initialVelocity = initialVelocity,
            ).animateDecay(flingDecay) {
                val delta = value - lastValue
                val consumed = scrollBy(delta)
                lastValue = value
                velocityLeft = this.velocity
                // avoid rounding errors and stop if anything is unconsumed
                if (abs(delta - consumed) > 0.5f) this.cancelAnimation()
            }
            velocityLeft
        } else {
            initialVelocity
        }
    }
}
