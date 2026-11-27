package com.customcall.feature.customizer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.customcall.core.model.AppSettings
import com.customcall.core.model.contract.CallRepository
import com.customcall.feature.customizer.platform.DiagnosticPermissionType
import com.customcall.feature.customizer.platform.SystemDiagnosticController
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PermissionWizardViewModel(
    private val systemDiagnosticController: SystemDiagnosticController,
    private val callRepository: CallRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionWizardUiState())
    val uiState: StateFlow<PermissionWizardUiState> = _uiState.asStateFlow()

    private var latestSettings: AppSettings = AppSettings()

    init {
        callRepository.observeAppSettings()
            .onEach { settings ->
                latestSettings = settings
                _uiState.update {
                    it.copy(isOnboardingCompleted = settings.isOnboardingCompleted)
                }
            }
            .launchIn(viewModelScope)

        refreshDiagnostics()
    }

    fun refreshDiagnostics() {
        viewModelScope.launch {
            _uiState.update { it.copy(isInspecting = true) }
            val inspectedPermissions = systemDiagnosticController.inspectPermissions()
            val guide = systemDiagnosticController.getOemAutostartGuide()
            _uiState.update {
                it.copy(
                    permissions = inspectedPermissions,
                    oemGuide = guide,
                    isInspecting = false,
                )
            }
        }
    }

    fun openPermissionSettings(type: DiagnosticPermissionType) {
        systemDiagnosticController.openPermissionSettings(type)
    }

    fun openOemAutostartSettings() {
        val openedDirectly = systemDiagnosticController.openOemAutostartSettings()
        if (!openedDirectly) {
            systemDiagnosticController.openAppDetailsSettings()
            _uiState.update {
                it.copy(
                    statusMessage = "Opened App Info. Follow the OEM steps below to enable Autostart and Unrestricted Battery.",
                )
            }
        }
    }

    fun openAppDetailsSettings() {
        systemDiagnosticController.openAppDetailsSettings()
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            callRepository.updateAppSettings(
                latestSettings.copy(isOnboardingCompleted = true),
            )
            _uiState.update {
                it.copy(
                    isOnboardingCompleted = true,
                    statusMessage = "Setup checklist marked complete.",
                )
            }
        }
    }

    fun dismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}