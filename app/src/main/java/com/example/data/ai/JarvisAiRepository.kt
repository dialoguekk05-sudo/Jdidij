package com.example.data.ai

import android.util.Log
import com.example.data.local.ChatMessageEntity
import com.example.data.local.MemoryFactEntity
import com.example.data.preferences.JarvisLanguageMode
import com.example.data.remote.WebSource
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "JarvisAiRepository"

enum class IntentActionType {
    CHAT,
    OPEN_APP,
    MAKE_CALL,
    SEND_SMS,
    WEB_SEARCH,
    SYSTEM_NAV,
    OPEN_BUILDER
}

data class ClassifiedIntent(
    val actionType: IntentActionType = IntentActionType.CHAT,
    val targetApp: String = "",
    val contactOrNumber: String = "",
    val smsBody: String = "",
    val searchQuery: String = "",
    val navAction: String = "",
    val builderMode: String = "",
    val builderPrompt: String = "",
    val memoryFact: String = ""
)

data class GeneratedFileSpec(
    val relativePath: String,
    val language: String,
    val content: String
)

data class GeneratedProjectBundle(
    val projectName: String,
    val packageName: String,
    val summary: String,
    val files: List<GeneratedFileSpec>
)

data class CodeFixBundle(
    val summary: String,
    val fixedFiles: List<GeneratedFileSpec>
)

class JarvisAiRepository {

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    You are JARVIS AI, an intelligent, calm, futuristic personal Android assistant.
                    Language rules:
                    - Reply naturally in Hindi (Devanagari) when the user speaks/writes in Hindi or Hinglish, or in clear English when asked in English, matching the user's preferred mode.
                    - Keep spoken/chat replies concise, practical, and accurate unless the user asks for code or a detailed explanation.
                    - Never invent private user data. When web search snippets are provided, cite them cleanly.
                    """.trimIndent()
                )
            }
        )
    }

    private val intentSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "actionType" to Schema.enumeration(
                    values = listOf(
                        "CHAT",
                        "OPEN_APP",
                        "MAKE_CALL",
                        "SEND_SMS",
                        "WEB_SEARCH",
                        "SYSTEM_NAV",
                        "OPEN_BUILDER"
                    ),
                    description = "Primary intent of the user command"
                ),
                "targetApp" to Schema.string(description = "App name to open if actionType is OPEN_APP"),
                "contactOrNumber" to Schema.string(description = "Phone number or contact name if MAKE_CALL or SEND_SMS"),
                "smsBody" to Schema.string(description = "SMS message text if SEND_SMS"),
                "searchQuery" to Schema.string(description = "Concise web search query if WEB_SEARCH"),
                "navAction" to Schema.enumeration(
                    values = listOf("NONE", "BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS"),
                    description = "System navigation action if SYSTEM_NAV"
                ),
                "builderMode" to Schema.enumeration(
                    values = listOf("NONE", "ANDROID_KOTLIN", "WEBSITE_BUNDLE"),
                    description = "App builder mode if OPEN_BUILDER"
                ),
                "builderPrompt" to Schema.string(description = "App or website prompt if OPEN_BUILDER"),
                "memoryFact" to Schema.string(description = "Short personal preference or fact to remember if user explicitly asked to remember or shared a key preference, else empty")
            )
        )
    }

    private val intentClassifierModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.5-flash-lite",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = intentSchema
            }
        )
    }

    private val projectSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "projectName" to Schema.string(description = "Short clean project name"),
                "packageName" to Schema.string(description = "Valid Android package name like com.jarvis.generated.app"),
                "summary" to Schema.string(description = "Concise explanation of the generated project in simple Hindi and English"),
                "files" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "relativePath" to Schema.string(description = "Relative file path e.g. app/src/main/java/com/jarvis/app/MainActivity.kt or index.html"),
                            "language" to Schema.string(description = "kotlin, xml, gradle, html, css, javascript, python, or markdown"),
                            "content" to Schema.string(description = "Complete, compilable, bug-free file source code")
                        )
                    ),
                    description = "Complete list of project files"
                )
            )
        )
    }

    private val projectGeneratorModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = projectSchema
            }
        )
    }

    private val codeFixSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "summary" to Schema.string(description = "Summary of detected bugs and fixes applied in simple Hindi/English"),
                "fixedFiles" to Schema.array(
                    items = Schema.obj(
                        properties = mapOf(
                            "relativePath" to Schema.string(description = "Exact relative path of the fixed file"),
                            "language" to Schema.string(description = "File language"),
                            "content" to Schema.string(description = "Complete corrected file content")
                        )
                    )
                )
            )
        )
    }

    private val codeFixModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = codeFixSchema
            }
        )
    }

    suspend fun classifyUserIntent(
        userText: String,
        allowAutoMemory: Boolean
    ): ClassifiedIntent {
        return try {
            val prompt = """
                Classify the user command below into a structured JSON intent.
                Examples:
                - "YouTube खोलो" / "Open WhatsApp" -> OPEN_APP, targetApp="YouTube"
                - "9876543210 पर कॉल करो" / "Call Mom" -> MAKE_CALL, contactOrNumber="..."
                - "Rahul को मैसेज भेजो I am coming" -> SEND_SMS, contactOrNumber="Rahul", smsBody="I am coming"
                - "आज का मौसम / latest ISRO news / search internet for quantum computing" -> WEB_SEARCH
                - "गो बैक / home screen पर जाओ / open notifications" -> SYSTEM_NAV
                - "एक calculator Android app बनाओ" / "Build a portfolio website" -> OPEN_BUILDER
                - Everything else (questions, coding help, greetings, explanations) -> CHAT
                ${if (!allowAutoMemory) "Leave memoryFact empty." else "Only populate memoryFact if the user shares a durable preference or explicitly says remember/याद रखो."}

                User command: "$userText"
            """.trimIndent()

            val response = withTimeout(30_000L) {
                intentClassifierModel.generateContent(prompt)
            }
            val raw = response.text ?: return ClassifiedIntent()
            val json = JSONObject(raw)
            val action = runCatching {
                IntentActionType.valueOf(json.optString("actionType", "CHAT"))
            }.getOrDefault(IntentActionType.CHAT)

            ClassifiedIntent(
                actionType = action,
                targetApp = json.optString("targetApp", ""),
                contactOrNumber = json.optString("contactOrNumber", ""),
                smsBody = json.optString("smsBody", ""),
                searchQuery = json.optString("searchQuery", ""),
                navAction = json.optString("navAction", "NONE"),
                builderMode = json.optString("builderMode", "NONE"),
                builderPrompt = json.optString("builderPrompt", ""),
                memoryFact = if (allowAutoMemory) json.optString("memoryFact", "") else ""
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Intent classification failed, falling back to CHAT", e)
            ClassifiedIntent(actionType = IntentActionType.CHAT)
        }
    }

    fun streamJarvisReply(
        userMessage: String,
        languageMode: JarvisLanguageMode,
        recentHistory: List<ChatMessageEntity>,
        memoryFacts: List<MemoryFactEntity>,
        webSources: List<WebSource>
    ): Flow<String> = flow {
        val historyTurns: List<Content> = recentHistory
            .takeLast(8)
            .filter { it.role == "user" || it.role == "jarvis" }
            .map { msg ->
                val geminiRole = if (msg.role == "user") "user" else "model"
                content(geminiRole) { text(msg.content) }
            }

        val contextHeader = buildString {
            appendLine("Preferred response language mode: ${languageMode.label}.")
            if (languageMode == JarvisLanguageMode.HINDI) {
                appendLine("Respond in natural conversational Hindi (Devanagari).")
            } else if (languageMode == JarvisLanguageMode.ENGLISH) {
                appendLine("Respond in clear English.")
            } else {
                appendLine("Match the user's language (Hindi or English) naturally.")
            }
            if (memoryFacts.isNotEmpty()) {
                appendLine("User Memory Facts:")
                memoryFacts.take(10).forEach { appendLine("- ${it.fact}") }
            }
            if (webSources.isNotEmpty()) {
                appendLine("Live Internet Search Snippets:")
                webSources.forEachIndexed { idx, src ->
                    appendLine("[${idx + 1}] ${src.title}: ${src.snippet} (${src.url})")
                }
                appendLine("Use these live search snippets to answer accurately.")
            }
        }

        val chat = chatModel.startChat(history = historyTurns)
        val fullPrompt = "$contextHeader\nUser: $userMessage"

        withTimeout(60_000L) {
            val accumulated = StringBuilder()
            chat.sendMessageStream(fullPrompt).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    emit(accumulated.toString())
                }
            }
            if (accumulated.isBlank()) {
                throw IllegalStateException("JARVIS AI returned an empty response.")
            }
        }
    }

    suspend fun generateAppBuilderProject(
        userPrompt: String,
        projectType: String
    ): GeneratedProjectBundle {
        val specPrompt = if (projectType == "WEBSITE_BUNDLE") {
            """
            Build a complete, responsive, production-quality Website bundle for: "$userPrompt".
            Generate these files in the JSON response:
            1. index.html (complete semantic HTML5 referencing styles.css and app.js, plus inline fallback styling/behavior so preview works cleanly)
            2. styles.css (modern dark/clean responsive CSS)
            3. app.js (real interactive JavaScript logic)
            4. server.py (clean Python Flask or FastAPI backend serving the site and API endpoint)
            5. README.md (setup & run instructions in simple Hindi and English)
            Ensure valid syntax and zero placeholder TODOs.
            """.trimIndent()
        } else {
            """
            Build a complete, compilable Android Studio Kotlin + Jetpack Compose + XML project for: "$userPrompt".
            Generate these files in the JSON response:
            1. settings.gradle.kts
            2. build.gradle.kts (project level)
            3. app/build.gradle.kts (Android application with Compose & Material 3)
            4. app/src/main/AndroidManifest.xml
            5. app/src/main/res/values/strings.xml
            6. app/src/main/res/layout/activity_main_preview.xml (clean XML layout reference)
            7. app/src/main/java/com/jarvis/generated/MainActivity.kt (complete working Kotlin + Jetpack Compose implementation)
            8. README.md (step-by-step Android Studio & `./gradlew assembleDebug` APK build instructions in simple Hindi)
            Ensure all Kotlin braces, imports, and XML tags are 100% valid.
            """.trimIndent()
        }

        val response = withTimeout(60_000L) {
            projectGeneratorModel.generateContent(specPrompt)
        }
        val raw = response.text ?: throw IllegalStateException("Project generation returned empty output.")
        val json = JSONObject(raw)
        val filesJson = json.getJSONArray("files")
        val files = buildList {
            for (i in 0 until filesJson.length()) {
                val obj = filesJson.getJSONObject(i)
                add(
                    GeneratedFileSpec(
                        relativePath = obj.getString("relativePath"),
                        language = obj.optString("language", "text"),
                        content = obj.getString("content")
                    )
                )
            }
        }
        return GeneratedProjectBundle(
            projectName = json.optString("projectName", "JarvisGeneratedApp"),
            packageName = json.optString("packageName", "com.jarvis.generated.app"),
            summary = json.optString("summary", "Project generated successfully."),
            files = files
        )
    }

    suspend fun analyzeAndFixProject(
        files: List<GeneratedFileSpec>,
        localIssuesDescription: String
    ): CodeFixBundle {
        val codePayload = buildString {
            appendLine("Review these project files, detect any syntax/logic/Gradle/XML/HTML/Python bugs, and fix them.")
            if (localIssuesDescription.isNotBlank()) {
                appendLine("Pre-flight validator detected:")
                appendLine(localIssuesDescription)
            }
            files.take(8).forEach { f ->
                appendLine("--- FILE: ${f.relativePath} (${f.language}) ---")
                appendLine(f.content.take(4000))
            }
        }
        val response = withTimeout(60_000L) {
            codeFixModel.generateContent(codePayload)
        }
        val raw = response.text ?: throw IllegalStateException("Code analyzer returned empty response.")
        val json = JSONObject(raw)
        val arr = json.getJSONArray("fixedFiles")
        val fixed = buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                add(
                    GeneratedFileSpec(
                        relativePath = obj.getString("relativePath"),
                        language = obj.optString("language", "text"),
                        content = obj.getString("content")
                    )
                )
            }
        }
        return CodeFixBundle(
            summary = json.optString("summary", "Code analysis complete."),
            fixedFiles = fixed
        )
    }
}
