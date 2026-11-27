package com.customcall.feature.customizer

import com.customcall.core.model.ButtonStyleType
import com.customcall.feature.customizer.platform.PickedVideoAsset
import com.customcall.feature.customizer.presentation.ThemeStudioViewModel
import com.customcall.feature.customizer.presentation.TranscodingStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeStudioViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeCallRepository
    private lateinit var processor: FakeVideoAssetProcessor
    private lateinit var viewModel: ThemeStudioViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeCallRepository()
        processor = FakeVideoAssetProcessor()
        viewModel = ThemeStudioViewModel(
            callRepository = repository,
            videoAssetProcessor = processor,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun overlayGradientAlphaIsClampedWithinUnitBounds() {
        viewModel.updateOverlayGradientAlpha(1.75f)
        assertEquals(1.0f, viewModel.uiState.value.draftTheme.overlayGradientAlpha)

        viewModel.updateOverlayGradientAlpha(-0.4f)
        assertEquals(0.0f, viewModel.uiState.value.draftTheme.overlayGradientAlpha)
    }

    @Test
    fun savingPickedVideoTranscodesFirstAndPersistsInternalUri() = runTest {
        viewModel.onVideoPicked(
            PickedVideoAsset(
                uri = "content://media/external/video/media/42",
                durationMs = 12_000L,
                displayName = "cyber_loop.mp4",
            ),
        )
        viewModel.updateTrimRange(startMs = 1_000L, endMs = 6_000L)
        viewModel.updateButtonStyle(ButtonStyleType.CLASSIC_DUAL)
        viewModel.updateMuted(false)

        viewModel.saveDraftTheme()

        assertEquals(1, processor.recordedRequests.size)
        val request = processor.recordedRequests.single()
        assertEquals("content://media/external/video/media/42", request.sourceUri)
        assertEquals(1_000L, request.trimStartMs)
        assertEquals(6_000L, request.trimEndMs)

        val savedThemes = repository.observeThemes().first()
        assertEquals(1, savedThemes.size)
        val saved = savedThemes.single()
        assertEquals("cyber_loop", saved.name)
        assertEquals(ButtonStyleType.CLASSIC_DUAL, saved.buttonStyle)
        assertFalse(saved.isMuted)
        assertTrue(saved.videoUri.startsWith("file:///data/user/0/com.customcall/files/themes/"))
        assertEquals(saved.id, repository.observeAppSettings().first().defaultThemeId)
        assertNull(viewModel.uiState.value.pendingSourceUri)
    }

    @Test
    fun transcodingFailureSurfacesErrorWithoutPersistingBrokenTheme() = runTest {
        processor.mode = FakeVideoAssetProcessor.Mode.FAIL
        processor.failureMessage = "Corrupted H.264 stream"

        viewModel.onVideoPicked(
            PickedVideoAsset(
                uri = "content://media/external/video/media/99",
                durationMs = 8_000L,
                displayName = "broken.mp4",
            ),
        )
        viewModel.saveDraftTheme()

        assertIs<TranscodingStatus.Error>(viewModel.uiState.value.transcodingStatus)
        assertTrue(repository.observeThemes().first().isEmpty())
    }

    @Test
    fun deletingThemeRemovesPersistenceAndProcessedFile() = runTest {
        viewModel.updateThemeName("Midnight Neon")
        viewModel.saveDraftTheme()

        val savedTheme = repository.observeThemes().first().single()
        viewModel.deleteTheme(savedTheme.id)

        assertTrue(repository.observeThemes().first().isEmpty())
        assertEquals(listOf(savedTheme.id), repository.deletedThemeIds)
        assertEquals(listOf(savedTheme.id), processor.deletedAssetThemeIds)
        assertFalse(viewModel.uiState.value.isEditingExisting)
    }
}