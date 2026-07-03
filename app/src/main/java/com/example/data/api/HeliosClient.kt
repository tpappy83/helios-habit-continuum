package com.example.data.api

import android.content.Context
import android.util.Log
import com.example.data.model.Habit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// --- Remote Helios Data Models ---

data class HeliosHabitPayload(
    val habitId: Int,
    val title: String,
    val currentValue: Int,
    val targetValue: Int,
    val unit: String,
    val isCompleted: Boolean,
    val timestamp: Long,
    val streakCount: Int
)

data class HeliosSyncResult(
    val success: Boolean,
    val message: String,
    val serverTime: Long,
    val itemsSynced: Int
)

data class RemoteHabitTarget(
    val title: String,
    val description: String,
    val categoryIcon: String,
    val targetValue: Int,
    val unit: String,
    val recommendedTime: String,
    val benefitExplanation: String
)

// --- Helios API Client ---

class HeliosClient(private val context: Context) {
    private val sharedPrefs = context.getSharedPreferences("helios_config", Context.MODE_PRIVATE)

    private val _isConnected = MutableStateFlow(sharedPrefs.getBoolean("connected", false))
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun getHostUrl(): String = sharedPrefs.getString("host_url", "https://api.helios.ecosystem/v1") ?: ""
    fun getClientId(): String = sharedPrefs.getString("client_id", "") ?: ""
    fun getClientSecret(): String = sharedPrefs.getString("client_secret", "") ?: ""
    fun getAccessToken(): String = sharedPrefs.getString("access_token", "") ?: ""

    fun saveConfig(hostUrl: String, clientId: String, clientSecret: String, token: String) {
        sharedPrefs.edit()
            .putString("host_url", hostUrl)
            .putString("client_id", clientId)
            .putString("client_secret", clientSecret)
            .putString("access_token", token)
            .putBoolean("connected", clientId.isNotEmpty() && token.isNotEmpty())
            .apply()
        _isConnected.value = clientId.isNotEmpty() && token.isNotEmpty()
    }

    fun disconnect() {
        sharedPrefs.edit().clear().apply()
        _isConnected.value = false
    }

    // Connects / authenticates with the Helios gateway
    suspend fun authenticate(): Boolean {
        delay(1200) // Simulate network handshaking & OAuth key exchange
        val clientId = getClientId()
        val token = getAccessToken()
        val success = clientId.isNotEmpty() && token.isNotEmpty()
        sharedPrefs.edit().putBoolean("connected", success).apply()
        _isConnected.value = success
        return success
    }

    // Pushes local habit completion data to the Helios ecosystem
    suspend fun pushHabit(habit: Habit): Boolean {
        if (!_isConnected.value) {
            Log.w("HeliosClient", "Push aborted: Helios not synchronized.")
            return false
        }
        delay(800) // Simulate network request delay
        Log.i("HeliosClient", "Successfully pushed habit ${habit.title} to Helios dashboard.")
        return true
    }

    // Synchronizes bulk habit completions with Helios API
    suspend fun syncHabits(habits: List<Habit>): HeliosSyncResult {
        if (!_isConnected.value) {
            return HeliosSyncResult(false, "Authentication required. Please connect to Helios.", System.currentTimeMillis(), 0)
        }
        delay(1500) // Simulate batch upload over HTTP POST /v1/sync
        return HeliosSyncResult(
            success = true,
            message = "Synchronization successful! ${habits.size} habit checkpoints pushed to Helios node.",
            serverTime = System.currentTimeMillis(),
            itemsSynced = habits.size
        )
    }

    // Pulls recommended targets from Helios AI ecosystem (e.g., matching Alex's morning parameters)
    suspend fun pullRecommendedTargets(): List<RemoteHabitTarget> {
        delay(1000) // Simulate API request GET /v1/recommendations
        return listOf(
            RemoteHabitTarget(
                title = "Early Hydration",
                description = "Complete hydration targets before 9:00 AM.",
                categoryIcon = "💧",
                targetValue = 2500,
                unit = "ml",
                recommendedTime = "08:00 AM",
                benefitExplanation = "Your cognitive peak rises by 15% when hydrated early."
            ),
            RemoteHabitTarget(
                title = "Deep Focus Block",
                description = "Undivided focus block for critical projects.",
                categoryIcon = "🧠",
                targetValue = 90,
                unit = "min",
                recommendedTime = "10:30 AM",
                benefitExplanation = "Synchronizing with Alex's peak morning dopamine levels."
            ),
            RemoteHabitTarget(
                title = "Aesthetic Reading",
                description = "Offline reading block to foster synthesis.",
                categoryIcon = "📖",
                targetValue = 20,
                unit = "pages",
                recommendedTime = "09:00 PM",
                benefitExplanation = "Mitigates screen-fatigue and enhances sleep quality."
            )
        )
    }
}
