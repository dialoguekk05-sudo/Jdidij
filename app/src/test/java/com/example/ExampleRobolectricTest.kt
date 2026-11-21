package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.ProjectFileEntity
import com.example.system.FileWorkspaceManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("JARVIS AI", appName)
    }

    @Test
    fun `file workspace validator detects malformed kotlin and xml`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = FileWorkspaceManager(context)

        val validFiles = listOf(
            ProjectFileEntity(
                projectId = 1L,
                relativePath = "MainActivity.kt",
                language = "kotlin",
                content = "fun main() { println(\"Hello\") }"
            ),
            ProjectFileEntity(
                projectId = 1L,
                relativePath = "AndroidManifest.xml",
                language = "xml",
                content = "<manifest><application /></manifest>"
            )
        )
        assertTrue(manager.validateProjectFiles(validFiles).isEmpty())

        val brokenFiles = listOf(
            ProjectFileEntity(
                projectId = 1L,
                relativePath = "Broken.kt",
                language = "kotlin",
                content = "fun broken() { if (true) { "
            )
        )
        val issues = manager.validateProjectFiles(brokenFiles)
        assertEquals(1, issues.size)
        assertEquals("Broken.kt", issues.first().filePath)
    }

    @Test
    fun `viewmodels instantiate cleanly with AndroidViewModelFactory and MainActivity launches`() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val factory = androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(app)
        val assistantVm = factory.create(com.example.ui.state.JarvisAssistantViewModel::class.java)
        val builderVm = factory.create(com.example.ui.state.AppBuilderViewModel::class.java)
        assertTrue(assistantVm.telemetry.value.batteryPercent in 0..100)
        assertTrue(builderVm.projects.value.isEmpty())

        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java).setup().get()
        assertTrue(!activity.isFinishing)
    }
}
