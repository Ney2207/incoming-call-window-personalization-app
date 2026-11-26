package com.customcall.feature.callscreen.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.customcall.core.model.CallSource

@Immutable
private data class CallSourceVisualSpec(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
)

private fun CallSource.toVisualSpec(accentColor: Color): CallSourceVisualSpec {
    return when (this) {
        CallSource.PHONE_GSM -> CallSourceVisualSpec(
            label = "Phone Call",
            icon = Icons.Rounded.Phone,
            tint = accentColor,
        )
        CallSource.WHATSAPP -> CallSourceVisualSpec(
            label = "WhatsApp",
            icon = Icons.AutoMirrored.Rounded.Chat,
            tint = Color(0xFF25D366),
        )
        CallSource.WHATSAPP_BUSINESS -> CallSourceVisualSpec(
            label = "WhatsApp Business",
            icon = Icons.Rounded.Business,
            tint = Color(0xFF25D366),
        )
        CallSource.TELEGRAM -> CallSourceVisualSpec(
            label = "Telegram",
            icon = Icons.AutoMirrored.Rounded.Send,
            tint = Color(0xFF24A1DE),
        )
        CallSource.OTHER_VOIP -> CallSourceVisualSpec(
            label = "VoIP Call",
            icon = Icons.Rounded.Call,
            tint = Color(0xFF38BDF8),
        )
    }
}

/**
 * Translucent platform badge identifying the incoming [CallSource] (GSM telephony or VoIP app).
 */
@Composable
fun CallSourceBadge(
    source: CallSource,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val spec = source.toVisualSpec(accentColor)

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, spec.tint.copy(alpha = 0.48f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = spec.icon,
                contentDescription = spec.label,
                tint = spec.tint,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = spec.label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}