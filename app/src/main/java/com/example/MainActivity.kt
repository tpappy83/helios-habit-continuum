package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.ui.screens.MainScreen
import com.example.ui.viewmodel.HabitViewModel
import com.example.ui.viewmodel.HabitViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  var onShortcutPressed: (() -> Unit)? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()

    // Instantiate HabitViewModel using its Factory passing the application instance
    val viewModelFactory = HabitViewModelFactory(application)
    val viewModel = ViewModelProvider(this, viewModelFactory)[HabitViewModel::class.java]

    setContent {
      MyApplicationTheme {
        MainScreen(viewModel = viewModel)
      }
    }
  }

  override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
    if (keyCode == android.view.KeyEvent.KEYCODE_N && event != null && (event.isCtrlPressed || event.isMetaPressed)) {
      onShortcutPressed?.invoke()
      return true
    }
    return super.onKeyDown(keyCode, event)
  }
}
