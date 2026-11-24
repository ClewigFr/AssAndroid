package fr.clewig.asslib.compose

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.testing.TestLifecycleOwner
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val logged = mutableListOf<String>()
    private val owner = TestLifecycleOwner(Lifecycle.State.RESUMED, Dispatchers.Unconfined)

    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) = compose.setContent {
        CompositionLocalProvider(LocalLifecycleOwner provides owner) { content() }
    }

    private fun moveTo(state: Lifecycle.State) = compose.runOnIdle { owner.currentState = state }

    @Test
    fun logsOnceWhenShown() {
        show { TrackScreen(HOME, this) { logged += it } }
        compose.waitForIdle()
        assertEquals(listOf(HOME), logged)
    }

    @Test
    fun logsAgainEachTimeTheAppComesBackToTheForeground() {
        show { TrackScreen(HOME, this) { logged += it } }
        moveTo(Lifecycle.State.CREATED) // app in the background
        moveTo(Lifecycle.State.RESUMED)
        moveTo(Lifecycle.State.STARTED) // a dialog or another window over it
        moveTo(Lifecycle.State.RESUMED)
        assertEquals(listOf(HOME, HOME, HOME), logged)
    }

    @Test
    fun doesNotLogOnRecomposition() {
        var counter by mutableIntStateOf(0)
        show {
            @Suppress("UNUSED_EXPRESSION") counter // read: recomposes this scope on each change
            TrackScreen(HOME, this) { logged += it }
        }
        repeat(3) { compose.runOnIdle { counter++ } }
        compose.waitForIdle()
        assertEquals(listOf(HOME), logged)
    }

    @Test
    fun waitsUntilTheScreenIsReallyVisible() {
        owner.currentState = Lifecycle.State.CREATED
        show { TrackScreen(HOME, this) { logged += it } }
        compose.waitForIdle()
        assertEquals(emptyList<String>(), logged)
        moveTo(Lifecycle.State.RESUMED)
        assertEquals(listOf(HOME), logged)
    }

    @Test
    fun logsTheNewPageWhenItChanges() {
        var page by mutableStateOf(HOME)
        show { TrackScreen(page, this) { logged += it } }
        compose.runOnIdle { page = CART }
        compose.waitForIdle()
        assertEquals(listOf(HOME, CART), logged)
    }

    @Test
    fun stopsLoggingOnceTheScreenIsGone() {
        var shown by mutableStateOf(true)
        show { if (shown) TrackScreen(HOME, this) { logged += it } }
        compose.runOnIdle { shown = false }
        moveTo(Lifecycle.State.CREATED)
        moveTo(Lifecycle.State.RESUMED)
        assertEquals(listOf(HOME), logged)
    }

    private companion object {
        const val HOME = "30269ec7-8441-4493-ad65-8b3a7976c8c4"
        const val CART = "cf9dad24-712e-4c68-9825-06c61fe49345"
    }
}
