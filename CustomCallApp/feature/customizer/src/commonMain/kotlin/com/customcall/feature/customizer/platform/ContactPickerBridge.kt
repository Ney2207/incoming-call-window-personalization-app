package com.customcall.feature.customizer.platform

import androidx.compose.runtime.Composable

/**
 * Contact display name and raw phone number extracted from the device address book.
 */
data class PickedDeviceContact(
    val displayName: String,
    val phoneNumber: String,
)

/**
 * Registers a platform contact picker launcher bound to the current Compose lifecycle.
 *
 * On Android, the actual implementation handles requesting `READ_CONTACTS` runtime permission
 * before opening the system contact picker, invoking [onPermissionDenied] if denied and
 * [onContactPicked] with `null` if the picker is cancelled or the contact lacks a phone number.
 */
@Composable
expect fun rememberContactPickerLauncher(
    onContactPicked: (PickedDeviceContact?) -> Unit,
    onPermissionDenied: () -> Unit = {},
): () -> Unit