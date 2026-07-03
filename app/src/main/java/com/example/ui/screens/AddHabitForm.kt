package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.HabitEntity
import com.example.ui.theme.*

/**
 * A beautiful, highly-polished form component to create a new HabitEntity.
 * Includes complete input validation, responsive error states, and standard Material 3 spacing.
 *
 * @param onHabitCreated Callback triggered when the form is successfully validated and submitted.
 * @param onCancel Callback triggered when the user cancels or dismisses the form.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddHabitForm(
    onHabitCreated: (HabitEntity) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Form Input States
    var habitName by remember { mutableStateOf("") }
    var selectedFrequency by remember { mutableStateOf("") }
    var habitGoals by remember { mutableStateOf("") }

    // Validation Error States
    var nameError by remember { mutableStateOf<String?>(null) }
    var frequencyError by remember { mutableStateOf<String?>(null) }
    var goalsError by remember { mutableStateOf<String?>(null) }

    // Available standard frequencies
    val frequencies = listOf("Daily", "Weekly", "Monthly", "Custom")
    var isFrequencyDropdownExpanded by remember { mutableStateOf(false) }

    // Validation runner function
    val validateAndSubmit = {
        var isValid = true

        if (habitName.trim().isEmpty()) {
            nameError = "Habit name is required"
            isValid = false
        } else {
            nameError = null
        }

        if (selectedFrequency.trim().isEmpty()) {
            frequencyError = "Please select a frequency"
            isValid = false
        } else {
            frequencyError = null
        }

        if (habitGoals.trim().isEmpty()) {
            goalsError = "A goal or target description is required"
            isValid = false
        } else {
            goalsError = null
        }

        if (isValid) {
            val newHabit = HabitEntity(
                name = habitName.trim(),
                frequency = selectedFrequency.trim(),
                goals = habitGoals.trim(),
                isCompleted = false
            )
            onHabitCreated(newHabit)
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("habit_form_container"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SleekBackground),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        border = BorderStroke(1.dp, SleekBorder.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "New Habit Icon",
                    tint = SleekPrimary,
                    modifier = Modifier.size(28.dp)
                )
                Text(
                    text = "Architect New Habit",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = SleekDarkPurpleText
                )
            }

            Divider(color = SleekBorder.copy(alpha = 0.3f), thickness = 1.dp)

            // Input: Habit Name
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Habit Name",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = SleekGrayText
                )
                OutlinedTextField(
                    value = habitName,
                    onValueChange = {
                        habitName = it
                        if (it.isNotEmpty()) nameError = null
                    },
                    placeholder = { Text("e.g. Read Philosophy, Cardio") },
                    isError = nameError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Next
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("habit_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SleekPrimary,
                        unfocusedBorderColor = SleekBorder,
                        focusedContainerColor = SleekSurface,
                        unfocusedContainerColor = SleekSurface,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                AnimatedVisibility(
                    visible = nameError != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    nameError?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Error Icon",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("validation_error_text_name")
                            )
                        }
                    }
                }
            }

            // Input: Frequency Selector (Exposed Dropdown Menu)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Frequency",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = SleekGrayText
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    ExposedDropdownMenuBox(
                        expanded = isFrequencyDropdownExpanded,
                        onExpandedChange = { isFrequencyDropdownExpanded = !isFrequencyDropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedFrequency,
                            onValueChange = {},
                            readOnly = true,
                            placeholder = { Text("Select frequency") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isFrequencyDropdownExpanded)
                            },
                            isError = frequencyError != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                                .testTag("habit_frequency_dropdown"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = SleekPrimary,
                                unfocusedBorderColor = SleekBorder,
                                focusedContainerColor = SleekSurface,
                                unfocusedContainerColor = SleekSurface,
                                errorBorderColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )

                        ExposedDropdownMenu(
                            expanded = isFrequencyDropdownExpanded,
                            onDismissRequest = { isFrequencyDropdownExpanded = false },
                            modifier = Modifier.background(SleekSurface)
                        ) {
                            frequencies.forEach { freq ->
                                DropdownMenuItem(
                                    text = { Text(freq, fontWeight = FontWeight.Medium, color = SleekText) },
                                    onClick = {
                                        selectedFrequency = freq
                                        isFrequencyDropdownExpanded = false
                                        frequencyError = null
                                    },
                                    modifier = Modifier.testTag("freq_option_$freq")
                                )
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = frequencyError != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    frequencyError?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Error Icon",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("validation_error_text_frequency")
                            )
                        }
                    }
                }
            }

            // Input: User-defined Goals / Target description
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "User-Defined Goals",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = SleekGrayText
                )
                OutlinedTextField(
                    value = habitGoals,
                    onValueChange = {
                        habitGoals = it
                        if (it.isNotEmpty()) goalsError = null
                    },
                    placeholder = { Text("e.g. Read 20 pages every night, do 30 pushups") },
                    isError = goalsError != null,
                    minLines = 3,
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("habit_goals_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SleekPrimary,
                        unfocusedBorderColor = SleekBorder,
                        focusedContainerColor = SleekSurface,
                        unfocusedContainerColor = SleekSurface,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                AnimatedVisibility(
                    visible = goalsError != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    goalsError?.let {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Error Icon",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("validation_error_text_goals")
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Buttons Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cancel Button
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("cancel_habit_button"),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SleekBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SleekGrayText)
                ) {
                    Text(
                        text = "Cancel",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }

                // Submit Button
                Button(
                    onClick = validateAndSubmit,
                    modifier = Modifier
                        .weight(1.2f)
                        .height(48.dp)
                        .testTag("submit_habit_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SleekPrimary,
                        contentColor = SleekOnPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Save Icon",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Build Habit",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
