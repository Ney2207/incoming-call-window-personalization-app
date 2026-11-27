package com.customcall.feature.customizer.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberContactPickerLauncher(
    onContactPicked: (PickedDeviceContact?) -> Unit,
    onPermissionDenied: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnContactPicked by rememberUpdatedState(onContactPicked)
    val currentOnPermissionDenied by rememberUpdatedState(onPermissionDenied)

    val pickContactLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact(),
    ) { contactUri: Uri? ->
        if (contactUri == null) {
            currentOnContactPicked(null)
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            val resolvedContact = withContext(Dispatchers.IO) {
                resolvePickedContact(context = context, contactUri = contactUri)
            }
            currentOnContactPicked(resolvedContact)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted: Boolean ->
        if (isGranted) {
            pickContactLauncher.launch(null)
        } else {
            currentOnPermissionDenied()
        }
    }

    return {
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasContactsPermission) {
            pickContactLauncher.launch(null)
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        }
    }
}

private fun resolvePickedContact(
    context: Context,
    contactUri: Uri,
): PickedDeviceContact? = runCatching {
    val resolver = context.contentResolver

    var contactId: String? = null
    var displayName = ""

    resolver.query(
        contactUri,
        arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            if (idIdx >= 0) contactId = cursor.getString(idIdx)
            if (nameIdx >= 0) displayName = cursor.getString(nameIdx).orEmpty()
        }
    }

    val resolvedId = contactId ?: return@runCatching null

    var phoneNumber: String? = null
    resolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ),
        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
        arrayOf(resolvedId),
        "${ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY} DESC, " +
            "${ContactsContract.CommonDataKinds.Phone.IS_PRIMARY} DESC",
    )?.use { phoneCursor ->
        if (phoneCursor.moveToFirst()) {
            val numberIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val fallbackNameIdx = phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            if (numberIdx >= 0) {
                phoneNumber = phoneCursor.getString(numberIdx)?.trim()
            }
            if (displayName.isBlank() && fallbackNameIdx >= 0) {
                displayName = phoneCursor.getString(fallbackNameIdx).orEmpty()
            }
        }
    }

    val validNumber = phoneNumber?.takeIf { it.isNotBlank() } ?: return@runCatching null
    PickedDeviceContact(
        displayName = displayName.ifBlank { validNumber },
        phoneNumber = validNumber,
    )
}.getOrNull()