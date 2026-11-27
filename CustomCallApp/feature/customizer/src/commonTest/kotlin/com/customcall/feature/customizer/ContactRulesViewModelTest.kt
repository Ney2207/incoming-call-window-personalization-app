package com.customcall.feature.customizer

import com.customcall.core.model.ButtonStyleType
import com.customcall.core.model.CallSource
import com.customcall.core.model.CallThemeConfig
import com.customcall.feature.customizer.platform.PickedDeviceContact
import com.customcall.feature.customizer.presentation.ContactRulesViewModel
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ContactRulesViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeCallRepository
    private lateinit var viewModel: ContactRulesViewModel

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = FakeCallRepository()
        viewModel = ContactRulesViewModel(callRepository = repository)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun savingPickedContactNormalizesPhoneAndDisplayName() = runTest {
        val sampleTheme = CallThemeConfig(
            id = "theme_alpha",
            name = "Alpha",
            videoUri = "",
            buttonStyle = ButtonStyleType.SWIPE_PILL,
            accentColorHex = 0xFF3B82F6L,
        )
        repository.upsertTheme(sampleTheme)

        viewModel.openEditorFromPickedContact(
            PickedDeviceContact(
                displayName = "  Alice   M.  ",
                phoneNumber = "+1 (555) 234-5678",
            ),
        )
        viewModel.updateEditorSourceFilter(CallSource.WHATSAPP)
        viewModel.saveContactRule()

        val savedRules = repository.observeContactRules().first()
        assertEquals(1, savedRules.size)
        val rule = savedRules.single()
        assertEquals("+15552345678", rule.callerIdentifier)
        assertEquals("alice m.", rule.normalizedName)
        assertEquals(CallSource.WHATSAPP, rule.sourceFilter)
        assertEquals("theme_alpha", rule.themeId)
    }

    @Test
    fun validationBlocksBlankIdentifierAndName() = runTest {
        repository.upsertTheme(
            CallThemeConfig(
                id = "theme_beta",
                name = "Beta",
                videoUri = "",
                accentColorHex = 0xFF10B981L,
            ),
        )

        viewModel.openNewManualRuleEditor()
        viewModel.saveContactRule()

        assertNotNull(viewModel.uiState.value.editor.validationError)
        assertTrue(repository.observeContactRules().first().isEmpty())
    }

    @Test
    fun deletingRuleRemovesItFromRepository() = runTest {
        repository.upsertTheme(
            CallThemeConfig(
                id = "theme_gamma",
                name = "Gamma",
                videoUri = "",
                accentColorHex = 0xFFF43F5EL,
            ),
        )

        viewModel.openNewManualRuleEditor()
        viewModel.updateEditorIdentifier("@JohnDoe_Dev")
        viewModel.updateEditorSourceFilter(CallSource.TELEGRAM)
        viewModel.saveContactRule()

        val rule = repository.observeContactRules().first().single()
        assertEquals("@johndoe_dev", rule.callerIdentifier)

        viewModel.deleteContactRule(rule.id)
        assertTrue(repository.observeContactRules().first().isEmpty())
    }
}