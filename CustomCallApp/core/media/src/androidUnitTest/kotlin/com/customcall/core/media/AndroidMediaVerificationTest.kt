package com.customcall.core.media

import android.content.Context
import android.content.ContextWrapper
import com.customcall.core.media.di.mediaModule
import com.customcall.core.media.player.FAST_START_BUFFER_AFTER_REBUFFER_MS
import com.customcall.core.media.player.FAST_START_BUFFER_FOR_PLAYBACK_MS
import com.customcall.core.media.player.FAST_START_MAX_BUFFER_MS
import com.customcall.core.media.player.FAST_START_MIN_BUFFER_MS
import com.customcall.core.media.player.buildSub200msLoadControl
import com.customcall.core.model.VideoProcessingRequest
import com.customcall.core.model.VideoProcessingState
import com.customcall.core.model.contract.VideoAssetProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidMediaVerificationTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun buildSub200msLoadControl_enforcesLowLatencyBufferThresholds() {
        val loadControl = buildSub200msLoadControl()
        assertNotNull(loadControl)
        assertTrue(FAST_START_BUFFER_FOR_PLAYBACK_MS < 200)
        assertTrue(FAST_START_BUFFER_AFTER_REBUFFER_MS < 200)
        assertTrue(FAST_START_MIN_BUFFER_MS >= FAST_START_BUFFER_AFTER_REBUFFER_MS)
        assertTrue(FAST_START_MAX_BUFFER_MS >= FAST_START_MIN_BUFFER_MS)
    }

    @Test
    fun mediaModule_resolvesVideoAssetProcessor() {
        val stubContext: Context = ContextWrapper(null)
        val app = koinApplication {
            modules(
                module { single<Context> { stubContext } },
                mediaModule,
            )
        }
        try {
            val processor = app.koin.get<VideoAssetProcessor>()
            assertNotNull(processor)
        } finally {
            app.close()
        }
    }

    @Test
    fun processVideo_emitsFailedWhenSourceUriIsUnreadable() = runTest {
        val stubContext: Context = ContextWrapper(null)
        val app = koinApplication {
            modules(
                module { single<Context> { stubContext } },
                mediaModule,
            )
        }
        try {
            val processor = app.koin.get<VideoAssetProcessor>()

            val states = processor.processVideo(
                VideoProcessingRequest(
                    themeId = "theme_1",
                    sourceUri = "file:///nonexistent/missing_video.mp4",
                ),
            ).toList()

            assertIs<VideoProcessingState.InProgress>(states.first())
            assertIs<VideoProcessingState.Failed>(states.last())
        } finally {
            app.close()
        }
    }
}