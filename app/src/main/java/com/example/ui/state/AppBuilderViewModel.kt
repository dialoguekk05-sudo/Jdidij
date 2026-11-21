package com.example.ui.state

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.GeneratedFileSpec
import com.example.data.ai.JarvisAiRepository
import com.example.data.local.GeneratedProjectEntity
import com.example.data.local.JarvisDatabase
import com.example.data.local.ProjectFileEntity
import com.example.system.FileWorkspaceManager
import com.example.system.ValidationIssue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BuilderUiState {
    data object Idle : BuilderUiState
    data object Generating : BuilderUiState
    data object Fixing : BuilderUiState
    data class Success(val message: String) : BuilderUiState
    data class Error(val message: String) : BuilderUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppBuilderViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = JarvisDatabase.getInstance(application).projectBuilderDao()
    private val aiRepo = JarvisAiRepository()
    private val fileManager = FileWorkspaceManager(application)

    val projects: StateFlow<List<GeneratedProjectEntity>> = dao.observeAllProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedProjectId = MutableStateFlow<Long?>(null)
    val selectedProjectId: StateFlow<Long?> = _selectedProjectId.asStateFlow()

    val selectedProjectFiles: StateFlow<List<ProjectFileEntity>> = _selectedProjectId
        .flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else dao.observeFilesForProject(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFileId = MutableStateFlow<Long?>(null)
    val selectedFileId: StateFlow<Long?> = _selectedFileId.asStateFlow()

    private val _builderState = MutableStateFlow<BuilderUiState>(BuilderUiState.Idle)
    val builderState: StateFlow<BuilderUiState> = _builderState.asStateFlow()

    private val _validationIssues = MutableStateFlow<List<ValidationIssue>>(emptyList())
    val validationIssues: StateFlow<List<ValidationIssue>> = _validationIssues.asStateFlow()

    private val _shareIntentEvent = MutableStateFlow<Intent?>(null)
    val shareIntentEvent: StateFlow<Intent?> = _shareIntentEvent.asStateFlow()

    fun selectProject(projectId: Long) {
        _selectedProjectId.value = projectId
        _selectedFileId.value = null
        _validationIssues.value = emptyList()
    }

    fun selectFile(fileId: Long) {
        _selectedFileId.value = fileId
    }

    fun clearBuilderFeedback() {
        _builderState.value = BuilderUiState.Idle
    }

    fun consumeShareIntent(): Intent? {
        val intent = _shareIntentEvent.value
        _shareIntentEvent.value = null
        return intent
    }

    fun generateNewProject(prompt: String, projectType: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isBlank()) return

        viewModelScope.launch {
            _builderState.value = BuilderUiState.Generating
            _validationIssues.value = emptyList()
            try {
                val bundle = aiRepo.generateAppBuilderProject(cleanPrompt, projectType)
                val projectId = dao.insertProject(
                    GeneratedProjectEntity(
                        name = bundle.projectName,
                        projectType = projectType,
                        prompt = cleanPrompt,
                        packageName = bundle.packageName,
                        summary = bundle.summary
                    )
                )
                val entities = bundle.files.map { spec ->
                    ProjectFileEntity(
                        projectId = projectId,
                        relativePath = spec.relativePath,
                        language = spec.language,
                        content = spec.content
                    )
                }
                dao.insertFiles(entities)
                val persistedProject = GeneratedProjectEntity(
                    id = projectId,
                    name = bundle.projectName,
                    projectType = projectType,
                    prompt = cleanPrompt,
                    packageName = bundle.packageName,
                    summary = bundle.summary
                )
                val persistedFiles = dao.getFilesForProjectOnce(projectId)
                fileManager.syncProjectToDisk(persistedProject, persistedFiles)

                _selectedProjectId.value = projectId
                _selectedFileId.value = persistedFiles.firstOrNull()?.id
                _validationIssues.value = fileManager.validateProjectFiles(persistedFiles)
                _builderState.value = BuilderUiState.Success(bundle.summary)
            } catch (e: TimeoutCancellationException) {
                Log.e("AppBuilderVM", "Generation timed out", e)
                _builderState.value = BuilderUiState.Error("Project generation timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AppBuilderVM", "Project generation failed", e)
                _builderState.value = BuilderUiState.Error(
                    e.localizedMessage ?: "Failed to generate project."
                )
            }
        }
    }

    fun updateFileContent(file: ProjectFileEntity, newContent: String) {
        viewModelScope.launch {
            val updated = file.copy(content = newContent)
            dao.updateFile(updated)
            val projectId = _selectedProjectId.value ?: return@launch
            val project = projects.value.firstOrNull { it.id == projectId } ?: return@launch
            val allFiles = dao.getFilesForProjectOnce(projectId)
            fileManager.syncProjectToDisk(project, allFiles)
            _validationIssues.value = fileManager.validateProjectFiles(allFiles)
        }
    }

    fun runPreflightAndAiFix() {
        val projectId = _selectedProjectId.value ?: return
        val project = projects.value.firstOrNull { it.id == projectId } ?: return

        viewModelScope.launch {
            _builderState.value = BuilderUiState.Fixing
            try {
                val files = dao.getFilesForProjectOnce(projectId)
                val localIssues = fileManager.validateProjectFiles(files)
                _validationIssues.value = localIssues

                val issueText = localIssues.joinToString("\n") { "${it.filePath}: ${it.message}" }
                val specs = files.map {
                    GeneratedFileSpec(it.relativePath, it.language, it.content)
                }
                val fixBundle = aiRepo.analyzeAndFixProject(specs, issueText)

                val existingMap = files.associateBy { it.relativePath }.toMutableMap()
                fixBundle.fixedFiles.forEach { fixed ->
                    val existing = existingMap[fixed.relativePath]
                    if (existing != null) {
                        dao.updateFile(
                            existing.copy(
                                content = fixed.content,
                                hasErrors = false,
                                errorSummary = ""
                            )
                        )
                    } else {
                        dao.insertFiles(
                            listOf(
                                ProjectFileEntity(
                                    projectId = projectId,
                                    relativePath = fixed.relativePath,
                                    language = fixed.language,
                                    content = fixed.content
                                )
                            )
                        )
                    }
                }
                val refreshed = dao.getFilesForProjectOnce(projectId)
                fileManager.syncProjectToDisk(project, refreshed)
                _validationIssues.value = fileManager.validateProjectFiles(refreshed)
                _builderState.value = BuilderUiState.Success(fixBundle.summary)
            } catch (e: TimeoutCancellationException) {
                _builderState.value = BuilderUiState.Error("Code analyzer timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("AppBuilderVM", "Code analysis failed", e)
                _builderState.value = BuilderUiState.Error(
                    e.localizedMessage ?: "Failed to analyze/fix project."
                )
            }
        }
    }

    fun importFileIntoWorkspace(uri: Uri) {
        viewModelScope.launch {
            fileManager.readExternalTextFile(uri).onSuccess { imported ->
                val projectId = _selectedProjectId.value ?: run {
                    val id = dao.insertProject(
                        GeneratedProjectEntity(
                            name = "Imported Workspace",
                            projectType = "ANDROID_KOTLIN",
                            prompt = "Imported external source files",
                            packageName = "com.jarvis.imported",
                            summary = "Files imported with user permission via Storage Access Framework."
                        )
                    )
                    _selectedProjectId.value = id
                    id
                }
                val ext = imported.displayName.substringAfterLast('.', "txt").lowercase()
                dao.insertFiles(
                    listOf(
                        ProjectFileEntity(
                            projectId = projectId,
                            relativePath = imported.displayName,
                            language = ext,
                            content = imported.content
                        )
                    )
                )
                val refreshed = dao.getFilesForProjectOnce(projectId)
                _selectedFileId.value = refreshed.lastOrNull { it.relativePath == imported.displayName }?.id
                _validationIssues.value = fileManager.validateProjectFiles(refreshed)
                _builderState.value = BuilderUiState.Success("Imported ${imported.displayName} safely.")
            }.onFailure { err ->
                _builderState.value = BuilderUiState.Error(err.message ?: "Import failed.")
            }
        }
    }

    fun exportCurrentProjectToUri(uri: Uri) {
        val projectId = _selectedProjectId.value ?: return
        val project = projects.value.firstOrNull { it.id == projectId } ?: return
        viewModelScope.launch {
            val files = dao.getFilesForProjectOnce(projectId)
            fileManager.exportProjectAsZipToUri(project, files, uri)
                .onSuccess { count ->
                    _builderState.value = BuilderUiState.Success(
                        "Exported $count files as ZIP! Open in Android Studio or run build_debug_apk.sh to compile APK."
                    )
                }
                .onFailure { err ->
                    _builderState.value = BuilderUiState.Error(err.message ?: "Export failed.")
                }
        }
    }

    fun shareCurrentProjectZip() {
        val projectId = _selectedProjectId.value ?: return
        val project = projects.value.firstOrNull { it.id == projectId } ?: return
        viewModelScope.launch {
            val files = dao.getFilesForProjectOnce(projectId)
            fileManager.createShareableZipIntent(project, files)
                .onSuccess { intent -> _shareIntentEvent.value = intent }
                .onFailure { err ->
                    _builderState.value = BuilderUiState.Error(err.message ?: "Share failed.")
                }
        }
    }

    fun deleteProject(projectId: Long) {
        viewModelScope.launch {
            dao.deleteProjectById(projectId)
            if (_selectedProjectId.value == projectId) {
                _selectedProjectId.value = null
                _selectedFileId.value = null
            }
        }
    }
}
