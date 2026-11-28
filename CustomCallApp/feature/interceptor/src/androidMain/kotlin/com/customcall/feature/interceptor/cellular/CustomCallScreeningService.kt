package com.customcall.feature.interceptor.cellular

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.customcall.core.model.CallSource
import com.customcall.core.model.IncomingCallEvent
import com.customcall.core.model.contract.CallEventDispatcher
import com.customcall.core.model.contract.CallRepository
import com.customcall.core.model.util.IdentifierNormalizer
import com.customcall.feature.interceptor.session.CallSessionRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Android [CallScreeningService] (API 29+) intercepting inbound GSM calls without forcing
 * the app to replace the user's default system dialer.
 *
 * Extracts caller phone number and display metadata, publishes [IncomingCallEvent] when
 * [CallSource.PHONE_GSM] is enabled, and immediately allows the call through unblocked.
 */
@RequiresApi(Build.VERSION_CODES.Q)
class CustomCallScreeningService : CallScreeningService(), KoinComponent {

    private val sessionRegistry: CallSessionRegistry by inject()
    private val eventDispatcher: CallEventDispatcher by inject()
    private val callRepository: CallRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onScreenCall(callDetails: Call.Details) {
        // Always respond promptly so cellular call setup is never delayed.
        respondToCall(callDetails, CallResponse.Builder().build())

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            return
        }

        serviceScope.launch {
            val settings = runCatching { callRepository.observeAppSettings().first() }.getOrNull()
            if (settings != null && CallSource.PHONE_GSM !in settings.enabledSources) {
                return@launch
            }

            val rawNumber = callDetails.handle?.schemeSpecificPart?.trim().orEmpty()
            val normalizedIdentifier = IdentifierNormalizer.normalizePhoneNumber(rawNumber)
                .ifBlank { UNKNOWN_IDENTIFIER }

            val resolvedName = resolveCallerName(callDetails, rawNumber)
            val callId = buildCallId(normalizedIdentifier)

            val event = IncomingCallEvent(
                callId = callId,
                source = CallSource.PHONE_GSM,
                callerName = resolvedName,
                callerIdentifier = normalizedIdentifier,
                avatarUri = null,
                hasAnswerAction = true,
                hasDeclineAction = true,
                timestamp = callDetails.creationTimeMillis.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
            )

            val isNewSession = sessionRegistry.registerOrUpdateIncomingCall(event)
            if (isNewSession) {
                eventDispatcher.dispatchIncomingCall(event)
            }
        }
    }

    private fun resolveCallerName(callDetails: Call.Details, rawNumber: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val contactName = callDetails.contactDisplayName?.toString()?.trim()
            if (!contactName.isNullOrEmpty()) return contactName
        }

        val networkDisplayName = callDetails.callerDisplayName?.trim()
        if (!networkDisplayName.isNullOrEmpty()) return networkDisplayName

        if (rawNumber.isNotEmpty()) {
            val contactLookupName = lookupContactNameByNumber(rawNumber)
            if (!contactLookupName.isNullOrEmpty()) return contactLookupName
            return rawNumber
        }

        return PRIVATE_CALLER_NAME
    }

    private fun lookupContactNameByNumber(phoneNumber: String): String? {
        val hasContactsPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasContactsPermission) return null

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber),
        )
        return try {
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.trim()?.takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun buildCallId(normalizedIdentifier: String): String {
        return "gsm_${normalizedIdentifier}_${System.currentTimeMillis()}"
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val PRIVATE_CALLER_NAME = "Private Number"
        private const val UNKNOWN_IDENTIFIER = "private_number"
    }
}