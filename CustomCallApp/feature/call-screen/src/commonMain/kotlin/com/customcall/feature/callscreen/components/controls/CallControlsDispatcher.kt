package com.customcall.feature.callscreen.components.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.IncomingCallEvent

/**
 * Single-shot action guard preventing rapid taps or simultaneous swipe gestures from
 * invoking [onAnswer] or [onDecline] more than once per incoming call session.
 */
class SingleShotActionGuard {
    private var triggered = false

    val isTriggered: Boolean
        get() = triggered

    fun tryFire(action: () -> Unit): Boolean {
        if (triggered) return false
        triggered = true
        action()
        return true
    }

    fun reset() {
        triggered = false
    }
}

/**
 * **Z-Index Layer 4 (Interactive Controls Layer):** Routes [buttonStyle] to the active
 * control layout while respecting [IncomingCallEvent.hasAnswerAction],
 * [IncomingCallEvent.hasDeclineAction], and single-shot action firing per [event.callId].
 */
@Composable
fun CallControlsDispatcher(
    event: IncomingCallEvent,
    buttonStyle: ButtonStyleType,
    accentColor: Color,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnAnswer by rememberUpdatedState(onAnswer)
    val latestOnDecline by rememberUpdatedState(onDecline)
    val actionGuard = remember(event.callId) { SingleShotActionGuard() }

    val guardedAnswer: () -> Unit = remember(actionGuard) {
        { actionGuard.tryFire { latestOnAnswer() } }
    }
    val guardedDecline: () -> Unit = remember(actionGuard) {
        { actionGuard.tryFire { latestOnDecline() } }
    }

    when (buttonStyle) {
        ButtonStyleType.CLASSIC_DUAL -> {
            ClassicDualControls(
                accentColor = accentColor,
                hasAnswerAction = event.hasAnswerAction,
                hasDeclineAction = event.hasDeclineAction,
                onAnswer = guardedAnswer,
                onDecline = guardedDecline,
                modifier = modifier,
            )
        }
        ButtonStyleType.SWIPE_PILL -> {
            SwipePillControls(
                accentColor = accentColor,
                hasAnswerAction = event.hasAnswerAction,
                hasDeclineAction = event.hasDeclineAction,
                onAnswer = guardedAnswer,
                onDecline = guardedDecline,
                modifier = modifier,
            )
        }
        ButtonStyleType.MINIMAL_GESTURE -> {
            MinimalGestureControls(
                accentColor = accentColor,
                hasAnswerAction = event.hasAnswerAction,
                hasDeclineAction = event.hasDeclineAction,
                onAnswer = guardedAnswer,
                onDecline = guardedDecline,
                modifier = modifier,
            )
        }
    }
}