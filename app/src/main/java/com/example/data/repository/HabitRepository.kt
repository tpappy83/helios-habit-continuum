package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.db.*
import com.example.data.model.*
import com.example.data.api.HeliosClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class HabitRepository(
    private val habitDao: HabitDao,
    private val userStatsDao: UserStatsDao,
    private val chatMessageDao: ChatMessageDao,
    private val habitEntityDao: HabitEntityDao,
    private val context: Context
) {
    val allHabits: Flow<List<Habit>> = habitDao.getAllHabits()
    val allLogs: Flow<List<HabitLog>> = habitDao.getAllHabitLogs()
    val userStats: Flow<UserStats?> = userStatsDao.getUserStats()
    val allBadges: Flow<List<Badge>> = userStatsDao.getAllBadges()
    val chatHistory: Flow<List<ChatMessage>> = chatMessageDao.getChatHistory()
    val allHabitEntities: Flow<List<HabitEntity>> = habitEntityDao.getAllHabitEntities()

    suspend fun insertHabitEntity(habitEntity: HabitEntity) = withContext(Dispatchers.IO) {
        habitEntityDao.insertHabitEntity(habitEntity)
    }

    suspend fun deleteHabitEntity(habitEntity: HabitEntity) = withContext(Dispatchers.IO) {
        habitEntityDao.deleteHabitEntity(habitEntity)
    }

    private val heliosClient = HeliosClient(context)

    // Formats a date to YYYY-MM-DD
    fun getCurrentLocalDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    // Initialize default habits, user stats, and badges if they don't exist
    suspend fun initializeDatabaseIfNeeded() = withContext(Dispatchers.IO) {
        val habitsList = allHabits.first()
        val currentDate = getCurrentLocalDate()

        if (habitsList.isEmpty()) {
            // Seed default active habits modeled after the Sleek Interface HTML template
            val defaultHabits = listOf(
                Habit(
                    title = "Daily Hydration",
                    description = "Stay healthy and energized throughout the day.",
                    categoryIcon = "💧",
                    frequency = "Daily",
                    targetValue = 2500,
                    currentValue = 1800,
                    unit = "ml",
                    reminderTime = "08:00 AM",
                    reminderType = "Time-based",
                    pointsAwarded = 15
                ),
                Habit(
                    title = "Deep Focus Block",
                    description = "Undivided focus for critical research & development.",
                    categoryIcon = "🧠",
                    frequency = "Daily",
                    targetValue = 90,
                    currentValue = 30,
                    unit = "min",
                    reminderTime = "10:30 AM",
                    reminderType = "Adaptive",
                    pointsAwarded = 25
                ),
                Habit(
                    title = "Reading Block",
                    description = "Read physical/educational content before sleep.",
                    categoryIcon = "📖",
                    frequency = "Daily",
                    targetValue = 20,
                    currentValue = 15,
                    unit = "pages",
                    reminderTime = "09:00 PM",
                    reminderType = "Location-based",
                    pointsAwarded = 10
                )
            )
            for (habit in defaultHabits) {
                habitDao.insertHabit(habit)
            }
        }

        // Seed badges
        val badgeList = allBadges.first()
        if (badgeList.isEmpty()) {
            val defaultBadges = listOf(
                Badge("streak_3", "First Ignition", "Achieve a 3-day perfect streak.", "🔥"),
                Badge("streak_7", "Consistency Key", "Achieve a 7-day perfect streak.", "⚡"),
                Badge("streak_30", "Habit Master", "Achieve a 30-day perfect streak.", "🏆"),
                Badge("hydration_hero", "Hydration Hero", "Complete early hydration 5 times.", "🌊"),
                Badge("intellectual", "Mental Titan", "Complete 10 Deep Focus blocks.", "🧠"),
                Badge("first_sync", "Helios Awakened", "Successfully synchronized habit log with Helios API.", "🟢")
            )
            for (badge in defaultBadges) {
                userStatsDao.insertBadge(badge)
            }
        }

        // Seed user stats
        val stats = userStats.first()
        if (stats == null) {
            userStatsDao.insertUserStats(
                UserStats(
                    currentStreak = 5, // Match the HTML default of 5-day streak
                    longestStreak = 12,
                    totalPoints = 320,
                    lastActiveDate = currentDate
                )
            )
        }
    }

    // Handles daily resetting when the app starts or a date change is detected
    suspend fun checkAndResetDailyProgress() = withContext(Dispatchers.IO) {
        val currentDate = getCurrentLocalDate()
        val habits = allHabits.first()
        val stats = userStatsDao.getUserStatsDirect() ?: return@withContext

        // If last active date is older than today, check if streak broke or we need to reset
        if (stats.lastActiveDate != currentDate) {
            val yesterdayStr = getDaysAgoDateString(1)
            var newStreak = stats.currentStreak

            // Check if streak is broken (did they complete any daily habits yesterday?)
            if (stats.lastActiveDate != yesterdayStr && stats.lastActiveDate.isNotEmpty()) {
                // Was not active yesterday: streak is broken
                newStreak = 0
            }

            // Update user stats with new last active date
            userStatsDao.insertUserStats(
                stats.copy(
                    currentStreak = newStreak,
                    lastActiveDate = currentDate
                )
            )

            // Reset habit current values for the new day
            for (habit in habits) {
                // If last completed wasn't yesterday or today, the habit streak resets
                val newHabitStreak = if (habit.lastCompletedDate == yesterdayStr || habit.lastCompletedDate == currentDate) {
                    habit.streakCount
                } else {
                    0
                }

                habitDao.updateHabit(
                    habit.copy(
                        currentValue = 0,
                        isCompleted = false,
                        streakCount = newHabitStreak
                    )
                )
            }
            Log.i("HabitRepository", "Daily reset performed. Date transitioned to $currentDate")
        }
    }

    private fun getDaysAgoDateString(daysAgo: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(cal.time)
    }

    // Increments a habit's progress. Checks for completion, updates streaks, points, and triggers badge awards.
    suspend fun incrementHabitProgress(habitId: Int, incrementValue: Int) = withContext(Dispatchers.IO) {
        val habit = habitDao.getHabitById(habitId) ?: return@withContext
        val currentDate = getCurrentLocalDate()

        val newProgress = (habit.currentValue + incrementValue).coerceAtMost(habit.targetValue)
        val wasCompleted = habit.isCompleted
        val isNowCompleted = newProgress >= habit.targetValue

        val updatedHabit = habit.copy(
            currentValue = newProgress,
            isCompleted = isNowCompleted,
            lastCompletedDate = if (isNowCompleted) currentDate else habit.lastCompletedDate,
            streakCount = if (isNowCompleted && !wasCompleted) habit.streakCount + 1 else habit.streakCount,
            heliosSyncStatus = "pending" // Needs to sync to Helios after update
        )
        habitDao.updateHabit(updatedHabit)

        // Award points, update overall stats, check badges if completion occurred
        if (isNowCompleted && !wasCompleted) {
            val stats = userStatsDao.getUserStatsDirect() ?: UserStats(lastActiveDate = currentDate)
            val pointsEarned = habit.pointsAwarded

            // Log completion history in database
            val log = HabitLog(
                habitId = habit.id,
                title = habit.title,
                categoryIcon = habit.categoryIcon,
                date = currentDate,
                valueProgress = newProgress,
                targetValue = habit.targetValue,
                isCompleted = true,
                pointsEarned = pointsEarned
            )
            habitDao.insertHabitLog(log)

            // Calculate new overall streaks
            val lastActive = stats.lastActiveDate
            val yesterday = getDaysAgoDateString(1)
            var newStreak = stats.currentStreak

            if (lastActive == yesterday) {
                newStreak += 1
            } else if (lastActive != currentDate) {
                // Was completely cold, reset to 1
                newStreak = 1
            } // If lastActive is already today, streak was already updated for today.

            val newLongestStreak = maxOf(newStreak, stats.longestStreak)
            val newTotalPoints = stats.totalPoints + pointsEarned

            val updatedStats = stats.copy(
                currentStreak = newStreak,
                longestStreak = newLongestStreak,
                totalPoints = newTotalPoints,
                lastActiveDate = currentDate
            )
            userStatsDao.insertUserStats(updatedStats)

            // Evaluate badge conditions
            checkAndAwardBadges(newStreak, updatedStats.totalPoints, updatedHabit)
        }
    }

    // Helper to evaluate and unlock achievement badges dynamically
    private suspend fun checkAndAwardBadges(streak: Int, totalPoints: Int, lastCompletedHabit: Habit) {
        val badges = userStatsDao.getAllBadges().first()
        val logs = habitDao.getAllHabitLogs().first()

        for (badge in badges) {
            if (badge.isUnlocked) continue

            var shouldUnlock = false
            when (badge.id) {
                "streak_3" -> if (streak >= 3) shouldUnlock = true
                "streak_7" -> if (streak >= 7) shouldUnlock = true
                "streak_30" -> if (streak >= 30) shouldUnlock = true
                "hydration_hero" -> {
                    val completions = logs.count { it.habitId == lastCompletedHabit.id && lastCompletedHabit.categoryIcon == "💧" && it.isCompleted }
                    if (completions >= 5) shouldUnlock = true
                }
                "intellectual" -> {
                    val completions = logs.count { it.habitId == lastCompletedHabit.id && lastCompletedHabit.categoryIcon == "🧠" && it.isCompleted }
                    if (completions >= 10) shouldUnlock = true
                }
            }

            if (shouldUnlock) {
                userStatsDao.updateBadge(
                    badge.copy(
                        isUnlocked = true,
                        unlockedAt = System.currentTimeMillis()
                    )
                )
                Log.i("HabitRepository", "Achievement unlocked! ${badge.name}")
            }
        }
    }

    // Custom method to simulate redeeming points for productivity rewards
    suspend fun redeemPoints(rewardCost: Int): Boolean = withContext(Dispatchers.IO) {
        val stats = userStatsDao.getUserStatsDirect() ?: return@withContext false
        if (stats.totalPoints >= rewardCost) {
            userStatsDao.insertUserStats(
                stats.copy(totalPoints = stats.totalPoints - rewardCost)
            )
            true
        } else {
            false
        }
    }

    // Custom method to simulate a manual streak increment for testing gamification rewards
    suspend fun simulateStreakIncrement() = withContext(Dispatchers.IO) {
        val stats = userStatsDao.getUserStatsDirect() ?: return@withContext
        val newStreak = stats.currentStreak + 1
        val newLongest = maxOf(newStreak, stats.longestStreak)
        val currentDate = getCurrentLocalDate()

        userStatsDao.insertUserStats(
            stats.copy(
                currentStreak = newStreak,
                longestStreak = newLongest,
                lastActiveDate = currentDate
            )
        )
        checkAndAwardBadges(newStreak, stats.totalPoints, Habit(title = "Simulation", description = "", categoryIcon = "", frequency = "", targetValue = 1, unit = "", reminderTime = "", reminderType = ""))
    }

    // Insert new custom/personalized habits
    suspend fun insertHabit(habit: Habit): Long = withContext(Dispatchers.IO) {
        habitDao.insertHabit(habit)
    }

    // Delete a habit
    suspend fun deleteHabit(habit: Habit) = withContext(Dispatchers.IO) {
        habitDao.deleteHabit(habit)
    }

    // Update a habit
    suspend fun updateHabit(habit: Habit) = withContext(Dispatchers.IO) {
        habitDao.updateHabit(habit)
    }

    // Chatbot context history saving
    suspend fun saveChatMessage(role: String, content: String) = withContext(Dispatchers.IO) {
        chatMessageDao.insertMessage(ChatMessage(role = role, content = content))
    }

    suspend fun clearChat() = withContext(Dispatchers.IO) {
        chatMessageDao.clearChatHistory()
    }

    // Sync with Helios Client Interface
    suspend fun syncWithHelios(): String = withContext(Dispatchers.IO) {
        val habits = allHabits.first()
        val result = heliosClient.syncHabits(habits)

        if (result.success) {
            // Mark all habits as synced
            for (habit in habits) {
                habitDao.updateHabit(habit.copy(heliosSyncStatus = "synced"))
            }

            // Unlock First Sync Badge
            val badge = userStatsDao.getAllBadges().first().find { it.id == "first_sync" }
            if (badge != null && !badge.isUnlocked) {
                userStatsDao.updateBadge(badge.copy(isUnlocked = true, unlockedAt = System.currentTimeMillis()))
            }
        }
        result.message
    }
}
