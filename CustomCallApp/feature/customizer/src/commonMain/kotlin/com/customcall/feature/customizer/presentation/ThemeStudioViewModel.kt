package com.customcall.feature.customizer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallThemeConfig
import com.customcall.core.model.VideoProcessingRequest
import com.customcall.core.model.VideoProcessingState
import com.customcall.core.model.contract.CallRepository
import com.customcall.core.model.contract.VideoAssetProcessor
import com.customcall.feature.customizer.platform.PickedVideoAsset
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ThemeStudioViewModel(
    private val callRepository: CallRepository,
    private val videoAssetProcessor: VideoAssetProcessor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ThemeStudioUiState())
    val uiState: StateFlow<ThemeStudioUiState> = _uiState.asStateFlow()

    private var transcodingJob: Job? = null

    init {
        callRepository.observeThemes()
            .onEach { themes ->
                _uiState.update { it.copy(savedThemes = themes) }
            }
            .launchIn(viewModelScope)

        callRepository.observeAppSettings()
            .onEach { settings ->
                _uiState.update { it.copy(defaultThemeId = settings.defaultThemeId) }
            }
            .launchIn(viewModelScope)
    }

    fun startNewTheme() {
        cancelActiveTranscoding()
        _uiState.update { state ->
            state.copy(
                draftTheme = CustomizerDefaults.createBlankTheme(),
                isEditingExisting = false,
                pendingSourceUri = null,
                pendingVideoDisplayName = null,
                trimRange = VideoTrimRange(),
                transcodingStatus = TranscodingStatus.Idle,
                statusMessage = null,
            )
        }
    }

    fun selectThemeForEditing(theme: CallThemeConfig) {
        cancelActiveTranscoding()
        _uiState.update { state ->
            state.copy(
                draftTheme = theme.copy(
                    overlayGradientAlpha = theme.overlayGradientAlpha.coerceIn(0.0f, 1.0f),
                ),
                isEditingExisting = true,
                pendingSourceUri = null,
                pendingVideoDisplayName = null,
                trimRange = VideoTrimRange(),
                transcodingStatus = TranscodingStatus.Idle,
                statusMessage = null,
            )
        }
    }

    fun onVideoPicked(asset: PickedVideoAsset?) {
        if (asset == null || asset.uri.isBlank()) return
        cancelActiveTranscoding()
        val duration = asset.durationMs.coerceAtLeast(0L)
        _uiState.update { state ->
            val autoName = if (state.draftTheme.name.isBlank() && !asset.displayName.isNullOrBlank()) {
                asset.displayName.substringBeforeLast('.').take(32)
            } else {
                state.draftTheme.name
            }
            state.copy(
                draftTheme = state.draftTheme.copy(name = autoName),
                pendingSourceUri = asset.uri,
                pendingVideoDisplayName = asset.displayName,
                trimRange = VideoTrimRange(
                    startMs = 0L,
                    endMs = duration.takeIf { it > 0L },
                    totalDurationMs = duration,
                ),
                transcodingStatus = TranscodingStatus.Idle,
                statusMessage = null,
            )
        }
    }

    fun updateThemeName(name: String) {
        _uiState.update { state ->
            state.copy(draftTheme = state.draftTheme.copy(name = name))
        }
    }

    fun updateMuted(isMuted: Boolean) {
        _uiState.update { state ->
            state.copy(draftTheme = state.draftTheme.copy(isMuted = isMuted))
        }
    }

    fun updateOverlayGradientAlpha(alpha: Float) {
        val clamped = alpha.coerceIn(0.0f, 1.0f)
        _uiState.update { state ->
            state.copy(draftTheme = state.draftTheme.copy(overlayGradientAlpha = clamped))
        }
    }

    fun updateButtonStyle(style: ButtonStyleType) {
        _uiState.update { state ->
            state.copy(draftTheme = state.draftTheme.copy(buttonStyle = style))
        }
    }

    fun updateAccentColor(accentColorHex: Long) {
        _uiState.update { state ->
            state.copy(draftTheme = state.draftTheme.copy(accentColorHex = accentColorHex))
        }
    }

    fun updateTrimRange(startMs: Long, endMs: Long?) {
        _uiState.update { state ->
            val maxDuration = state.trimRange.totalDurationMs
            val safeStart = startMs.coerceAtLeast(0L)
            val safeEnd = endMs?.let { rawEnd ->
                val boundedEnd = if (maxDuration > 0L) rawEnd.coerceAtMost(maxDuration) else rawEnd
                if (boundedEnd > safeStart) boundedEnd else (safeStart + 500L)
            }
            val sourceForReTrim = state.pendingSourceUri
                ?: state.draftTheme.videoUri.takeIf { it.isNotBlank() }

            state.copy(
                pendingSourceUri = sourceForReTrim,
                trimRange = state.trimRange.copy(
                    startMs = safeStart,
                    endMs = safeEnd,
                ),
            )
        }
    }

    fun updatePreviewMetadata(
        callerName: String,
        callerIdentifier: String,
        source: CallSource,
    ) {
        _uiState.update { state ->
            state.copy(
                previewCallerName = callerName,
                previewCallerIdentifier = callerIdentifier,
                previewCallSource = source,
            )
        }
    }

    fun saveDraftTheme() {
        val snapshot = _uiState.value
        if (snapshot.isProcessing) return

        val resolvedName = snapshot.draftTheme.name.trim().ifBlank { "Custom Theme" }
        val baseTheme = snapshot.draftTheme.copy(
            name = resolvedName,
            overlayGradientAlpha = snapshot.draftTheme.overlayGradientAlpha.coerceIn(0.0f, 1.0f),
        )

        val sourceToTranscode = snapshot.pendingSourceUri?.takeIf { it.isNotBlank() }

        if (sourceToTranscode == null) {
            viewModelScope.launch {
                persistSavedTheme(baseTheme)
            }
            return
        }

        val validTrimEnd = snapshot.trimRange.endMs?.takeIf { it > snapshot.trimRange.startMs }
        val request = VideoProcessingRequest(
            themeId = baseTheme.id,
            sourceUri = sourceToTranscode,
            trimStartMs = snapshot.trimRange.startMs.coerceAtLeast(0L),
            trimEndMs = validTrimEnd,
            maxShortSidePx = 1080,
            targetFps = 30,
            stripAudio = false,
        )

        transcodingJob?.cancel()
        _uiState.update {
            it.copy(
                transcodingStatus = TranscodingStatus.InProgress(0f),
                statusMessage = null,
            )
        }

        transcodingJob = videoAssetProcessor.processVideo(request)
            .onEach { processingState ->
                when (processingState) {
                    is VideoProcessingState.InProgress -> {
                        _uiState.update {
                            it.copy(
                                transcodingStatus = TranscodingStatus.InProgress(
                                    processingState.progress.coerceIn(0f, 1f),
                                ),
                            )
                        }
                    }

                    is VideoProcessingState.Completed -> {
                        val committedTheme = baseTheme.copy(
                            videoUri = processingState.asset.outputUri,
                        )
                        persistSavedTheme(committedTheme)
                    }

                    is VideoProcessingState.Failed -> {
                        _uiState.update {
                            it.copy(
                                transcodingStatus = TranscodingStatus.Error(processingState.message),
                                statusMessage = "Video processing failed: ${processingState.message}",
                            )
                        }
                    }
                }
            }
            .catch { throwable ->
                val message = throwable.message ?: "Unexpected video transcoding error"
                _uiState.update {
                    it.copy(
                        transcodingStatus = TranscodingStatus.Error(message),
                        statusMessage = "Video processing failed: $message",
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun cancelActiveTranscoding() {
        transcodingJob?.cancel()
        transcodingJob = null
        _uiState.update { state ->
            if (state.transcodingStatus is TranscodingStatus.InProgress) {
                state.copy(transcodingStatus = TranscodingStatus.Idle)
            } else {
                state
            }
        }
    }

    fun setAsDefaultTheme(themeId: String) {
        viewModelScope.launch {
            callRepository.setDefaultThemeId(themeId)
            _uiState.update {
                it.copy(statusMessage = "Default incoming call theme updated.")
            }
        }
    }

    fun deleteTheme(themeId: String) {
        viewModelScope.launch {
            // Persist deletion first so ThemeRuleResolver immediately stops matching this theme
            callRepository.deleteTheme(themeId)
            // Remove processed file from private internal storage second
            videoAssetProcessor.deleteProcessedAsset(themeId)

            _uiState.update { state ->
                val resetDraft = state.draftTheme.id == themeId
                state.copy(
                    draftTheme = if (resetDraft) CustomizerDefaults.createBlankTheme() else state.draftTheme,
                    isEditingExisting = if (resetDraft) false else state.isEditingExisting,
                    pendingSourceUri = if (resetDraft) null else state.pendingSourceUri,
                    pendingVideoDisplayName = if (resetDraft) null else state.pendingVideoDisplayName,
                    trimRange = if (resetDraft) VideoTrimRange() else state.trimRange,
                    statusMessage = "Theme deleted.",
                )
            }
        }
    }

    fun dismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private suspend fun persistSavedTheme(theme: CallThemeConfig) {
        callRepository.upsertTheme(theme)
        if (_uiState.value.defaultThemeId == null) {
            callRepository.setDefaultThemeId(theme.id)
        }
        _uiState.update { state ->
            state.copy(
                draftTheme = theme,
                isEditingExisting = true,
                pendingSourceUri = null,
                pendingVideoDisplayName = null,
                trimRange = VideoTrimRange(),
                transcodingStatus = TranscodingStatus.Idle,
                statusMessage = "Theme \"${theme.name}\" saved.",
            )
        }
    }
}