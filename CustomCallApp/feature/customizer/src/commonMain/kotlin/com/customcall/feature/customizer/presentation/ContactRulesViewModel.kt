package com.customcall.feature.customizer.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.customcall.core.model.CallSource
import com.customcall.core.model.ContactRule
import com.customcall.core.model.contract.CallRepository
import com.customcall.core.model.util.IdentifierNormalizer
import com.customcall.feature.customizer.platform.PickedDeviceContact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ContactRulesViewModel(
    private val callRepository: CallRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactRulesUiState())
    val uiState: StateFlow<ContactRulesUiState> = _uiState.asStateFlow()

    init {
        combine(
            callRepository.observeContactRules(),
            callRepository.observeThemes(),
        ) { rules, themes ->
            val themeById = themes.associateBy { it.id }
            val joinedItems = rules.map { rule ->
                ContactRuleItemUi(
                    rule = rule,
                    assignedTheme = themeById[rule.themeId],
                )
            }
            _uiState.update { state ->
                val defaultSelectedTheme = state.editor.selectedThemeId
                    .takeIf { id -> themes.any { it.id == id } }
                    ?: themes.firstOrNull()?.id.orEmpty()
                state.copy(
                    rules = joinedItems,
                    availableThemes = themes,
                    editor = state.editor.copy(selectedThemeId = defaultSelectedTheme),
                )
            }
        }.launchIn(viewModelScope)
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun openNewManualRuleEditor() {
        val defaultThemeId = _uiState.value.availableThemes.firstOrNull()?.id.orEmpty()
        _uiState.update { state ->
            state.copy(
                editor = ContactRuleEditorState(
                    isVisible = true,
                    editingRuleId = null,
                    displayName = "",
                    identifierInput = "",
                    sourceFilter = null,
                    selectedThemeId = defaultThemeId,
                    validationError = null,
                ),
            )
        }
    }

    fun openEditorFromPickedContact(contact: PickedDeviceContact?) {
        if (contact == null) return
        val defaultThemeId = _uiState.value.availableThemes.firstOrNull()?.id.orEmpty()
        _uiState.update { state ->
            state.copy(
                editor = ContactRuleEditorState(
                    isVisible = true,
                    editingRuleId = null,
                    displayName = contact.displayName,
                    identifierInput = contact.phoneNumber,
                    sourceFilter = null,
                    selectedThemeId = defaultThemeId,
                    validationError = null,
                ),
            )
        }
    }

    fun openEditRuleEditor(rule: ContactRule) {
        _uiState.update { state ->
            state.copy(
                editor = ContactRuleEditorState(
                    isVisible = true,
                    editingRuleId = rule.id,
                    displayName = rule.normalizedName,
                    identifierInput = rule.callerIdentifier,
                    sourceFilter = rule.sourceFilter,
                    selectedThemeId = rule.themeId,
                    validationError = null,
                ),
            )
        }
    }

    fun closeEditor() {
        _uiState.update { state ->
            state.copy(editor = ContactRuleEditorState())
        }
    }

    fun updateEditorDisplayName(name: String) {
        _uiState.update { state ->
            state.copy(
                editor = state.editor.copy(
                    displayName = name,
                    validationError = null,
                ),
            )
        }
    }

    fun updateEditorIdentifier(identifier: String) {
        _uiState.update { state ->
            state.copy(
                editor = state.editor.copy(
                    identifierInput = identifier,
                    validationError = null,
                ),
            )
        }
    }

    fun updateEditorSourceFilter(source: CallSource?) {
        _uiState.update { state ->
            state.copy(editor = state.editor.copy(sourceFilter = source))
        }
    }

    fun updateEditorThemeId(themeId: String) {
        _uiState.update { state ->
            state.copy(
                editor = state.editor.copy(
                    selectedThemeId = themeId,
                    validationError = null,
                ),
            )
        }
    }

    fun saveContactRule() {
        val snapshot = _uiState.value
        val editor = snapshot.editor

        val normalizedIdentifier = IdentifierNormalizer.normalizeIdentifier(editor.identifierInput)
        val normalizedName = IdentifierNormalizer.normalizeDisplayName(editor.displayName)

        if (normalizedIdentifier.isBlank() && normalizedName.isBlank()) {
            _uiState.update {
                it.copy(
                    editor = editor.copy(
                        validationError = "Enter a phone number, VoIP username, or caller display name.",
                    ),
                )
            }
            return
        }

        if (editor.selectedThemeId.isBlank()) {
            _uiState.update {
                it.copy(
                    editor = editor.copy(
                        validationError = "Create and select a call theme before saving a rule.",
                    ),
                )
            }
            return
        }

        val rule = ContactRule(
            id = editor.editingRuleId ?: CustomizerDefaults.generateId("rule"),
            callerIdentifier = normalizedIdentifier,
            normalizedName = normalizedName,
            sourceFilter = editor.sourceFilter,
            themeId = editor.selectedThemeId,
        )

        viewModelScope.launch {
            callRepository.upsertContactRule(rule)
            _uiState.update { state ->
                state.copy(
                    editor = ContactRuleEditorState(),
                    statusMessage = "Contact rule saved.",
                )
            }
        }
    }

    fun deleteContactRule(ruleId: String) {
        viewModelScope.launch {
            callRepository.deleteContactRule(ruleId)
            _uiState.update { state ->
                state.copy(statusMessage = "Contact rule removed.")
            }
        }
    }

    fun notifyPermissionDenied() {
        _uiState.update {
            it.copy(
                statusMessage = "Contacts permission denied. You can still add contacts or VoIP handles manually.",
            )
        }
    }

    fun dismissStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}