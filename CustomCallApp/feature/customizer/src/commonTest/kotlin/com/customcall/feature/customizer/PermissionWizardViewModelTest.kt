package com.customcall.feature.customizer

import com.customcall.feature.customizer.platform.DiagnosticPermissionType
import com.customcall.feature.customizer.platform.PermissionItemStatus
import com.customcall.feature.customizer.presentation.PermissionWizardViewModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionWizardViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeCallRepository
    private lateinit var diagnostics: FakeSystemDiagnosticController
    private lateinit var viewModel: PermissionWizardViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeCallRepository()
        diagnostics = FakeSystemDiagnosticController()
        viewModel = PermissionWizardViewModel(
            systemDiagnosticController = diagnostics,
            callRepository = repository,
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun allRequiredGrantedIgnoresOptionalBatteryOptimization() = runTest {
        assertFalse(viewModel.uiState.value.allRequiredGranted)

        diagnostics.permissionsResult = listOf(
            PermissionItemStatus(DiagnosticPermissionType.NOTIFICATION_LISTENER, isGranted = true, isRequiredOnCurrentOs = true),
            PermissionItemStatus(DiagnosticPermissionType.OVERLAY, isGranted = true, isRequiredOnCurrentOs = true),
            PermissionItemStatus(DiagnosticPermissionType.FULL_SCREEN_INTENT, isGranted = true, isRequiredOnCurrentOs = true),
            PermissionItemStatus(DiagnosticPermissionType.BATTERY_OPTIMIZATION, isGranted = false, isRequiredOnCurrentOs = false),
        )

        viewModel.refreshDiagnostics()

        assertTrue(viewModel.uiState.value.allRequiredGranted)
        assertEquals(3, viewModel.uiState.value.grantedCount)
    }

    @Test
    fun oemAutostartFallsBackToAppInfoWhenDirectIntentUnavailable() = runTest {
        diagnostics.canLaunchOemDirectly = false

        viewModel.openOemAutostartSettings()

        assertEquals(1, diagnostics.oemSettingsLaunchCount)
        assertEquals(1, diagnostics.appDetailsLaunchCount)
        assertNotNull(viewModel.uiState.value.statusMessage)
    }

    @Test
    fun completeOnboardingPersistsFlagInAppSettings() = runTest {
        viewModel.completeOnboarding()

        assertTrue(viewModel.uiState.value.isOnboardingCompleted)
        assertTrue(repository.observeAppSettings().first().isOnboardingCompleted)
    }
}