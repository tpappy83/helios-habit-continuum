package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.api.GeminiClient
import com.example.data.api.HeliosClient
import com.example.data.db.AppDatabase
import com.example.data.model.*
import com.example.data.repository.HabitRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class HabitViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = HabitRepository(
        database.habitDao(),
        database.userStatsDao(),
        database.chatMessageDao(),
        database.habitEntityDao(),
        application
    )

    val heliosClient = HeliosClient(application)

    // --- Core Reactive Database Flows ---
    val habits: StateFlow<List<Habit>> = repository.allHabits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val logs: StateFlow<List<HabitLog>> = repository.allLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val userStats: StateFlow<UserStats?> = repository.userStats
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val badges: StateFlow<List<Badge>> = repository.allBadges
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chatHistory: StateFlow<List<ChatMessage>> = repository.chatHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val habitEntities: StateFlow<List<HabitEntity>> = repository.allHabitEntities
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- UI State Management ---
    private val _isChatLoading = MutableStateFlow(false)
    val isChatLoading: StateFlow<Boolean> = _isChatLoading.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    // Smart Recommendations State
    private val _isRecommending = MutableStateFlow(false)
    val isRecommending: StateFlow<Boolean> = _isRecommending.asStateFlow()

    private val _recommendedHabits = MutableStateFlow<List<AIHabitSuggestion>>(emptyList())
    val recommendedHabits: StateFlow<List<AIHabitSuggestion>> = _recommendedHabits.asStateFlow()

    // PC & Browser Telemetry states
    private val _telemetryLogs = MutableStateFlow<List<TelemetryLog>>(emptyList())
    val telemetryLogs: StateFlow<List<TelemetryLog>> = _telemetryLogs.asStateFlow()

    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _telemetrySuggestions = MutableStateFlow<List<AIHabitSuggestion>>(emptyList())
    val telemetrySuggestions: StateFlow<List<AIHabitSuggestion>> = _telemetrySuggestions.asStateFlow()

    private val _isAnalyzingTelemetry = MutableStateFlow(false)
    val isAnalyzingTelemetry: StateFlow<Boolean> = _isAnalyzingTelemetry.asStateFlow()

    private val companionPrefs = application.getSharedPreferences("companion_config", android.content.Context.MODE_PRIVATE)
    private val _companionIpAddress = MutableStateFlow(companionPrefs.getString("companion_ip", "10.0.2.2") ?: "10.0.2.2")
    val companionIpAddress: StateFlow<String> = _companionIpAddress.asStateFlow()

    private val _isSyncingToCompanion = MutableStateFlow(false)
    val isSyncingToCompanion: StateFlow<Boolean> = _isSyncingToCompanion.asStateFlow()

    private var telemetryServer: TelemetryServer? = null

    // Advanced Analytics State
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analyticsReport = MutableStateFlow<String?>(null)
    val analyticsReport: StateFlow<String?> = _analyticsReport.asStateFlow()

    init {
        viewModelScope.launch {
            repository.initializeDatabaseIfNeeded()
            repository.checkAndResetDailyProgress()
        }
    }

    // --- Habit Progress Operations ---
    fun incrementProgress(habitId: Int, value: Int) {
        viewModelScope.launch {
            repository.incrementHabitProgress(habitId, value)
            // Auto-sync back to twin companion on Windows
            syncToWindowsCompanion(getApplication())
        }
    }

    fun addNewHabit(title: String, desc: String, emoji: String, target: Int, unit: String, rTime: String, rType: String) {
        viewModelScope.launch {
            val newHabit = Habit(
                title = title,
                description = desc,
                categoryIcon = emoji,
                frequency = "Daily",
                targetValue = target,
                unit = unit,
                reminderTime = rTime,
                reminderType = rType,
                pointsAwarded = 10 + (target / 50).coerceAtMost(15)
            )
            repository.insertHabit(newHabit)
        }
    }

    fun deleteHabit(habit: Habit) {
        viewModelScope.launch {
            repository.deleteHabit(habit)
        }
    }

    fun createHabitFromEntity(habitEntity: HabitEntity) {
        viewModelScope.launch {
            // 1. Insert into HabitEntity table
            repository.insertHabitEntity(habitEntity)

            // 2. Also map to standard Habit and insert for tracking
            val mappedHabit = Habit(
                title = habitEntity.name,
                description = habitEntity.goals,
                categoryIcon = when {
                    habitEntity.name.contains("water", true) || habitEntity.name.contains("hydrate", true) -> "💧"
                    habitEntity.name.contains("read", true) || habitEntity.name.contains("study", true) -> "📖"
                    habitEntity.name.contains("run", true) || habitEntity.name.contains("cardio", true) || habitEntity.name.contains("gym", true) -> "🏃"
                    habitEntity.name.contains("meditate", true) || habitEntity.name.contains("mind", true) || habitEntity.name.contains("focus", true) -> "🧠"
                    habitEntity.name.contains("diet", true) || habitEntity.name.contains("eat", true) || habitEntity.name.contains("salad", true) -> "🥗"
                    else -> "⭐"
                },
                frequency = habitEntity.frequency,
                targetValue = 1,
                unit = "times",
                reminderTime = "08:00 AM",
                reminderType = "Time-based",
                isCompleted = habitEntity.isCompleted,
                pointsAwarded = 15
            )
            repository.insertHabit(mappedHabit)
        }
    }

    // --- PC & Browser Telemetry Integration Methods ---
    fun toggleTelemetryServer() {
        if (_isServerRunning.value) {
            telemetryServer?.stop()
            telemetryServer = null
            _isServerRunning.value = false
        } else {
            val server = TelemetryServer(9090) { log ->
                val current = _telemetryLogs.value.toMutableList()
                current.add(0, log)
                _telemetryLogs.value = current.take(50)
            }
            server.start()
            telemetryServer = server
            _isServerRunning.value = true
        }
    }

    fun addManualTelemetry(source: String, title: String, detail: String) {
        val log = TelemetryLog(
            source = source,
            title = title,
            timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            detail = detail
        )
        val current = _telemetryLogs.value.toMutableList()
        current.add(0, log)
        _telemetryLogs.value = current.take(50)
    }

    fun clearTelemetryLogs() {
        _telemetryLogs.value = emptyList()
        _telemetrySuggestions.value = emptyList()
    }

    fun analyzeTelemetryLogs() {
        if (_telemetryLogs.value.isEmpty()) return
        _isAnalyzingTelemetry.value = true
        viewModelScope.launch {
            val logsString = _telemetryLogs.value.joinToString("\n") {
                "[${it.timestamp}] ${it.source} - ${it.title} : ${it.detail}"
            }

            val systemPrompt = """
                You are Helios AI Desktop Habit Synthesis Engine. Your job is to analyze computer usage logs (sites visited, clicks, app focus) and discover patterns.
                Suggest exactly 2 personalized habits, projects, or routine upgrades based on these patterns.
                Format your response strictly as a JSON array of objects with these keys: "title", "description", "categoryIcon" (emoji), "targetValue" (integer), "unit", "reminderTime", "benefitExplanation".
                Do not include markdown or text before or after the JSON.
            """.trimIndent()

            val response = GeminiClient.generateContent(
                prompt = "Analyze these computer telemetry logs and generate tracking recommendations:\n$logsString",
                systemPrompt = systemPrompt
            )

            // Parse JSON response safely
            try {
                val cleanedResponse = response.trim()
                    .removePrefix("```json")
                    .removeSuffix("```")
                    .trim()

                val array = JSONArray(cleanedResponse)
                val suggestions = mutableListOf<AIHabitSuggestion>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    suggestions.add(
                        AIHabitSuggestion(
                            title = obj.getString("title"),
                            description = obj.getString("description"),
                            categoryIcon = obj.getString("categoryIcon"),
                            targetValue = obj.optInt("targetValue", 1),
                            unit = obj.getString("unit"),
                            reminderTime = obj.getString("reminderTime"),
                            benefitExplanation = obj.getString("benefitExplanation")
                        )
                    )
                }
                _telemetrySuggestions.value = suggestions
            } catch (e: Exception) {
                android.util.Log.e("HabitViewModel", "Error parsing telemetry JSON: ${e.message}", e)
                _telemetrySuggestions.value = listOf(
                    AIHabitSuggestion(
                        title = "Offline Focus",
                        description = "Take offline breaks to refresh mental capacity.",
                        categoryIcon = "🧠",
                        targetValue = 15,
                        unit = "mins",
                        reminderTime = "03:00 PM",
                        benefitExplanation = "Your continuous screen usage indicates you need eye and mind recovery periods."
                    )
                )
            } finally {
                _isAnalyzingTelemetry.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        telemetryServer?.stop()
    }

    // --- Helios Sync API Integration ---
    fun saveHeliosConfig(host: String, clientId: String, clientSecret: String, token: String) {
        heliosClient.saveConfig(host, clientId, clientSecret, token)
    }

    fun disconnectHelios() {
        heliosClient.disconnect()
    }

    fun synchronizeWithHelios() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = "Authenticating handshakes with Helios gateway..."
            val authSuccess = heliosClient.authenticate()
            if (authSuccess) {
                _syncMessage.value = "Uploading habit logs to the Helios data node..."
                val resultMessage = repository.syncWithHelios()
                _syncMessage.value = resultMessage
            } else {
                _syncMessage.value = "Helios Sync failed. Check authentication tokens."
            }
            _isSyncing.value = false
        }
    }

    fun dismissSyncMessage() {
        _syncMessage.value = null
    }

    // --- Smart Gemini-based Recommendation Engine ---
    fun fetchPersonalizedHabitSuggestions(userGoals: String) {
        viewModelScope.launch {
            _isRecommending.value = true
            val currentHabitsSummary = habits.value.joinToString { "${it.title} (${it.currentValue}/${it.targetValue} ${it.unit})" }
            val systemPrompt = """
                You are an expert behavioral scientist and habit architect connected to Helios OS.
                Your response must be a JSON array containing exactly 3-4 habit objects.
                Each object must have these exact string keys:
                "title", "description", "categoryIcon" (single emoji), "targetValue" (integer), "unit" (string, e.g. ml, min, reps, pages), "reminderTime" (string like "08:30 AM"), "benefitExplanation" (one clear motivational sentence showing synergy with their current lifestyle).
                Do not include any markdown format tags like ```json in the beginning or end of your text, output only pure parsable JSON text.
            """.trimIndent()

            val prompt = """
                Analyze the user's current habits: [$currentHabitsSummary]
                And their stated lifestyle goals: "$userGoals"
                Recommend 3-4 complementary, highly beneficial habits in response.
            """.trimIndent()

            try {
                val rawResponse = GeminiClient.generateContent(prompt, systemPrompt)
                // Parse the JSON array
                val cleanedResponse = rawResponse.trim().removeSurrounding("```json", "```").trim()
                val jsonArray = JSONArray(cleanedResponse)
                val list = mutableListOf<AIHabitSuggestion>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        AIHabitSuggestion(
                            title = obj.getString("title"),
                            description = obj.getString("description"),
                            categoryIcon = obj.getString("categoryIcon"),
                            targetValue = obj.getInt("targetValue"),
                            unit = obj.getString("unit"),
                            reminderTime = obj.getString("reminderTime"),
                            benefitExplanation = obj.getString("benefitExplanation")
                        )
                    )
                }
                _recommendedHabits.value = list
            } catch (e: Exception) {
                // Fallback default suggestions in case of parsing exceptions or no internet
                _recommendedHabits.value = listOf(
                    AIHabitSuggestion("Post-Meal Stretch", "Do a quick 10-minute full-body static stretch.", "🧘", 10, "min", "01:30 PM", "Greatly alleviates sitting fatigue and aligns posture."),
                    AIHabitSuggestion("Gratitude Reflection", "List 3 meaningful things you are grateful for today.", "✨", 3, "things", "08:00 AM", "Reduces baseline anxiety and triggers serotonin synthesis."),
                    AIHabitSuggestion("Protein Checkpoint", "Ingest clean, high-quality protein source.", "🍗", 30, "grams", "12:00 PM", "Critical for neurotransmitter synthesis and energy maintenance.")
                )
            } finally {
                _isRecommending.value = false
            }
        }
    }

    // --- Advanced Analytics Insight Generator ---
    fun generateAIAnalyticsInsight() {
        viewModelScope.launch {
            _isAnalyzing.value = true
            val currentHabits = habits.value.joinToString("\n") { 
                "- ${it.title}: ${it.currentValue}/${it.targetValue} ${it.unit} (Streak: ${it.streakCount} days, Sync: ${it.heliosSyncStatus})" 
            }
            val totalLogs = logs.value.take(15).joinToString("\n") { 
                "- Date: ${it.date} | Habit: ${it.title} | Achieved: ${it.valueProgress}/${it.targetValue} (Points: ${it.pointsEarned})" 
            }
            val stats = userStats.value

            val prompt = """
                Analyze the user's habit records and completion history. Generate an elite executive summary in highly readable Markdown format with these exact headings:
                
                ### Consistency Analysis
                [Summarize their current streak of ${stats?.currentStreak ?: 0} days and completion rates of the 💧, 🧠, and 📖 habits.]
                
                ### Behavioral Habits Correlation
                [Explain how different habits support or conflict with each other. Reference specific metrics (e.g. hydration supporting focus blocks).]
                
                ### Routine Optimization Recommendations
                [Provide 2 concrete schedule tweaks Alex can do to optimize consistency.]
                
                Keep the tone objective, highly personalized, professional, and motivating.
            """.trimIndent()

            try {
                val report = GeminiClient.generateContent(prompt, "You are the primary intelligence subsystem of Helios OS.")
                _analyticsReport.value = report
            } catch (e: Exception) {
                _analyticsReport.value = "### Consistency Analysis\nFailed to calculate analytics due to communication timeout. Maintain hydration to keep progress active!"
            } finally {
                _isAnalyzing.value = false
            }
        }
    }

    // --- Gemini Support & Advice Chatbot ---
    fun sendChatMessage(userText: String) {
        if (userText.trim().isEmpty()) return

        viewModelScope.launch {
            // Save user message to database
            repository.saveChatMessage("user", userText)

            _isChatLoading.value = true

            // Retrieve history to feed context
            val history = repository.chatHistory.first()
            val habitsSummary = habits.value.joinToString("\n") { "- ${it.title}: current ${it.currentValue}/${it.targetValue} ${it.unit} (streak: ${it.streakCount})" }
            val stats = userStats.value

            val systemPrompt = """
                You are Helios, a highly sophisticated AI companion and support agent for the user's habit tracker app.
                You are objective, professional, highly supportive, and helpful.
                You have real-time access to the user's local dashboard state:
                Current User Stats: Points: ${stats?.totalPoints ?: 0} | Daily Streak: ${stats?.currentStreak ?: 0} days.
                Active Local Habits:
                $habitsSummary
                
                Guidelines:
                1. Provide highly specific, science-backed habit troubleshooting advice.
                2. Remember previous conversation turns.
                3. Keep answers concise, helpful, and free from corporate marketing filler.
                4. Focus on multi-step planning or resolving consistency problems.
            """.trimIndent()

            val aiResponse = GeminiClient.generateChatResponse(history, systemPrompt)
            repository.saveChatMessage("model", aiResponse)
            _isChatLoading.value = false
        }
    }

    fun clearChatHistory() {
        viewModelScope.launch {
            repository.clearChat()
        }
    }

    // --- Points & Streak Simulation (Gamification Test) ---
    fun triggerSimulatedStreakIncrement() {
        viewModelScope.launch {
            repository.simulateStreakIncrement()
        }
    }

    fun addPointsDirectly(amount: Int) {
        viewModelScope.launch {
            val stats = repository.userStats.first() ?: return@launch
            database.userStatsDao().insertUserStats(stats.copy(totalPoints = stats.totalPoints + amount))
        }
    }

    fun redeemStoreItem(itemCost: Int, onItemRedeemed: (String) -> Unit) {
        viewModelScope.launch {
            val success = repository.redeemPoints(itemCost)
            if (success) {
                onItemRedeemed("Redeemed! Reward applied to your Helios dashboard.")
            } else {
                onItemRedeemed("Insufficient points for this reward. Keep completing habits!")
            }
        }
    }

    fun exportHabitHistoryToCSV(context: android.content.Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentLogs = logs.value
                val exportDir = context.getExternalFilesDir(null) ?: context.cacheDir
                if (!exportDir.exists()) {
                    exportDir.mkdirs()
                }
                val fileName = "habit_history_${System.currentTimeMillis()}.csv"
                val file = java.io.File(exportDir, fileName)
                
                java.io.FileWriter(file).use { writer ->
                    // Write Header
                    writer.write("Log ID,Habit ID,Title,Category,Date,Value Progress,Target Value,Is Completed,Points Earned,Timestamp\n")
                    
                    // Write Rows
                    for (log in currentLogs) {
                        val row = listOf(
                            log.id.toString(),
                            log.habitId.toString(),
                            escapeCsvField(log.title),
                            escapeCsvField(log.categoryIcon),
                            escapeCsvField(log.date),
                            log.valueProgress.toString(),
                            log.targetValue.toString(),
                            log.isCompleted.toString(),
                            log.pointsEarned.toString(),
                            log.timestamp.toString()
                        ).joinToString(",")
                        writer.write(row + "\n")
                    }
                }
                
                // Success callback with file name
                onResult("Success: ${file.name}")
                
                // Launch sharing chooser
                val authority = "${context.packageName}.fileprovider"
                val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(android.content.Intent.EXTRA_STREAM, uri)
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Habit History Export")
                    putExtra(android.content.Intent.EXTRA_TEXT, "Here is my habit history export for personal archiving.")
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = android.content.Intent.createChooser(intent, "Export Habit History CSV")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
                
            } catch (e: Exception) {
                android.util.Log.e("HabitViewModel", "CSV export failed", e)
                onResult("ERROR: ${e.message}")
            }
        }
    }

    private fun escapeCsvField(value: Any?): String {
        val str = value?.toString() ?: ""
        if (str.contains(",") || str.contains("\"") || str.contains("\n") || str.contains("\r")) {
            return "\"" + str.replace("\"", "\"\"") + "\""
        }
        return str
    }

    fun saveCompanionIp(ip: String) {
        companionPrefs.edit().putString("companion_ip", ip).apply()
        _companionIpAddress.value = ip
    }

    fun syncToWindowsCompanion(context: android.content.Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        val ip = _companionIpAddress.value
        val currentHabits = habits.value
        val stats = userStats.value

        _isSyncingToCompanion.value = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .writeTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                    .build()

                val jsonPayload = JSONObject().apply {
                    put("currentStreak", stats?.currentStreak ?: 0)
                    put("longestStreak", stats?.longestStreak ?: 0)
                    put("totalPoints", stats?.totalPoints ?: 0)

                    val habitsArray = JSONArray()
                    for (h in currentHabits) {
                        habitsArray.put(JSONObject().apply {
                            put("id", h.id)
                            put("title", h.title)
                            put("description", h.description)
                            put("categoryIcon", h.categoryIcon)
                            put("targetValue", h.targetValue)
                            put("currentValue", h.currentValue)
                            put("unit", h.unit)
                            put("reminderTime", h.reminderTime)
                            put("isCompleted", h.isCompleted)
                            put("streakCount", h.streakCount)
                        })
                    }
                    put("habits", habitsArray)
                }

                val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
                val body = jsonPayload.toString().toRequestBody(mediaType)
                
                val cleanedIp = ip.trim()
                val url = if (cleanedIp.startsWith("http://") || cleanedIp.startsWith("https://")) {
                    if (cleanedIp.endsWith("/sync_from_android")) cleanedIp else "$cleanedIp/sync_from_android"
                } else {
                    if (cleanedIp.contains(":")) "http://$cleanedIp/sync_from_android" else "http://$cleanedIp:9092/sync_from_android"
                }

                val request = Request.Builder()
                    .url(url)
                    .post(body)
                    .build()

                client.newCall(request).execute().use { response ->
                    _isSyncingToCompanion.value = false
                    if (response.isSuccessful) {
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(true, "Synchronized with Windows Companion!")
                        }
                    } else {
                        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            onResult(false, "Server error: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                _isSyncingToCompanion.value = false
                android.util.Log.e("HabitViewModel", "Failed to sync with Windows Companion: ${e.message}")
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    onResult(false, "Failed: ${e.message}")
                }
            }
        }
    }
}

// Simple Helper data class for suggested habits parsed from Gemini
data class AIHabitSuggestion(
    val title: String,
    val description: String,
    val categoryIcon: String,
    val targetValue: Int,
    val unit: String,
    val reminderTime: String,
    val benefitExplanation: String
)

class HabitViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HabitViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HabitViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// Telemetry structures for PC integration
data class TelemetryLog(
    val source: String,
    val title: String,
    val timestamp: String,
    val detail: String = ""
)

class TelemetryServer(
    private val port: Int,
    private val onLogReceived: (TelemetryLog) -> Unit
) {
    private var serverSocket: java.net.ServerSocket? = null
    private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread {
            try {
                serverSocket = java.net.ServerSocket(port)
                while (running) {
                    val socket = serverSocket?.accept() ?: break
                    handleClient(socket)
                }
            } catch (e: Exception) {
                android.util.Log.e("TelemetryServer", "Server error: ${e.message}")
            }
        }
        thread?.start()
    }

    fun stop() {
        running = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            android.util.Log.e("TelemetryServer", "Error closing server: ${e.message}")
        }
        serverSocket = null
        thread = null
    }

    private fun handleClient(socket: java.net.Socket) {
        Thread {
            try {
                val reader = java.io.BufferedReader(java.io.InputStreamReader(socket.getInputStream()))
                val writer = java.io.PrintWriter(socket.getOutputStream(), true)
                var line: String?
                var contentLength = 0
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.isEmpty()) break
                    if (line!!.startsWith("Content-Length:", ignoreCase = true)) {
                        contentLength = line!!.substringAfter(":").trim().toIntOrNull() ?: 0
                    }
                }

                val bodyBuilder = StringBuilder()
                if (contentLength > 0) {
                    val buffer = CharArray(1024)
                    var bytesRead = 0
                    var totalRead = 0
                    while (totalRead < contentLength && reader.read(buffer, 0, Math.min(buffer.size, contentLength - totalRead)).also { bytesRead = it } != -1) {
                        bodyBuilder.append(buffer, 0, bytesRead)
                        totalRead += bytesRead
                    }
                }

                val body = bodyBuilder.toString()
                var success = false
                try {
                    val json = org.json.JSONObject(body)
                    val source = json.optString("source", "Unknown Desktop App")
                    val title = json.optString("title", "Active Task")
                    val detail = json.optString("detail", "")
                    val log = TelemetryLog(
                        source = source,
                        title = title,
                        timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                        detail = detail
                    )
                    onLogReceived(log)
                    success = true
                } catch (e: Exception) {
                    android.util.Log.e("TelemetryServer", "Error parsing body: ${e.message}")
                }

                val responseBody = if (success) {
                    "{\"status\":\"success\",\"message\":\"Telemetry synced!\"}"
                } else {
                    "{\"status\":\"error\",\"message\":\"Invalid JSON payload\"}"
                }

                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: application/json")
                writer.println("Content-Length: ${responseBody.toByteArray().size}")
                writer.println("Access-Control-Allow-Origin: *")
                writer.println("Access-Control-Allow-Methods: POST, GET, OPTIONS")
                writer.println("Access-Control-Allow-Headers: Content-Type")
                writer.println("")
                writer.print(responseBody)
                writer.flush()
                socket.close()
            } catch (e: Exception) {
                android.util.Log.e("TelemetryServer", "Client handling error: ${e.message}")
            }
        }.start()
    }
}
