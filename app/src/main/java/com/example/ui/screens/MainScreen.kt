package com.example.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.example.MainActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.BorderStroke
import com.example.data.api.RemoteHabitTarget
import com.example.data.model.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.HabitViewModel
import com.example.ui.viewmodel.AIHabitSuggestion
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: HabitViewModel) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("today") }

    // Collect states from ViewModel
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val userStats by viewModel.userStats.collectAsStateWithLifecycle()
    val badges by viewModel.badges.collectAsStateWithLifecycle()
    val chatHistory by viewModel.chatHistory.collectAsStateWithLifecycle()
    val isChatLoading by viewModel.isChatLoading.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val isRecommending by viewModel.isRecommending.collectAsStateWithLifecycle()
    val recommendedHabits by viewModel.recommendedHabits.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val analyticsReport by viewModel.analyticsReport.collectAsStateWithLifecycle()
    val isHeliosConnected by viewModel.heliosClient.isConnected.collectAsStateWithLifecycle()

    // Overlay dialog states
    var showAddHabitDialog by remember { mutableStateOf(false) }
    var showNewHabitFormDialog by remember { mutableStateOf(false) }
    var showHeliosSettings by remember { mutableStateOf(false) }
    var showIncrementDialog by remember { mutableStateOf<Habit?>(null) }

    // Intercept hardware key shortcuts (Ctrl+N / Cmd+N) globally
    val activity = context.findActivity() as? MainActivity
    DisposableEffect(activity) {
        activity?.onShortcutPressed = {
            showNewHabitFormDialog = true
        }
        onDispose {
            val currentActivity = context.findActivity() as? MainActivity
            if (currentActivity?.onShortcutPressed != null) {
                currentActivity.onShortcutPressed = null
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("main_scaffold"),
        bottomBar = {
            BottomNavigationBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it }
            )
        },
        containerColor = SleekBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header Section
            HeaderSection(
                username = "Alex",
                isSynced = isHeliosConnected,
                onAvatarClick = { showHeliosSettings = true }
            )

            // Dynamic Content body based on bottom tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (currentTab) {
                    "today" -> TodayTab(
                        habits = habits,
                        viewModel = viewModel,
                        onAddHabitClick = { showNewHabitFormDialog = true },
                        onHabitClick = { showIncrementDialog = it },
                        onOptimizeClick = {
                            currentTab = "profile"
                            viewModel.generateAIAnalyticsInsight()
                        }
                    )
                    "habits" -> HabitsTab(
                        habits = habits,
                        recommendedHabits = recommendedHabits,
                        isRecommending = isRecommending,
                        viewModel = viewModel,
                        onAddHabitClick = { showNewHabitFormDialog = true }
                    )
                    "ai" -> ChatTab(
                        chatHistory = chatHistory,
                        isChatLoading = isChatLoading,
                        viewModel = viewModel
                    )
                    "profile" -> ProfileTab(
                        userStats = userStats,
                        badges = badges,
                        logs = logs,
                        isAnalyzing = isAnalyzing,
                        analyticsReport = analyticsReport,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    // --- Overlay Dialogs ---

    // 1. Add Habit Dialog
    if (showAddHabitDialog) {
        AddHabitDialog(
            onDismiss = { showAddHabitDialog = false },
            onConfirm = { title, desc, emoji, target, unit, time, type ->
                viewModel.addNewHabit(title, desc, emoji, target, unit, time, type)
                showAddHabitDialog = false
                Toast.makeText(context, "Habit added successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 1b. Custom Habit Form Dialog (triggered by shortcuts or add clicks)
    if (showNewHabitFormDialog) {
        Dialog(onDismissRequest = { showNewHabitFormDialog = false }) {
            AddHabitForm(
                onHabitCreated = { habitEntity ->
                    viewModel.createHabitFromEntity(habitEntity)
                    showNewHabitFormDialog = false
                    Toast.makeText(context, "Habit custom-crafted successfully!", Toast.LENGTH_SHORT).show()
                },
                onCancel = { showNewHabitFormDialog = false }
            )
        }
    }

    // 2. Helios Integration Settings Dialog
    if (showHeliosSettings) {
        HeliosSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showHeliosSettings = false }
        )
    }

    // 3. Quick Habit Progress Incrementer Dialog
    showIncrementDialog?.let { habit ->
        IncrementProgressDialog(
            habit = habit,
            onDismiss = { showIncrementDialog = null },
            onIncrement = { value ->
                viewModel.incrementProgress(habit.id, value)
                showIncrementDialog = null
                Toast.makeText(context, "Progress updated!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 4. Global Sync Notification Banner
    syncMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissSyncMessage() },
            title = { Text("Helios Sync Status", fontWeight = FontWeight.Bold, color = SleekPrimary) },
            text = { Text(msg, color = SleekText, fontSize = 14.sp) },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.dismissSyncMessage() },
                    modifier = Modifier.testTag("dismiss_sync_btn")
                ) {
                    Text("OK", color = SleekPrimary, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = SleekBackground
        )
    }
}

// --- Sub-Composables & Screens ---

@Composable
fun HeaderSection(
    username: String,
    isSynced: Boolean,
    onAvatarClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, start = 24.dp, end = 24.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                // Pulse sync circle matching CSS
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = if (isSynced) SleekGreen.copy(alpha = alpha) else Color.Gray.copy(alpha = alpha),
                            shape = CircleShape
                        )
                )

                Text(
                    text = if (isSynced) "Helios Synchronized" else "Helios Offline (Tap Avatar)",
                    color = SleekGrayText,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Text(
                text = "Morning, ",
                color = SleekText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-0.5).sp
            )
            Text(
                text = username,
                color = SleekText,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.5).sp
            )
        }

        // Avatar matching HTML styled circle
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(color = SleekSecondaryContainer, shape = CircleShape)
                .border(width = 1.dp, color = SleekAccent, shape = CircleShape)
                .clip(CircleShape)
                .clickable { onAvatarClick() }
                .testTag("avatar_button"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = username.take(1).uppercase(),
                color = SleekDarkPurpleText,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

@Composable
fun BottomNavigationBar(
    currentTab: String,
    onTabSelected: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        color = SleekSurface,
        tonalElevation = 4.dp,
        border = BorderStroke(width = 0.5.dp, color = SleekBorder)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                label = "Today",
                icon = Icons.Default.Today,
                selected = currentTab == "today",
                onClick = { onTabSelected("today") },
                testTag = "tab_today"
            )
            BottomNavItem(
                label = "Habits",
                icon = Icons.Outlined.FormatListBulleted,
                selected = currentTab == "habits",
                onClick = { onTabSelected("habits") },
                testTag = "tab_habits"
            )
            BottomNavItem(
                label = "Helios AI",
                icon = Icons.Outlined.AutoAwesome,
                selected = currentTab == "ai",
                onClick = { onTabSelected("ai") },
                testTag = "tab_ai"
            )
            BottomNavItem(
                label = "Profile",
                icon = Icons.Outlined.Person,
                selected = currentTab == "profile",
                onClick = { onTabSelected("profile") },
                testTag = "tab_profile"
            )
        }
    }
}

@Composable
fun BottomNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        modifier = Modifier
            .testTag(testTag)
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Aesthetic pill for active item from Sleek Interface CSS
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (selected) SleekSecondaryContainer else Color.Transparent)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) SleekDarkPurpleText else SleekGrayText,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (selected) SleekDarkPurpleText else SleekGrayText,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

// --- TODAY TAB ---

@Composable
fun TodayTab(
    habits: List<Habit>,
    viewModel: HabitViewModel,
    onAddHabitClick: () -> Unit,
    onHabitClick: (Habit) -> Unit,
    onOptimizeClick: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // AI Insight Banner (matches CSS theme exactly)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_insight_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SleekAccent)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(color = SleekDarkPurpleText, shape = RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "AI Insight",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = "AI INSIGHT",
                            color = SleekDarkPurpleText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            letterSpacing = 1.2.sp
                        )
                    }

                    Text(
                        text = "You're 24% more likely to complete Deep Focus when you finish hydration before 9:00 AM.",
                        color = SleekDarkPurpleText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 24.sp
                    )

                    Button(
                        onClick = { onOptimizeClick() },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekDarkPurpleText),
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        modifier = Modifier.testTag("optimize_schedule_btn")
                    ) {
                        Text(
                            text = "OPTIMIZE SCHEDULE",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Active Habits Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE HABITS",
                    color = SleekGrayText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                TextButton(
                    onClick = { onAddHabitClick() },
                    modifier = Modifier.testTag("add_habit_shortcut")
                ) {
                    Text("+ Add Habit (Ctrl+N)", color = SleekPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (habits.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No habits scheduled for today. Click '+ Add Habit' to get started!",
                        color = SleekGrayText,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            items(habits, key = { it.id }) { habit ->
                HabitCardItem(
                    habit = habit,
                    onClick = { onHabitClick(habit) },
                    onCheckClick = {
                        // Quick increment of 1 step or fraction
                        val fraction = if (habit.targetValue > 100) 250 else 1
                        viewModel.incrementProgress(habit.id, fraction)
                    }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun HabitCardItem(
    habit: Habit,
    onClick: () -> Unit,
    onCheckClick: () -> Unit
) {
    val progress = if (habit.targetValue > 0) {
        habit.currentValue.toFloat() / habit.targetValue.toFloat()
    } else {
        0f
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("habit_card_${habit.id}"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SleekSurface),
        border = BorderStroke(
            width = if (habit.isCompleted) 1.dp else 0.dp,
            color = if (habit.isCompleted) SleekPrimary else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Category Icon Emoji Container
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(color = SleekSecondaryContainer, shape = RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = habit.categoryIcon, fontSize = 24.sp)
            }

            // Info Column
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = habit.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = SleekText
                )
                Text(
                    text = "${habit.currentValue} / ${habit.targetValue} ${habit.unit}",
                    fontSize = 12.sp,
                    color = SleekGrayText
                )

                // Beautiful customized progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(color = SleekPrimaryContainer, shape = CircleShape)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(color = SleekPrimary, shape = CircleShape)
                    )
                }

                // Reminder Pill
                if (habit.reminderTime.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(
                            imageVector = when (habit.reminderType) {
                                "Location-based" -> Icons.Default.Place
                                "Adaptive" -> Icons.Default.AutoAwesome
                                else -> Icons.Default.AccessTime
                            },
                            contentDescription = "Reminder",
                            tint = SleekPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "${habit.reminderTime} • ${habit.reminderType}",
                            fontSize = 10.sp,
                            color = SleekPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Completion check circle button (at least 48dp target)
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable { onCheckClick() }
                    .border(
                        width = 2.dp,
                        color = if (habit.isCompleted) SleekPrimary else SleekBorder,
                        shape = CircleShape
                    )
                    .background(if (habit.isCompleted) SleekAccent.copy(alpha = 0.4f) else Color.Transparent)
                    .testTag("completion_circle_${habit.id}"),
                contentAlignment = Alignment.Center
            ) {
                if (habit.isCompleted) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Completed",
                        tint = SleekPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// --- HABITS TAB (MANAGEMENT & RECOMMENDATION) ---

@Composable
fun HabitsTab(
    habits: List<Habit>,
    recommendedHabits: List<AIHabitSuggestion>,
    isRecommending: Boolean,
    viewModel: HabitViewModel,
    onAddHabitClick: () -> Unit
) {
    var userGoals by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Master Catalog section
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HABIT CATALOG",
                    color = SleekGrayText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Button(
                    onClick = { onAddHabitClick() },
                    colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("add_custom_habit_btn")
                ) {
                    Text("+ Custom Habit (Ctrl+N)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // List active habits for quick deletion/reminders checking
        if (habits.isEmpty()) {
            item {
                Text(
                    text = "No local habits exist. Create one or try AI Recommendations below!",
                    fontSize = 13.sp,
                    color = SleekGrayText,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            items(habits) { habit ->
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("manage_habit_${habit.id}"),
                    colors = CardDefaults.cardColors(containerColor = SleekSurface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(text = habit.categoryIcon, fontSize = 24.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = habit.title, fontWeight = FontWeight.Bold, color = SleekText, fontSize = 15.sp)
                            Text(text = "Target: ${habit.targetValue} ${habit.unit} | Reminder: ${habit.reminderTime}", fontSize = 12.sp, color = SleekGrayText)
                        }
                        IconButton(
                            onClick = { viewModel.deleteHabit(habit) },
                            modifier = Modifier.testTag("delete_habit_${habit.id}")
                        ) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete Habit", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        // Smart Suggestion Generator Section
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_suggestion_builder_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SleekPrimaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Suggestions",
                            tint = SleekDarkPurpleText,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "HELIOS PERSONALIZED ARCHITECT",
                            color = SleekDarkPurpleText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "Enter your primary life goals (e.g. 'boost focus, run a 5k, drink more fluid') and the AI will architect routine upgrades.",
                        color = SleekDarkPurpleText,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )

                    OutlinedTextField(
                        value = userGoals,
                        onValueChange = { userGoals = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goals_input_field"),
                        placeholder = { Text("E.g., build focus, sleep better") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SleekPrimary,
                            unfocusedBorderColor = SleekBorder,
                            focusedContainerColor = SleekBackground,
                            unfocusedContainerColor = SleekBackground
                        ),
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            viewModel.fetchPersonalizedHabitSuggestions(userGoals)
                        })
                    )

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.fetchPersonalizedHabitSuggestions(userGoals)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekDarkPurpleText),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_suggestions_btn"),
                        enabled = !isRecommending
                    ) {
                        if (isRecommending) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("ARCHITECT RECOMMENDATIONS", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Suggested habits recommendations list
        if (recommendedHabits.isNotEmpty()) {
            item {
                Text(
                    text = "RECOMMENDED BY HELIOS AI",
                    color = SleekGrayText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(recommendedHabits) { suggestion ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_suggestion_item_${suggestion.title}"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = SleekSurface),
                    border = BorderStroke(1.dp, SleekAccent)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color = SleekSecondaryContainer, shape = RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = suggestion.categoryIcon, fontSize = 20.sp)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = suggestion.title, fontWeight = FontWeight.Bold, color = SleekText, fontSize = 15.sp)
                                Text(text = "Target: ${suggestion.targetValue} ${suggestion.unit}", fontSize = 11.sp, color = SleekGrayText)
                            }
                            IconButton(
                                onClick = {
                                    viewModel.addNewHabit(
                                        title = suggestion.title,
                                        desc = suggestion.description,
                                        emoji = suggestion.categoryIcon,
                                        target = suggestion.targetValue,
                                        unit = suggestion.unit,
                                        rTime = suggestion.reminderTime,
                                        rType = "Adaptive"
                                    )
                                    Toast.makeText(viewModel.getApplication(), "Added suggested habit!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .testTag("add_suggestion_${suggestion.title}")
                                    .background(color = SleekPrimary, shape = CircleShape)
                                    .size(36.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = "Add suggested", tint = Color.White)
                            }
                        }

                        Text(
                            text = suggestion.description,
                            color = SleekText,
                            fontSize = 13.sp
                        )

                        // AI Synergy Explanation
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color = SleekSecondaryContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "💡 ${suggestion.benefitExplanation}",
                                color = SleekDarkPurpleText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // ------------------ DESKTOP TELEMETRY SYNC ENGINE ------------------
        item {
            val telemetryLogs by viewModel.telemetryLogs.collectAsStateWithLifecycle()
            val isServerRunning by viewModel.isServerRunning.collectAsStateWithLifecycle()
            val telemetrySuggestions by viewModel.telemetrySuggestions.collectAsStateWithLifecycle()
            val isAnalyzingTelemetry by viewModel.isAnalyzingTelemetry.collectAsStateWithLifecycle()

            var manualSource by remember { mutableStateOf("Chrome Browser") }
            var manualTitle by remember { mutableStateOf("") }
            var manualDetail by remember { mutableStateOf("") }
            var showManualInputForm by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("desktop_telemetry_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SleekSurface),
                border = BorderStroke(1.dp, SleekBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "Desktop Telemetry",
                            tint = SleekPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DESKTOP & BROWSER TELEMETRY",
                                color = SleekText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Listen or sync browser histories & system clicks securely",
                                color = SleekGrayText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // HTTP Server control panel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color = SleekBackground, shape = RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isServerRunning) "Live Listener Active" else "Live Listener Inactive",
                                color = if (isServerRunning) Color(0xFF4CAF50) else SleekGrayText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Port: 9090 (CORS Active)",
                                color = SleekGrayText,
                                fontSize = 11.sp
                            )
                        }

                        Button(
                            onClick = { viewModel.toggleTelemetryServer() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isServerRunning) Color.Red.copy(alpha = 0.7f) else SleekPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = if (isServerRunning) "STOP" else "START SERVER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    // Manual Paste / Quick Simulation button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showManualInputForm = !showManualInputForm },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (showManualInputForm) "Close Simulator" else "Paste / Log Telemetry", fontSize = 11.sp)
                        }

                        if (telemetryLogs.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { viewModel.clearTelemetryLogs() },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.7f))
                            ) {
                                Text("Clear", fontSize = 11.sp)
                            }
                        }
                    }

                    // Simulation Input Form
                    if (showManualInputForm) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color = SleekBackground, shape = RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Mock Telemetry Simulator", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SleekText)
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { manualSource = "Chrome Browser" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (manualSource == "Chrome Browser") SleekPrimary else SleekSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Browser", fontSize = 10.sp, color = if (manualSource == "Chrome Browser") Color.White else SleekText)
                                }
                                Button(
                                    onClick = { manualSource = "VS Code" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (manualSource == "VS Code") SleekPrimary else SleekSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("IDE", fontSize = 10.sp, color = if (manualSource == "VS Code") Color.White else SleekText)
                                }
                                Button(
                                    onClick = { manualSource = "System Click" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (manualSource == "System Click") SleekPrimary else SleekSurface
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Clicks", fontSize = 10.sp, color = if (manualSource == "System Click") Color.White else SleekText)
                                }
                            }

                            OutlinedTextField(
                                value = manualTitle,
                                onValueChange = { manualTitle = it },
                                placeholder = { Text("URL, file title, or action") },
                                label = { Text("Title / URL") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary),
                                shape = RoundedCornerShape(8.dp)
                            )

                            OutlinedTextField(
                                value = manualDetail,
                                onValueChange = { manualDetail = it },
                                placeholder = { Text("Metrics (e.g. clicks count, active mins)") },
                                label = { Text("Detail / Metrics") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary),
                                shape = RoundedCornerShape(8.dp)
                            )

                            Button(
                                onClick = {
                                    if (manualTitle.isNotEmpty()) {
                                        viewModel.addManualTelemetry(manualSource, manualTitle, manualDetail)
                                        manualTitle = ""
                                        manualDetail = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Log Telemetry Action", color = Color.White, fontSize = 12.sp)
                            }
                        }
                    }

                    // Telemetry logs list
                    if (telemetryLogs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Captured Telemetry Streams (${telemetryLogs.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SleekGrayText)
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(150.dp)
                                    .background(color = SleekBackground, shape = RoundedCornerShape(16.dp))
                                    .padding(8.dp)
                            ) {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(telemetryLogs) { log ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(color = SleekSurface, shape = RoundedCornerShape(8.dp))
                                                .padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        text = if (log.source.contains("Browser")) "🌐" else if (log.source.contains("VS Code") || log.source.contains("IDE")) "💻" else "🖱️",
                                                        fontSize = 12.sp
                                                    )
                                                    Text(text = log.source, fontWeight = FontWeight.Bold, color = SleekPrimary, fontSize = 11.sp)
                                                }
                                                Text(text = log.title, color = SleekText, fontSize = 12.sp, maxLines = 1)
                                                if (log.detail.isNotEmpty()) {
                                                    Text(text = log.detail, color = SleekGrayText, fontSize = 10.sp)
                                                }
                                            }
                                            Text(text = log.timestamp, color = SleekGrayText, fontSize = 10.sp)
                                        }
                                    }
                                }
                            }

                            // AI Action button
                            Button(
                                onClick = { viewModel.analyzeTelemetryLogs() },
                                colors = ButtonDefaults.buttonColors(containerColor = SleekDarkPurpleText),
                                shape = CircleShape,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isAnalyzingTelemetry
                            ) {
                                if (isAnalyzingTelemetry) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                } else {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Text("AI DISCOVER HABITS FROM TELEMETRY", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // Helpful starter hints
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color = SleekBackground, shape = RoundedCornerShape(16.dp))
                                .padding(14.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("💡 Quick telemetry instructions:", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = SleekGrayText)
                                Text("• Start the listener to receive raw developer/browser logs.", fontSize = 11.sp, color = SleekGrayText)
                                Text("• Or, click 'Paste / Log Telemetry' to mock web-browsing, IDE coding, or clicks.", fontSize = 11.sp, color = SleekGrayText)
                                Text("• Run AI Discovery to auto-architect tracking categories & goals!", fontSize = 11.sp, color = SleekGrayText)
                            }
                        }
                    }

                    // Telemetry derived suggestions
                    if (telemetrySuggestions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("DISCOVERED BY HELIOS COGNITION", color = SleekPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 1.sp)
                            
                            telemetrySuggestions.forEach { suggestion ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    colors = CardDefaults.cardColors(containerColor = SleekSecondaryContainer.copy(alpha = 0.4f)),
                                    border = BorderStroke(1.dp, SleekPrimary.copy(alpha = 0.3f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Text(text = suggestion.categoryIcon, fontSize = 20.sp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(text = suggestion.title, fontWeight = FontWeight.Bold, color = SleekText, fontSize = 14.sp)
                                                Text(text = "Target: ${suggestion.targetValue} ${suggestion.unit}", fontSize = 11.sp, color = SleekGrayText)
                                            }
                                            IconButton(
                                                onClick = {
                                                    viewModel.addNewHabit(
                                                        title = suggestion.title,
                                                        desc = suggestion.description,
                                                        emoji = suggestion.categoryIcon,
                                                        target = suggestion.targetValue,
                                                        unit = suggestion.unit,
                                                        rTime = suggestion.reminderTime,
                                                        rType = "Adaptive"
                                                    )
                                                    Toast.makeText(viewModel.getApplication(), "Added telemetry habit!", Toast.LENGTH_SHORT).show()
                                                },
                                                modifier = Modifier
                                                    .background(color = SleekPrimary, shape = CircleShape)
                                                    .size(32.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Add, contentDescription = "Add telemetry habit", tint = Color.White, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Text(text = suggestion.description, fontSize = 12.sp, color = SleekText)
                                        Text(text = "💡 Cognitive Inference: ${suggestion.benefitExplanation}", fontSize = 11.sp, color = SleekDarkPurpleText, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ------------------ WINDOWS COMPANION TWIN SYNC ------------------
        item {
            val companionIp by viewModel.companionIpAddress.collectAsStateWithLifecycle()
            val isSyncingToCompanion by viewModel.isSyncingToCompanion.collectAsStateWithLifecycle()
            var ipInput by remember(companionIp) { mutableStateOf(companionIp) }
            val context = LocalContext.current

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("windows_companion_sync_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SleekSurface),
                border = BorderStroke(1.dp, SleekBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Title section
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "Windows Twin Sync",
                            tint = SleekPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "WINDOWS COMPANION TWIN SYNC",
                                color = SleekText,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "Synchronize habits bidirectionally to your desktop overlay",
                                color = SleekGrayText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    // Explanation
                    Text(
                        text = "Your twin Windows companion tracks active windows and system clicks (sent to the Android Live Listener above). In return, this synchronizer pushes your real-time daily habit progress and statistics to the Windows overlay for widgets and local alerts.",
                        color = SleekGrayText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    // Companion IP Configuration
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Windows Companion IP Address:",
                            color = SleekText,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = ipInput,
                                onValueChange = { 
                                    ipInput = it
                                    viewModel.saveCompanionIp(it)
                                },
                                placeholder = { Text("e.g. 10.0.2.2 or 192.168.1.100") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SleekPrimary,
                                    unfocusedBorderColor = SleekBorder
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Button(
                                onClick = {
                                    viewModel.syncToWindowsCompanion(context) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isSyncingToCompanion,
                                colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isSyncingToCompanion) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                                } else {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(imageVector = Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("Sync Now", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Auto sync info badge
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(color = SleekBackground, shape = RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("⚡", fontSize = 16.sp)
                            Column {
                                Text(
                                    text = "Auto-Sync Enabled",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = SleekText
                                )
                                Text(
                                    text = "Habit check-ins will automatically update the Windows overlay in real time.",
                                    fontSize = 11.sp,
                                    color = SleekGrayText
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// --- CONTEXT-AWARE GEMINI CHAT TAB ---

@Composable
fun ChatTab(
    chatHistory: List<ChatMessage>,
    isChatLoading: Boolean,
    viewModel: HabitViewModel
) {
    var userText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Auto scroll chat to end when new messages arrive
    LaunchedEffect(chatHistory.size) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top panel controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "HELIOS COMPANION",
                color = SleekGrayText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            TextButton(
                onClick = { viewModel.clearChatHistory() },
                modifier = Modifier.testTag("clear_chat_history_btn")
            ) {
                Text("Clear History", color = Color.Red.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }

        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("chat_messages_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (chatHistory.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(imageVector = Icons.Default.Chat, contentDescription = "Chat", tint = SleekAccent, modifier = Modifier.size(48.dp))
                            Text(
                                text = "Welcome! I am Helios, your lifestyle assistant. Ask me how to optimize your focus block, troubleshoot streaks, or sync routine metrics.",
                                color = SleekGrayText,
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            } else {
                items(chatHistory) { message ->
                    val isUser = message.role == "user"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .wrapContentWidth(if (isUser) androidx.compose.ui.Alignment.End else androidx.compose.ui.Alignment.Start)
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 20.dp,
                                        topEnd = 20.dp,
                                        bottomStart = if (isUser) 20.dp else 0.dp,
                                        bottomEnd = if (isUser) 0.dp else 20.dp
                                    )
                                )
                                .background(if (isUser) SleekPrimary else SleekSecondaryContainer)
                                .padding(14.dp)
                        ) {
                            Text(
                                text = message.content,
                                color = if (isUser) Color.White else SleekOnSecondaryContainer,
                                fontSize = 14.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            if (isChatLoading) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(SleekSecondaryContainer)
                                .padding(14.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    color = SleekPrimary,
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("Helios is writing...", fontSize = 13.sp, color = SleekGrayText)
                            }
                        }
                    }
                }
            }
        }

        // Input bottom bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = userText,
                onValueChange = { userText = it },
                modifier = Modifier
                    .weight(1f)
                    .testTag("chat_input_text"),
                placeholder = { Text("Ask Helios anything...") },
                shape = CircleShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SleekPrimary,
                    unfocusedBorderColor = SleekBorder,
                    focusedContainerColor = SleekSurface,
                    unfocusedContainerColor = SleekSurface
                ),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (userText.trim().isNotEmpty()) {
                        keyboardController?.hide()
                        viewModel.sendChatMessage(userText)
                        userText = ""
                    }
                })
            )

            // Send Button with at least 48dp target
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(SleekPrimary)
                    .clickable {
                        if (userText.trim().isNotEmpty()) {
                            keyboardController?.hide()
                            viewModel.sendChatMessage(userText)
                            userText = ""
                        }
                    }
                    .testTag("chat_send_btn"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// --- PROFILE & ANALYTICS TAB ---

@Composable
fun ProfileTab(
    userStats: UserStats?,
    badges: List<Badge>,
    logs: List<HabitLog>,
    isAnalyzing: Boolean,
    analyticsReport: String?,
    viewModel: HabitViewModel
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Gamification Stats Grid (Streaks & Points)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .testTag("gamification_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SleekSecondaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ALEX'S STANDING",
                            fontWeight = FontWeight.Bold,
                            color = SleekOnSecondaryContainer,
                            fontSize = 11.sp,
                            letterSpacing = 1.2.sp
                        )
                        Row(
                            modifier = Modifier
                                .background(color = SleekDarkPurpleText, shape = CircleShape)
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = "Points", tint = Color.Yellow, modifier = Modifier.size(14.dp))
                            Text(
                                text = "${userStats?.totalPoints ?: 0} PTS",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🔥 ${userStats?.currentStreak ?: 0}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = SleekText)
                            Text(text = "Current Streak", fontSize = 11.sp, color = SleekGrayText)
                        }
                        Divider(modifier = Modifier.height(48.dp).width(1.dp), color = SleekBorder)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🏆 ${userStats?.longestStreak ?: 0}", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = SleekText)
                            Text(text = "Longest Streak", fontSize = 11.sp, color = SleekGrayText)
                        }
                    }

                    // Simulated streak multiplier button for testers to quickly increment streak and unlock awards!
                    Button(
                        onClick = { viewModel.triggerSimulatedStreakIncrement() },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("simulate_streak_btn")
                    ) {
                        Text("SIMULATE DAY COMPLETE (+1 STREAK)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        }

        // 2. Advanced Analytics AI Subsystem
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_analytics_card"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = SleekBackground),
                border = BorderStroke(1.dp, SleekBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = "AI Analytics", tint = SleekPrimary, modifier = Modifier.size(24.dp))
                        Text(
                            text = "ADVANCED ANALYTICS INTERFACE",
                            fontWeight = FontWeight.Bold,
                            color = SleekPrimary,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = "Synthesizes correlation patterns, weekly schedules, and daily habit consistency into an actionable briefing.",
                        color = SleekGrayText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Button(
                        onClick = { viewModel.generateAIAnalyticsInsight() },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_analytics_report_btn"),
                        enabled = !isAnalyzing
                    ) {
                        if (isAnalyzing) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp))
                        } else {
                            Text("CALCULATE COHERENCE REPORT", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Display AI Report
                    analyticsReport?.let { report ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(color = SleekSurface, shape = RoundedCornerShape(16.dp))
                                .padding(16.dp)
                        ) {
                            Text(
                                text = report,
                                color = SleekText,
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }
        }

        // 3. Achievement Badges Milestone Panel
        item {
            Text(
                text = "ACHIEVEMENT MILESTONES",
                color = SleekGrayText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Let's render badges in beautiful card list
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    badges.filterIndexed { idx, _ -> idx % 2 == 0 }.forEach { badge ->
                        BadgeCard(badge = badge)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    badges.filterIndexed { idx, _ -> idx % 2 != 0 }.forEach { badge ->
                        BadgeCard(badge = badge)
                    }
                }
            }
        }

        // 4. Points Redemption Store
        item {
            Text(
                text = "REDEEM POINTS STORE",
                color = SleekGrayText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("redemption_store_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SleekSurface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    StoreRedeemItem(
                        title = "Focus Music Pack",
                        description = "Unlock 8 spatial ambient focus synth tracks.",
                        cost = 100,
                        onRedeemClick = {
                            viewModel.redeemStoreItem(100) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    Divider(color = SleekBorder.copy(alpha = 0.5f))
                    StoreRedeemItem(
                        title = "Helios Premium Theme",
                        description = "High-fidelity organic obsidian interface.",
                        cost = 250,
                        onRedeemClick = {
                            viewModel.redeemStoreItem(250) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    Divider(color = SleekBorder.copy(alpha = 0.5f))
                    StoreRedeemItem(
                        title = "AI Lifestyle Consult",
                        description = "15-min interactive voice routine architect session.",
                        cost = 500,
                        onRedeemClick = {
                            viewModel.redeemStoreItem(500) { message ->
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                }
            }
        }

        item {
            Text(
                text = "DATA PORTABILITY & EXPORT",
                color = SleekGrayText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("data_portability_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = SleekSurface),
                border = BorderStroke(1.dp, SleekBorder)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SaveAlt,
                            contentDescription = "Export Data",
                            tint = SleekPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(
                                text = "Personal CSV Archiver",
                                fontWeight = FontWeight.Bold,
                                color = SleekText,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${logs.size} logs available for backup",
                                color = SleekGrayText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    Text(
                        text = "Archiving creates a local CSV spreadsheet containing all your historic logs, check-ins, values, target values, points, and timestamps. You can open this file in Microsoft Excel, Google Sheets, or keep it as a secure backup.",
                        color = SleekGrayText,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    Button(
                        onClick = {
                            if (logs.isEmpty()) {
                                Toast.makeText(context, "No habit logs logged yet to export!", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.exportHabitHistoryToCSV(context) { status ->
                                    Toast.makeText(context, status, Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                        shape = CircleShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("export_csv_btn")
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Text("EXPORT HABIT HISTORY (CSV)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun BadgeCard(badge: Badge) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("badge_card_${badge.id}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (badge.isUnlocked) SleekSecondaryContainer else SleekSurface.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (badge.isUnlocked) SleekAccent else SleekBorder.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = if (badge.isUnlocked) SleekAccent else Color.LightGray.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (badge.isUnlocked) badge.icon else "🔒",
                    fontSize = 24.sp
                )
            }
            Text(
                text = badge.name,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                color = if (badge.isUnlocked) SleekText else SleekGrayText
            )
            Text(
                text = badge.description,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                color = SleekGrayText,
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
fun StoreRedeemItem(
    title: String,
    description: String,
    cost: Int,
    onRedeemClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, color = SleekText, fontSize = 14.sp)
            Text(text = description, color = SleekGrayText, fontSize = 11.sp, lineHeight = 14.sp)
        }
        Button(
            onClick = onRedeemClick,
            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
            shape = CircleShape,
            modifier = Modifier.testTag("redeem_${title.replace(" ", "_")}")
        ) {
            Text(text = "$cost PTS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// --- SYSTEM CONFIG DIALOGS ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Int, String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("💧") }
    var target by remember { mutableStateOf("2500") }
    var unit by remember { mutableStateOf("ml") }
    var reminderTime by remember { mutableStateOf("08:00 AM") }
    var reminderType by remember { mutableStateOf("Time-based") } // Time-based, Location-based, Adaptive

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_habit_dialog_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SleekBackground)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Architect New Habit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = SleekPrimary
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Habit Title") },
                    modifier = Modifier.fillMaxWidth().testTag("add_habit_title"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                )

                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Short Description") },
                    modifier = Modifier.fillMaxWidth().testTag("add_habit_desc"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it },
                        label = { Text("Emoji") },
                        modifier = Modifier.weight(1f).testTag("add_habit_emoji"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                    )
                    OutlinedTextField(
                        value = target,
                        onValueChange = { target = it },
                        label = { Text("Target") },
                        modifier = Modifier.weight(1.5f).testTag("add_habit_target"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("Unit") },
                        modifier = Modifier.weight(1f).testTag("add_habit_unit"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = reminderTime,
                        onValueChange = { reminderTime = it },
                        label = { Text("Reminder Time") },
                        modifier = Modifier.weight(1f).testTag("add_habit_time"),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                    )

                    // Reminder types selector
                    Box(modifier = Modifier.weight(1.2f)) {
                        var expanded by remember { mutableStateOf(false) }
                        OutlinedTextField(
                            value = reminderType,
                            onValueChange = {},
                            label = { Text("Reminder Mode") },
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = true }
                                .testTag("add_habit_rem_mode"),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary),
                            trailingIcon = { Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Drop") }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(SleekBackground)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Time-based") },
                                onClick = { reminderType = "Time-based"; expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Adaptive (AI)") },
                                onClick = { reminderType = "Adaptive"; expanded = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Location-based") },
                                onClick = { reminderType = "Location-based"; expanded = false }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("add_habit_cancel")) {
                        Text("CANCEL", color = SleekGrayText, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (title.isNotEmpty()) {
                                onConfirm(
                                    title, desc, emoji,
                                    target.toIntOrNull() ?: 1,
                                    unit, reminderTime, reminderType
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                        shape = CircleShape,
                        modifier = Modifier.testTag("add_habit_confirm")
                    ) {
                        Text("CREATE", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeliosSettingsDialog(
    viewModel: HabitViewModel,
    onDismiss: () -> Unit
) {
    var hostUrl by remember { mutableStateOf(viewModel.heliosClient.getHostUrl()) }
    var clientId by remember { mutableStateOf(viewModel.heliosClient.getClientId()) }
    var clientSecret by remember { mutableStateOf(viewModel.heliosClient.getClientSecret()) }
    var accessToken by remember { mutableStateOf(viewModel.heliosClient.getAccessToken()) }

    val isConnected by viewModel.heliosClient.isConnected.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("helios_config_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SleekBackground)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Helios OS Connectivity",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = SleekPrimary
                )

                Text(
                    text = "Provide your client parameters to configure real-time bi-directional sync of Alex's logs with the Helios dashboard.",
                    fontSize = 12.sp,
                    color = SleekGrayText,
                    lineHeight = 16.sp
                )

                OutlinedTextField(
                    value = hostUrl,
                    onValueChange = { hostUrl = it },
                    label = { Text("Gateway host URL") },
                    modifier = Modifier.fillMaxWidth().testTag("helios_host_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                )

                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it },
                    label = { Text("Client ID") },
                    modifier = Modifier.fillMaxWidth().testTag("helios_id_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                )

                OutlinedTextField(
                    value = clientSecret,
                    onValueChange = { clientSecret = it },
                    label = { Text("Client Secret") },
                    modifier = Modifier.fillMaxWidth().testTag("helios_secret_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                )

                OutlinedTextField(
                    value = accessToken,
                    onValueChange = { accessToken = it },
                    label = { Text("Access Sync Token") },
                    modifier = Modifier.fillMaxWidth().testTag("helios_token_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isConnected) {
                        Button(
                            onClick = {
                                viewModel.disconnectHelios()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                            modifier = Modifier.weight(1f).testTag("disconnect_helios_btn")
                        ) {
                            Text("DISCONNECT", color = Color.White)
                        }
                    } else {
                        Button(
                            onClick = {
                                viewModel.saveHeliosConfig(hostUrl, clientId, clientSecret, accessToken)
                                viewModel.synchronizeWithHelios()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                            modifier = Modifier.weight(1f).testTag("connect_helios_btn")
                        ) {
                            Text("CONNECT", color = Color.White)
                        }
                    }

                    if (isConnected) {
                        Button(
                            onClick = {
                                viewModel.synchronizeWithHelios()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SleekDarkPurpleText),
                            modifier = Modifier.weight(1.2f).testTag("sync_helios_btn"),
                            enabled = !isSyncing
                        ) {
                            Text("SYNC NOW", color = Color.White)
                        }
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(androidx.compose.ui.Alignment.End)
                        .testTag("dismiss_config_btn")
                ) {
                    Text("CLOSE", color = SleekGrayText)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IncrementProgressDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onIncrement: (Int) -> Unit
) {
    var textValue by remember { mutableStateOf("10") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("increment_progress_card"),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = SleekBackground)
        ) {
            Column(
                modifier = Modifier.padding(24.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Log Progress: ${habit.title}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = SleekPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "Increment your current progress today. Target is ${habit.targetValue} ${habit.unit}.",
                    fontSize = 13.sp,
                    color = SleekGrayText,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    label = { Text("Progress Increment (${habit.unit})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("increment_value_input"),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = SleekPrimary)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Button(
                        onClick = { onIncrement(habit.targetValue - habit.currentValue) },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekAccent),
                        shape = CircleShape,
                        modifier = Modifier.testTag("mark_complete_instant")
                    ) {
                        Text("COMPLETION", color = SleekDarkPurpleText, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Button(
                        onClick = {
                            val v = textValue.toIntOrNull() ?: 1
                            onIncrement(v)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SleekPrimary),
                        shape = CircleShape,
                        modifier = Modifier.testTag("increment_confirm_btn")
                    ) {
                        Text("ADD VALUE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(androidx.compose.ui.Alignment.End).testTag("increment_cancel_btn")
                ) {
                    Text("CANCEL", color = SleekGrayText)
                }
            }
        }
    }
}

private fun Context.findActivity(): ComponentActivity? {
    var currentContext = this
    while (currentContext is ContextWrapper) {
        if (currentContext is ComponentActivity) {
            return currentContext
        }
        currentContext = currentContext.baseContext
    }
    return null
}
