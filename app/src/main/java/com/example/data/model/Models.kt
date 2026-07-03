package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class Habit(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val categoryIcon: String, // e.g. "💧", "🧠", "📖", "🏃", "🥗"
    val frequency: String, // e.g. "Daily", "Weekly"
    val targetValue: Int, // e.g. 2500, 90, 20
    val currentValue: Int = 0, // Current progress for today
    val unit: String, // e.g. "ml", "min", "pages"
    val reminderTime: String, // e.g. "08:00 AM"
    val reminderType: String, // "Time-based", "Adaptive", "Location-based"
    val lastCompletedDate: String = "", // "YYYY-MM-DD"
    val isCompleted: Boolean = false,
    val streakCount: Int = 0,
    val pointsAwarded: Int = 10,
    val heliosSyncStatus: String = "pending" // "synced", "pending", "failed"
)

@Entity(tableName = "habit_logs")
data class HabitLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val habitId: Int,
    val title: String,
    val categoryIcon: String,
    val date: String, // "YYYY-MM-DD"
    val valueProgress: Int,
    val targetValue: Int,
    val isCompleted: Boolean,
    val pointsEarned: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "badges")
data class Badge(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val icon: String, // Emoji or icon name
    val isUnlocked: Boolean = false,
    val unlockedAt: Long? = null
)

@Entity(tableName = "user_stats")
data class UserStats(
    @PrimaryKey val id: Int = 1,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val totalPoints: Int = 0,
    val lastActiveDate: String = "" // "YYYY-MM-DD"
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String, // "user" or "model"
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)
