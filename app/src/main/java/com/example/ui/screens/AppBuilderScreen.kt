package com.example.ui.screens

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.local.GeneratedProjectEntity
import com.example.data.local.ProjectFileEntity
import com.example.system.ValidationIssue
import com.example.ui.state.BuilderUiState
import com.example.ui.theme.JarvisBorderGlow
import com.example.ui.theme.JarvisCyan
import com.example.ui.theme.JarvisError
import com.example.ui.theme.JarvisObsidian
import com.example.ui.theme.JarvisPanel
import com.example.ui.theme.JarvisPanelElevated
import com.example.ui.theme.JarvisSuccess
import com.example.ui.theme.JarvisTeal
import com.example.ui.theme.JarvisTextPrimary
import com.example.ui.theme.JarvisTextSecondary
import com.example.ui.theme.JarvisWarning
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun AppBuilderScreen(
    projects: List<GeneratedProjectEntity>,
    selectedProjectId: Long?,
    files: List<ProjectFileEntity>,
    selectedFileId: Long?,
    builderState: BuilderUiState,
    validationIssues: List<ValidationIssue>,
    initialPromptPair: Pair<String, String>?,
    onGenerateProject: (prompt: String, type: String) -> Unit,
    onSelectProject: (Long) -> Unit,
    onSelectFile: (Long) -> Unit,
    onSaveFileContent: (ProjectFileEntity, String) -> Unit,
    onAnalyzeAndFix: () -> Unit,
    onImportExternalFile: (android.net.Uri) -> Unit,
    onExportProjectZip: (android.net.Uri) -> Unit,
    onShareProjectZip: () -> Unit,
    onDeleteProject: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedType by remember { mutableStateOf("ANDROID_KOTLIN") }
    var promptText by remember { mutableStateOf("") }
    var showWebPreview by remember { mutableStateOf(false) }

    LaunchedEffect(initialPromptPair) {
        if (initialPromptPair != null) {
            selectedType = initialPromptPair.first
            promptText = initialPromptPair.second
        }
    }

    val activeProject = projects.firstOrNull { it.id == selectedProjectId }
    val activeFile = files.firstOrNull { it.id == selectedFileId } ?: files.firstOrNull()

    var editableCode by remember(activeFile?.id, activeFile?.content) {
        mutableStateOf(activeFile?.content.orEmpty())
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportExternalFile(uri)
    }

    val exportZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onExportProjectZip(uri)
    }

    if (showWebPreview && activeProject != null) {
        WebsitePreviewDialog(
            files = files,
            onDismiss = { showWebPreview = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(JarvisObsidian),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Futuristic Header Card with HUD Artwork
            Card(
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, JarvisBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.height(104.dp)) {
                    Image(
                        painter = painterResource(id = R.drawable.img_jarvis_hud_banner_1795255306047),
                        contentDescription = "JARVIS Coding Workspace Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        JarvisObsidian.copy(alpha = 0.92f),
                                        JarvisObsidian.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "AI APP & WEB BUILDER",
                            style = MaterialTheme.typography.titleLarge,
                            color = JarvisCyan
                        )
                        Text(
                            text = "Generate Kotlin/XML Android Studio APK projects, HTML/CSS/JS/Python websites, detect & fix bugs, and export ZIP bundles.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisTextPrimary
                        )
                    }
                }
            }

            // Generator Input Card
            Card(
                colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                border = BorderStroke(1.dp, JarvisBorderGlow),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedType == "ANDROID_KOTLIN",
                            onClick = { selectedType = "ANDROID_KOTLIN" },
                            label = { Text("Android Kotlin + XML") },
                            leadingIcon = { Icon(Icons.Default.Android, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                selectedLabelColor = JarvisCyan
                            ),
                            modifier = Modifier.testTag("builder_mode_android")
                        )
                        FilterChip(
                            selected = selectedType == "WEBSITE_BUNDLE",
                            onClick = { selectedType = "WEBSITE_BUNDLE" },
                            label = { Text("Web + Python") },
                            leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = JarvisCyan.copy(alpha = 0.2f),
                                selectedLabelColor = JarvisCyan
                            ),
                            modifier = Modifier.testTag("builder_mode_website")
                        )
                    }

                    OutlinedTextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        label = { Text("Describe the Android App or Website") },
                        placeholder = { Text("e.g. Build a Hindi Expense Tracker Kotlin app with dark UI") },
                        minLines = 2,
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = JarvisCyan,
                            unfocusedBorderColor = JarvisBorderGlow
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("builder_prompt_input")
                    )

                    // Quick Templates
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                selectedType = "ANDROID_KOTLIN"
                                promptText = "Build a Scientific Calculator Android app in Kotlin and Jetpack Compose with history"
                            },
                            label = { Text("Calculator App") }
                        )
                        AssistChip(
                            onClick = {
                                selectedType = "ANDROID_KOTLIN"
                                promptText = "Build a Daily Habit Tracker Android app in Kotlin with dark Material 3 UI"
                            },
                            label = { Text("Habit Tracker") }
                        )
                        AssistChip(
                            onClick = {
                                selectedType = "WEBSITE_BUNDLE"
                                promptText = "Create a futuristic AI Portfolio website using HTML, CSS, JavaScript, and Python Flask backend"
                            },
                            label = { Text("AI Portfolio Web") }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onGenerateProject(promptText, selectedType) },
                            enabled = builderState !is BuilderUiState.Generating && promptText.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = JarvisCyan),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("builder_generate_button")
                        ) {
                            if (builderState is BuilderUiState.Generating) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = JarvisObsidian,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Generating...")
                            } else {
                                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Generate Project")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                importLauncher.launch(arrayOf("text/*", "application/json", "application/xml", "*/*"))
                            },
                            modifier = Modifier.testTag("builder_import_file_button")
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import")
                        }
                    }
                }
            }

            // Status / Feedback Banner
            when (builderState) {
                is BuilderUiState.Success -> {
                    Surface(
                        color = JarvisSuccess.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, JarvisSuccess.copy(alpha = 0.5f)),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = builderState.message,
                            color = JarvisSuccess,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                is BuilderUiState.Error -> {
                    Surface(
                        color = JarvisError.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, JarvisError),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = builderState.message,
                            color = JarvisError,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
                else -> {}
            }

            // Existing Projects Selector
            if (projects.isNotEmpty()) {
                Text(
                    text = "GENERATED WORKSPACES (${projects.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisTeal
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    projects.forEach { proj ->
                        FilterChip(
                            selected = proj.id == activeProject?.id,
                            onClick = { onSelectProject(proj.id) },
                            label = { Text(proj.name) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { onDeleteProject(proj.id) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete project",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // Active Project Workspace & Code Editor
            if (activeProject != null && files.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = JarvisPanel),
                    border = BorderStroke(1.dp, JarvisBorderGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeProject.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = JarvisCyan
                                )
                                Text(
                                    text = "${files.size} files • ${activeProject.packageName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = JarvisTextSecondary
                                )
                            }
                        }

                        // Action Toolbar: Fix Bugs, Preview Web, Export ZIP, Share
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onAnalyzeAndFix,
                                enabled = builderState !is BuilderUiState.Fixing,
                                modifier = Modifier.testTag("builder_fix_errors_button")
                            ) {
                                Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (builderState is BuilderUiState.Fixing) "Fixing..." else "Detect & Fix Errors")
                            }

                            if (files.any { it.relativePath.endsWith(".html", ignoreCase = true) }) {
                                OutlinedButton(
                                    onClick = { showWebPreview = true },
                                    modifier = Modifier.testTag("builder_preview_web_button")
                                ) {
                                    Icon(Icons.Default.Preview, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Live Preview")
                                }
                            }

                            Button(
                                onClick = {
                                    val safeFileName = activeProject.name
                                        .lowercase()
                                        .replace(Regex("[^a-z0-9]+"), "_")
                                        .trim('_')
                                        .ifBlank { "jarvis_project" }
                                    exportZipLauncher.launch("${safeFileName}_apk_project.zip")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = JarvisTeal),
                                modifier = Modifier.testTag("builder_export_zip_button")
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Export ZIP + APK Script")
                            }

                            OutlinedButton(
                                onClick = onShareProjectZip,
                                modifier = Modifier.testTag("builder_share_zip_button")
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        }

                        // Validation Diagnostic Pill
                        Surface(
                            color = if (validationIssues.isEmpty()) {
                                JarvisSuccess.copy(alpha = 0.1f)
                            } else {
                                JarvisWarning.copy(alpha = 0.12f)
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (validationIssues.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (validationIssues.isEmpty()) JarvisSuccess else JarvisWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (validationIssues.isEmpty()) {
                                        "Pre-flight check passed: XML, Kotlin & Gradle structure verified."
                                    } else {
                                        validationIssues.joinToString("\n") { "${it.filePath}: ${it.message}" }
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (validationIssues.isEmpty()) JarvisSuccess else JarvisWarning
                                )
                            }
                        }

                        // Project File Tabs
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            files.forEach { file ->
                                val shortName = file.relativePath.substringAfterLast('/')
                                FilterChip(
                                    selected = file.id == activeFile?.id,
                                    onClick = { onSelectFile(file.id) },
                                    label = { Text(shortName) },
                                    leadingIcon = { Icon(Icons.Default.Code, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                )
                            }
                        }

                        // Editable Code Workspace
                        if (activeFile != null) {
                            Text(
                                text = activeFile.relativePath,
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisCyan
                            )
                            OutlinedTextField(
                                value = editableCode,
                                onValueChange = { editableCode = it },
                                textStyle = TextStyle(
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = JarvisTextPrimary
                                ),
                                minLines = 10,
                                maxLines = 18,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = JarvisObsidian,
                                    unfocusedContainerColor = JarvisObsidian,
                                    focusedBorderColor = JarvisCyan,
                                    unfocusedBorderColor = JarvisBorderGlow
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("builder_code_editor")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                Button(
                                    onClick = { onSaveFileContent(activeFile, editableCode) },
                                    colors = ButtonDefaults.buttonColors(containerColor = JarvisPanelElevated),
                                    modifier = Modifier.testTag("builder_save_file_button")
                                ) {
                                    Text("Save Code Changes", color = JarvisCyan)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebsitePreviewDialog(
    files: List<ProjectFileEntity>,
    onDismiss: () -> Unit
) {
    val htmlFile = files.firstOrNull { it.relativePath.endsWith(".html", ignoreCase = true) }
    val cssContent = files.firstOrNull { it.relativePath.endsWith(".css", ignoreCase = true) }?.content.orEmpty()
    val jsContent = files.firstOrNull { it.relativePath.endsWith(".js", ignoreCase = true) }?.content.orEmpty()

    val combinedHtml = remember(htmlFile, cssContent, jsContent) {
        val rawHtml = htmlFile?.content ?: "<html><body><h3>No HTML file found.</h3></body></html>"
        rawHtml
            .replace("</head>", "<style>$cssContent</style></head>", ignoreCase = true)
            .replace("</body>", "<script>$jsContent</script></body>", ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisPanel,
        title = { Text("Live Website Preview", color = JarvisCyan) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
            ) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            loadDataWithBaseURL(null, combinedHtml, "text/html", "UTF-8", null)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = JarvisCyan)
            }
        }
    )
}
