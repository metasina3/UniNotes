package com.sina.uninotes

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val container get() = (rule.activity.application as UniNotesApp).container

    @Before fun cleanLibrary() = runBlocking {
        container.database.withTransaction {
            container.database.photoDao().deleteAll()
            container.database.noteDao().deleteAll()
            container.database.subjectDao().deleteAll()
        }
    }

    private fun openSubject(name: String = "Data Structures") {
        runBlocking { container.subjectRepository.createSubject(name, 0xFFF5A35C).getOrThrow() }
        rule.waitUntil(10000) { rule.onAllNodesWithText(name).fetchSemanticsNodes().isNotEmpty() }
        capture("home")
        rule.onNodeWithText(name).performClick()
        rule.onNodeWithText("Camera").assertIsDisplayed()
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val context = rule.activity.applicationContext
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun subjectActionsStayAboveSystemNavigation() {
        openSubject()
        val actionBounds = rule.onNodeWithTag("subjectActions").fetchSemanticsNode().boundsInWindow
        rule.runOnIdle {
            val decor = rule.activity.window.decorView
            val bottom = ViewCompat.getRootWindowInsets(decor)!!
                .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            // The tag wraps the whole inset-aware action area. Check the actual button bounds below.
            assertThat(actionBounds.bottom).isAtMost(decor.height.toFloat())
        }
        for (label in listOf("Camera", "Write note")) {
            val bounds = rule.onNodeWithText(label).fetchSemanticsNode().boundsInWindow
            rule.runOnIdle {
                val decor = rule.activity.window.decorView
                val bottom = ViewCompat.getRootWindowInsets(decor)!!
                    .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                assertThat(bounds.bottom).isAtMost((decor.height - bottom).toFloat())
            }
        }
        capture("subject-photos")
        rule.onNodeWithText("Notes", useUnmergedTree = true).performClick()
        rule.onNodeWithText("No notes yet").assertIsDisplayed()
        capture("subject-notes")
    }

    @Test fun mixedPersianEnglishSurvivesImmediateBackAndReopen() {
        openSubject("ساختمان داده")
        rule.onNodeWithText("Write note").performClick()
        val body = rule.onAllNodes(hasSetTextAction())[1]
        rule.waitUntil(10000) {
            runBlocking { container.database.noteDao().getAll().isNotEmpty() }
        }
        rule.waitForIdle()
        val sample = "امروز دربارهٔ Linked List و تفاوتش با Array صحبت کردیم.\nAn English paragraph.\nاین گره به Node بعدی اشاره می‌کند."
        body.performTextInput(sample)
        val layouts = mutableListOf<TextLayoutResult>()
        body.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertThat(layouts.first().getParagraphDirection(0)).isEqualTo(ResolvedTextDirection.Rtl)
        val englishOffset = sample.indexOf("An English")
        assertThat(layouts.first().getParagraphDirection(englishOffset)).isEqualTo(ResolvedTextDirection.Ltr)
        // Leave before debounce expires: this exercises persistence after navigation removes the editor.
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(10000) {
            runBlocking { container.database.noteDao().getAll().singleOrNull()?.body == sample }
        }
        rule.onNodeWithText("Write note").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodes(hasSetTextAction() and hasText(sample)).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(runBlocking { container.database.noteDao().getAll().size }).isEqualTo(1)
        capture("mixed-language-editor")
    }
}
