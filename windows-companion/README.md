# Helios Habit Tracker — Windows Twin Companion

The **Helios Windows Twin Companion** is a sleek, modern desktop client designed to work in absolute synchrony with your **Helios Habit Tracker Android Application**. 

By establishing a low-latency local network connection, your computer activity serves as tracking telemetry to build habit logs, and your real-time Android stats are updated directly inside a stylish, cyberpunk-themed desktop overlay HUD.

---

## 🎨 Visual Preview & Features

- **Cyberpunk Dark HUD**: Displays real-time computer use metrics in an eye-safe, immersive deep violet and cyan palette.
- **Active Window tracking**: Safely captures process names (e.g., `chrome.exe`, `code.exe`) and active tab headers for context.
- **Input Density metrics**: Tracks keyboard strokes and mouse click rates to measure focus intensity blocks.
- **Bidirectional Sync (CORS Active)**:
  - **Outgoing**: Streams your live PC usage logs to the Android server on port **`9090`**.
  - **Incoming**: Establishes a local listener on port **`9092`** that receives your real-time stats, streaks, level-ups, points, and checklist progresses pushed from Android.

---

## 🚀 Quick Start Guide (Windows)

### 1. Requirements
Ensure you have Python 3.8+ installed on your computer. You can download it from [python.org](https://www.python.org/).

### 2. Startup
1. Extract or clone this folder onto your Windows computer.
2. Double-click the **`run_companion.bat`** script. This will:
   - Check and verify your python setup.
   - Automatically install required dependencies (`psutil`, `pynput`, `pywin32`) from `requirements.txt`.
   - Start the twin companion app.

### 3. Bidirectional Setup (Link Android and Windows)

#### Step A: Configure Target IP on Windows Companion
When the Windows Companion starts, look at the status bar at the bottom or the text box in the **Connection Config** frame. 
- It will list this PC's local network IP addresses (e.g., `192.168.1.104` or `10.0.0.12`).
- In the **Twin Android IP Target** input, enter your Android device's IP address (if you are running on an emulator, you can leave it as the default `127.0.0.1` / `10.0.2.2`).
- Click **Connect**.

#### Step B: Configure IP on Android Application
1. Open the Helios Android app on your phone.
2. Navigate to the **Today** tab and scroll down to the **Windows Companion Twin Sync** card.
3. In the **Windows Companion IP Address** field, enter the local IP of your PC (the IP you saw in Step A, e.g., `192.168.1.104` or `10.0.0.12`).
4. Press **Sync Now** to test connection! 
5. In the **Desktop & Browser Telemetry** card right above, toggle **Start Server** on.

---

## ⚡ Real-Time Auto-Synchronization

Once configured, the twin architecture operates entirely automatically:
- **PC to Android**: Every 2 seconds, your PC broadcasts its active window process and intensity metrics to Android. On Android, you can click **"AI Discover Habits from Telemetry"** to have Gemini analyze your computer use and generate habit suggestions (e.g., "Take a break after 90 mins of VS Code!").
- **Android to PC**: Whenever you check in a habit on your Android phone, or progress increases, Android automatically fires a background update to port `9092` on your computer. Your Windows HUD updates instantly, celebrating streaks and showing your real-time daily progress bar!

---

## ⚙️ Safe-Fallback Mode
Don't have access to python packages or running on a secure network? 
The script is designed with **zero-crash fallback safety**:
- If `pynput` is blocked by permissions, the app will gracefully run in fallback mode using local simulators to simulate clicks and keys, preserving the network synchronization loop!
- If running on a non-Windows machine, native window APIs fall back to mock IDE headers so you can still test synchronization perfectly.

*Stay synchronized, stay mindful. Powered by Helios Ecosystem.*
