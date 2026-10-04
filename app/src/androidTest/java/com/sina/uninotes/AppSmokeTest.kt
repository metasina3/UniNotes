package com.sina.uninotes

import android.graphics.Bitmap
import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.sina.uninotes.data.local.db.PhotoStatus
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class AppSmokeTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @get:Rule val cameraPermission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)
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
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(100, 5000)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val context = rule.activity.applicationContext
        val dir = File(context.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun subjectActionsStayAboveSystemNavigation() {
        openSubject()
        rule.runOnIdle {
            val controller = WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
            assertThat(controller.isAppearanceLightStatusBars).isFalse()
            assertThat(controller.isAppearanceLightNavigationBars).isFalse()
        }
        val actionBounds = rule.onNodeWithTag("subjectActions").fetchSemanticsNode().boundsInWindow
        rule.runOnIdle {
            val decor = rule.activity.window.decorView
            val bottom = ViewCompat.getRootWindowInsets(decor)!!
                .getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            // The tag wraps the whole inset-aware action area. Check the actual button bounds below.
            assertThat(actionBounds.bottom).isAtMost(decor.height.toFloat())
        }
        for (label in listOf("Camera", "Note")) {
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
        rule.onNodeWithText("Note").performClick()
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
        rule.onNodeWithText("Note").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodes(hasSetTextAction() and hasText(sample)).fetchSemanticsNodes().isNotEmpty()
        }
        assertThat(runBlocking { container.database.noteDao().getAll().size }).isEqualTo(1)
        capture("mixed-language-editor")
    }

    @Test fun cameraCaptureSavesOriginalAndThumbnailToSelectedSubject() {
        openSubject()
        val subject = runBlocking { container.database.subjectDao().getAll().single() }
        rule.onNodeWithText("Camera").performClick()
        rule.waitUntil(60000) {
            rule.onAllNodes(hasContentDescription("Take photo") and isEnabled())
                .fetchSemanticsNodes().isNotEmpty()
        }
        capture("camera")
        rule.onNodeWithContentDescription("Take photo").performClick()
        rule.waitUntil(30000) {
            runBlocking { container.photoRepository.getReadyForSubject(subject.id).size == 1 }
        }
        val photo = runBlocking { container.photoRepository.getReadyForSubject(subject.id).single() }
        assertThat(photo.subjectId).isEqualTo(subject.id)
        assertThat(photo.status).isEqualTo(PhotoStatus.READY)
        assertThat(photo.width).isGreaterThan(0)
        assertThat(photo.height).isGreaterThan(0)
        val original = container.photoRepository.resolveFile(photo.relativePath)
        assertThat(original.exists()).isTrue()
        assertThat(original.length()).isGreaterThan(0L)
        assertThat(original.canonicalPath.startsWith(rule.activity.filesDir.canonicalPath + "/")).isTrue()
        assertThat(photo.thumbnailRelativePath).isNotNull()
        assertThat(container.photoRepository.resolveFile(photo.thumbnailRelativePath!!).exists()).isTrue()
        rule.onNodeWithContentDescription("Back").performClick()
        rule.waitUntil(10000) {
            rule.onAllNodesWithContentDescription("Photo taken ${photo.localDate}")
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithContentDescription("Photo taken ${photo.localDate}").assertIsDisplayed()
        capture("gallery-with-photo")
        rule.onNodeWithContentDescription("Photo taken ${photo.localDate}").performClick()
        capture("photo-viewer")
    }
}
