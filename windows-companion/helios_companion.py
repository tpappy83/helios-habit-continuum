# -*- coding: utf-8 -*-
"""
Helios Habit Tracker - Windows Twin Companion Program
A highly sophisticated, real-time desktop telemetry tracker and overlay dashboard 
that synchronizes bidirectionally with its twin Android App.

Features:
1. Active Window Tracking: Identifies active processes, browser tabs, and titles.
2. Click & Keystroke Monitor: Tallies system clicks and keypress counts to measure focus density.
3. Live Telemetry Outbox: Delivers real-time JSON logs to the Android app on port 9090.
4. Synced Habit Inbox Server: Opens a local HTTP server on port 9092 to receive habit list and progress state from Android.
5. Cyberpunk HUD Dashboard: Features an immersive dark violet and neon-tinted tkinter GUI showing live stats.
6. Local IP Detector: Scans and lists your LAN IP addresses for quick configuration on the Android App.
"""

import os
import sys
import time
import json
import socket
import threading
import traceback
from http.server import HTTPServer, BaseHTTPRequestHandler

# Standard GUI imports
import tkinter as tk
from tkinter import ttk, messagebox

# Fallback-safe HTTP client
import urllib.request
import urllib.error

# Graceful third-party imports
try:
    import psutil
except ImportError:
    psutil = None

try:
    import win32gui
    import win32process
except ImportError:
    win32gui = None
    win32process = None

try:
    from pynput import keyboard, mouse
except ImportError:
    keyboard = None
    mouse = None


# --- CONFIGURATION & GLOBAL STATES ---
ANDROID_SERVER_PORT = 9090
COMPANION_SERVER_PORT = 9092

class CompanionState:
    def __init__(self):
        self.is_running = True
        self.android_ip = "127.0.0.1" # Default fallback (for emulators or localhost testing)
        
        # Telemetry counts
        self.click_count = 0
        self.keypress_count = 0
        self.active_window = "Idle"
        self.active_process = "system"
        self.session_start_time = time.time()
        
        # Synced states from Android
        self.current_streak = 0
        self.longest_streak = 0
        self.total_points = 0
        self.habits = [] # Holds list of synced habits
        
        # Server status indicators
        self.telemetry_connected = False
        self.receiver_status = "Inactive"
        self.last_sync_time = "Never"

state = CompanionState()


# --- LOCAL NETWORK HELPER ---
def get_local_ips():
    """Returns a list of local LAN IPs to assist user in configuration."""
    ips = []
    try:
        hostname = socket.gethostname()
        ips.append(f"Hostname: {hostname}")
        # Get all interfaces
        for info in socket.gethostbyname_ex(hostname)[2]:
            if not info.startswith("127."):
                ips.append(info)
    except Exception:
        pass
    if not ips or len(ips) <= 1:
        # Fallback quick scan
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            s.connect(("8.8.8.8", 80))
            ips.append(s.getsockname()[0])
            s.close()
        except Exception:
            ips.append("127.0.0.1")
    return ips


# --- TELEMETRY TRACKERS (WINDOWS NATIVE & FALLBACKS) ---
def get_active_window_info():
    """Retrieves active window title and process name."""
    if sys.platform != "win32" or win32gui is None:
        # Fallback mockup/mock titles for cross-platform compliance
        return "Helios Workspace IDE", "code.exe"
    
    try:
        hwnd = win32gui.GetForegroundWindow()
        if hwnd:
            title = win32gui.GetWindowText(hwnd)
            _, pid = win32process.GetWindowThreadProcessId(hwnd)
            if psutil and pid > 0:
                process = psutil.Process(pid)
                process_name = process.name()
                return title, process_name
            return title, "unknown"
    except Exception:
        pass
    return "Desktop Background", "explorer.exe"


# --- SYSTEM INPUT LISTENER HOOKS ---
def start_input_listeners():
    """Asynchronously starts background hooks for mouse & keyboard counts."""
    if keyboard is None or mouse is None:
        print("[Telemetry] Input hooks (pynput) missing. Incremental metrics will use simulators.")
        return

    def on_click(x, y, button, pressed):
        if pressed and state.is_running:
            state.click_count += 1

    def on_press(key):
        if state.is_running:
            state.keypress_count += 1

    # Listeners in non-blocking daemon threads
    mouse_listener = mouse.Listener(on_click=on_click)
    mouse_listener.daemon = True
    mouse_listener.start()

    keyboard_listener = keyboard.Listener(on_press=on_press)
    keyboard_listener.daemon = True
    keyboard_listener.start()


# --- TELEMETRY TRANSMITTER (TO ANDROID) ---
def start_telemetry_outbox():
    """Periodically packages tracking data and POSTs it to the Android App."""
    def outbox_loop():
        print(f"[Telemetry] Starting transmitter to Android app on port {ANDROID_SERVER_PORT}...")
        while state.is_running:
            try:
                # 1. Update local metrics
                title, process = get_active_window_info()
                state.active_window = title if title else "Idle System"
                state.active_process = process if process else "system"
                
                # 2. Compile JSON payload
                payload = {
                    "source": f"Windows Companion ({state.active_process})",
                    "title": state.active_window,
                    "detail": f"Intensity: {state.keypress_count} keys, {state.click_count} clicks"
                }
                
                # 3. HTTP Request
                url = f"http://{state.android_ip}:{ANDROID_SERVER_PORT}"
                data = json.dumps(payload).encode('utf-8')
                
                req = urllib.request.Request(
                    url, 
                    data=data, 
                    headers={'Content-Type': 'application/json'},
                    method='POST'
                )
                
                with urllib.request.urlopen(req, timeout=1.5) as response:
                    if response.status == 200:
                        state.telemetry_connected = True
                    else:
                        state.telemetry_connected = False
            except Exception:
                state.telemetry_connected = False
            
            time.sleep(2.0) # Rate limit telemetry posts
            
    t = threading.Thread(target=outbox_loop)
    t.daemon = True
    t.start()


# --- INCOMING SERVER (RECEIVES HABIT DATA FROM ANDROID) ---
class AndroidSyncHandler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        """Enable CORS pre-flight."""
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')
        self.end_headers()

    def do_POST(self):
        if self.path == "/sync_from_android":
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            
            try:
                payload = json.loads(post_data.decode('utf-8'))
                
                # Update twin client state variables
                state.current_streak = payload.get("currentStreak", 0)
                state.longest_streak = payload.get("longestStreak", 0)
                state.total_points = payload.get("totalPoints", 0)
                state.habits = payload.get("habits", [])
                state.last_sync_time = time.strftime("%H:%M:%S", time.localtime())
                
                # Standard success response
                self.send_response(200)
                self.send_header('Content-Type', 'application/json')
                self.send_header('Access-Control-Allow-Origin', '*')
                self.end_headers()
                
                response = {"status": "success", "message": "Companion updated!"}
                self.wfile.write(json.dumps(response).encode('utf-8'))
                
                # Optional: trigger desktop notification on successful synchronization
                print(f"[Sync Server] Synchronized Android habits successfully at {state.last_sync_time}!")
            except Exception as e:
                traceback.print_exc()
                self.send_response(400)
                self.end_headers()
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        # Suppress noisy HTTP console log dump
        pass

def start_incoming_server():
    """Spins up a lightweight background HTTP listener to absorb Android events."""
    def server_run():
        server_address = ('', COMPANION_SERVER_PORT)
        try:
            httpd = HTTPServer(server_address, AndroidSyncHandler)
            state.receiver_status = f"Active (Port {COMPANION_SERVER_PORT})"
            print(f"[Sync Server] Live & listening for Android sync signals on port {COMPANION_SERVER_PORT}...")
            while state.is_running:
                httpd.handle_request()
        except Exception as e:
            state.receiver_status = "Failed to Bind"
            print(f"[Sync Server] Server error: {e}")

    t = threading.Thread(target=server_run)
    t.daemon = True
    t.start()


# --- CYBERPUNK HUD INTERFACE ---
class CyberHUDApp:
    def __init__(self, root):
        self.root = root
        self.root.title("HELIOS // WINDOWS COMPANION TWIN")
        self.root.geometry("820x600")
        self.root.resizable(True, True)
        self.root.configure(bg="#0a0a14")
        
        # Configure styles
        self.setup_styles()
        
        # Build UI layout elements
        self.build_header()
        self.build_body()
        self.build_status_bar()
        
        # Start GUI polling loop to refresh real-time tracking metrics
        self.refresh_gui_loop()
        
    def setup_styles(self):
        style = ttk.Style()
        style.theme_use('clam')
        
        # Dark Cyber styling configurations
        style.configure("TFrame", background="#0a0a14")
        style.configure("TLabel", background="#0a0a14", foreground="#E2E2EC")
        
        style.configure("Cyber.TLabelframe", background="#0a0a14", foreground="#00E5FF", bordercolor="#1e1e32")
        style.configure("Cyber.TLabelframe.Label", font=("Consolas", 10, "bold"), foreground="#00E5FF", background="#0a0a14")
        
        style.configure("NeonButton.TButton", 
                        font=("Consolas", 9, "bold"), 
                        foreground="#00E5FF", 
                        background="#15152a", 
                        borderwidth=1,
                        bordercolor="#00E5FF",
                        focuscolor="none")
        style.map("NeonButton.TButton",
                  background=[('active', '#00E5FF')],
                  foreground=[('active', '#0a0a14')])
        
        style.configure("HighlightButton.TButton", 
                        font=("Consolas", 9, "bold"), 
                        foreground="#FF007F", 
                        background="#15152a", 
                        borderwidth=1,
                        bordercolor="#FF007F",
                        focuscolor="none")
        style.map("HighlightButton.TButton",
                  background=[('active', '#FF007F')],
                  foreground=[('active', '#0a0a14')])

    def build_header(self):
        header_frame = tk.Frame(self.root, bg="#0d0d1f", height=60, bd=0)
        header_frame.pack(fill="x", side="top")
        
        # Glow neon effect line
        glow_bar = tk.Frame(self.root, bg="#00E5FF", height=2)
        glow_bar.pack(fill="x", side="top")
        
        title_lbl = tk.Label(header_frame, 
                             text="⚡ HELIOS // WINDOWS TWIN SYSTEM HUB", 
                             font=("Consolas", 14, "bold"), 
                             fg="#00E5FF", 
                             bg="#0d0d1f")
        title_lbl.pack(side="left", padx=20, pady=12)
        
        tagline_lbl = tk.Label(header_frame, 
                               text="V1.1 COGNITIVE TELEMETRY & SYNCHRONIZER", 
                               font=("Consolas", 8, "bold"), 
                               fg="#FF007F", 
                               bg="#0d0d1f")
        tagline_lbl.pack(side="right", padx=20, pady=16)

    def build_body(self):
        body_container = ttk.Frame(self.root, padding=15)
        body_container.pack(fill="both", expand=True)
        
        # Grid layout with 2 columns (Telemetry & Synced Android Habits Overlay)
        body_container.columnconfigure(0, weight=4, uniform="group")
        body_container.columnconfigure(1, weight=5, uniform="group")
        body_container.rowconfigure(0, weight=1)
        
        # --- LEFT PANEL: TELEMETRY STREAM & SETTINGS ---
        left_panel = ttk.Frame(body_container)
        left_panel.grid(row=0, column=0, sticky="nsew", padx=(0, 10))
        
        # 1. Config Block
        config_box = ttk.LabelFrame(left_panel, text=" CONNECTION CONFIG ", style="Cyber.TLabelframe")
        config_box.pack(fill="x", pady=(0, 12))
        
        ip_lbl = tk.Label(config_box, text="Twin Android IP Target:", font=("Consolas", 9), fg="#6E6E80", bg="#0a0a14")
        ip_lbl.pack(anchor="w", padx=12, pady=(8, 2))
        
        ip_input_frame = tk.Frame(config_box, bg="#0a0a14")
        ip_input_frame.pack(fill="x", padx=12, pady=(0, 12))
        
        self.ip_val = tk.StringVar(value=state.android_ip)
        self.ip_entry = tk.Entry(ip_input_frame, 
                                 textvariable=self.ip_val, 
                                 font=("Consolas", 10), 
                                 fg="#E2E2EC", 
                                 bg="#15152a", 
                                 insertbackground="#00E5FF",
                                 bd=1, 
                                 relief="solid")
        self.ip_entry.pack(side="left", fill="x", expand=True, ipady=3)
        
        save_btn = ttk.Button(ip_input_frame, 
                              text="CONNECT", 
                              style="NeonButton.TButton", 
                              width=10, 
                              command=self.save_ip_config)
        save_btn.pack(side="right", padx=(8, 0))
        
        # 2. Local IP helper list
        ip_list_lbl = tk.Label(config_box, 
                               text="This PC's Local IP(s) for Android Setup:\n" + ", ".join(get_local_ips()[1:]),
                               font=("Consolas", 8, "italic"),
                               fg="#8A2BE2",
                               bg="#0a0a14",
                               justify="left")
        ip_list_lbl.pack(anchor="w", padx=12, pady=(0, 10))

        # 3. Telemetry Stream Display Box
        stream_box = ttk.LabelFrame(left_panel, text=" DESKTOP MONITORING (OUTGOING) ", style="Cyber.TLabelframe")
        stream_box.pack(fill="both", expand=True, pady=(0, 12))
        
        self.lbl_act_proc = tk.Label(stream_box, text="ACTIVE PROCESS: explorer.exe", font=("Consolas", 9, "bold"), fg="#FF007F", bg="#0a0a14")
        self.lbl_act_proc.pack(anchor="w", padx=12, pady=(10, 2))
        
        self.lbl_act_title = tk.Label(stream_box, text="ACTIVE WINDOW:\nDesktop Workspace", font=("Consolas", 9), fg="#E2E2EC", bg="#0a0a14", justify="left", wraplength=300)
        self.lbl_act_title.pack(anchor="w", padx=12, pady=(0, 12))
        
        metrics_bar = tk.Frame(stream_box, bg="#15152a", height=1)
        metrics_bar.pack(fill="x", padx=12, pady=5)
        
        self.lbl_clicks = tk.Label(stream_box, text="MOUSE CLICKS: 0", font=("Consolas", 9), fg="#00E5FF", bg="#0a0a14")
        self.lbl_clicks.pack(anchor="w", padx=12, pady=3)
        
        self.lbl_keys = tk.Label(stream_box, text="KEYBOARD PRESSES: 0", font=("Consolas", 9), fg="#00E5FF", bg="#0a0a14")
        self.lbl_keys.pack(anchor="w", padx=12, pady=3)
        
        self.lbl_session = tk.Label(stream_box, text="SESSION TIME: 0s", font=("Consolas", 9), fg="#6E6E80", bg="#0a0a14")
        self.lbl_session.pack(anchor="w", padx=12, pady=3)

        # 4. Simulation actions for testing
        test_box = ttk.LabelFrame(left_panel, text=" EMULATORS & SIMULATORS ", style="Cyber.TLabelframe")
        test_box.pack(fill="x")
        
        btn_frame = tk.Frame(test_box, bg="#0a0a14")
        btn_frame.pack(fill="x", padx=12, pady=12)
        
        sim_win_btn = ttk.Button(btn_frame, text="MOCK WINDOW", style="NeonButton.TButton", command=self.simulate_window_shift)
        sim_win_btn.pack(side="left", fill="x", expand=True, padx=(0, 4))
        
        sim_click_btn = ttk.Button(btn_frame, text="MOCK CLICK", style="NeonButton.TButton", command=self.simulate_click_event)
        sim_click_btn.pack(side="left", fill="x", expand=True, padx=(4, 0))

        # --- RIGHT PANEL: SYNCHRONIZED ANDROID HABITS OVERLAY ---
        right_panel = ttk.Frame(body_container)
        right_panel.grid(row=0, column=1, sticky="nsew", padx=(10, 0))
        
        habits_box = ttk.LabelFrame(right_panel, text=" ANDROID APP TELEMETRY (INCOMING) ", style="Cyber.TLabelframe")
        habits_box.pack(fill="both", expand=True)
        
        # User details card sync
        stats_frame = tk.Frame(habits_box, bg="#0d0d1f", bd=1, relief="solid")
        stats_frame.pack(fill="x", padx=12, pady=12)
        
        self.lbl_stats_streak = tk.Label(stats_frame, text="STREAK: 0 days", font=("Consolas", 10, "bold"), fg="#FF007F", bg="#0d0d1f")
        self.lbl_stats_streak.grid(row=0, column=0, padx=12, pady=8, sticky="w")
        
        self.lbl_stats_points = tk.Label(stats_frame, text="SCORE: 0 pts", font=("Consolas", 10, "bold"), fg="#00E5FF", bg="#0d0d1f")
        self.lbl_stats_points.grid(row=0, column=1, padx=12, pady=8, sticky="w")
        
        stats_frame.columnconfigure(0, weight=1)
        stats_frame.columnconfigure(1, weight=1)

        # Scrollable habits canvas list
        self.habits_list_frame = tk.Frame(habits_box, bg="#0a0a14")
        self.habits_list_frame.pack(fill="both", expand=True, padx=12, pady=(0, 12))
        
        self.habits_canvas = tk.Canvas(self.habits_list_frame, bg="#0a0a14", bd=0, highlightthickness=0)
        self.scrollbar = ttk.Scrollbar(self.habits_list_frame, orient="vertical", command=self.habits_canvas.yview)
        
        self.scroll_scrollable_frame = tk.Frame(self.habits_canvas, bg="#0a0a14")
        self.scroll_scrollable_frame.bind(
            "<Configure>",
            lambda e: self.habits_canvas.configure(scrollregion=self.habits_canvas.bbox("all"))
        )
        
        self.habits_canvas.create_window((0, 0), window=self.scroll_scrollable_frame, anchor="nw")
        self.habits_canvas.configure(yscrollcommand=self.scrollbar.set)
        
        self.habits_canvas.pack(side="left", fill="both", expand=True)
        self.scrollbar.pack(side="right", fill="y")
        
        # Initial empty overlay state
        self.empty_lbl = tk.Label(self.scroll_scrollable_frame, 
                                  text="No habit data received yet.\n\nTrigger a check-in or press 'Sync Now'\non the Android application to synchronize stats!", 
                                  font=("Consolas", 9, "italic"), 
                                  fg="#6E6E80", 
                                  bg="#0a0a14",
                                  justify="center")
        self.empty_lbl.pack(fill="both", expand=True, pady=80)

    def build_status_bar(self):
        status_frame = tk.Frame(self.root, bg="#0d0d1f", height=24)
        status_frame.pack(fill="x", side="bottom")
        
        # Status details indicators
        self.status_out = tk.Label(status_frame, text="Android Server: Connecting...", font=("Consolas", 8, "bold"), fg="#FF007F", bg="#0d0d1f")
        self.status_out.pack(side="left", padx=15)
        
        self.status_in = tk.Label(status_frame, text="Sync Receiver: Ready", font=("Consolas", 8, "bold"), fg="#00E5FF", bg="#0d0d1f")
        self.status_in.pack(side="left", padx=15)
        
        self.status_sync = tk.Label(status_frame, text="Last Sync: Never", font=("Consolas", 8), fg="#6E6E80", bg="#0d0d1f")
        self.status_sync.pack(side="right", padx=15)

    def save_ip_config(self):
        entered_ip = self.ip_val.get().strip()
        if entered_ip:
            state.android_ip = entered_ip
            messagebox.showinfo("Configured", f"Target Android IP set to: {entered_ip}\nTelemetry loops updated.")

    def simulate_window_shift(self):
        state.active_process = "chrome.exe"
        state.active_window = "Google Chrome - Deep Learning Tutorial & AI Systems"
        print("[Simulated] Simulating focus shift to Google Chrome.")

    def simulate_click_event(self):
        state.click_count += 5
        state.keypress_count += 12
        print("[Simulated] Simulated input activity burst.")

    def update_habits_overlay(self):
        """Re-renders the synchronized Android active habit list widget."""
        # Clear previous UI list widgets
        for widget in self.scroll_scrollable_frame.winfo_children():
            widget.destroy()
            
        if not state.habits:
            self.empty_lbl = tk.Label(self.scroll_scrollable_frame, 
                                      text="No habit data received yet.\n\nTrigger a check-in or press 'Sync Now'\non the Android application to synchronize stats!", 
                                      font=("Consolas", 9, "italic"), 
                                      fg="#6E6E80", 
                                      bg="#0a0a14")
            self.empty_lbl.pack(fill="both", expand=True, pady=80)
            return

        # Render each dynamic habit
        for h in state.habits:
            habit_id = h.get("id", 0)
            title = h.get("title", "Habit")
            desc = h.get("description", "")
            emoji = h.get("categoryIcon", "🎯")
            curr_val = h.get("currentValue", 0)
            tgt_val = h.get("targetValue", 1)
            unit = h.get("unit", "")
            completed = h.get("isCompleted", False)
            streak = h.get("streakCount", 0)
            
            card = tk.Frame(self.scroll_scrollable_frame, bg="#15152a", bd=1, relief="solid", highlightthickness=0)
            card.pack(fill="x", pady=5, padx=2)
            
            # Left Icon emoji block
            emoji_lbl = tk.Label(card, text=emoji, font=("Segoe UI Emoji", 14), bg="#15152a")
            emoji_lbl.pack(side="left", padx=12)
            
            # Texts Block
            texts_frame = tk.Frame(card, bg="#15152a")
            texts_frame.pack(side="left", fill="both", expand=True, pady=8)
            
            header_frame = tk.Frame(texts_frame, bg="#15152a")
            header_frame.pack(fill="x")
            
            title_lbl = tk.Label(header_frame, text=title, font=("Consolas", 10, "bold"), fg="#E2E2EC", bg="#15152a")
            title_lbl.pack(side="left")
            
            if completed:
                badge = tk.Label(header_frame, text=" COMPLETED ", font=("Consolas", 7, "bold"), fg="#0a0a14", bg="#00E5FF")
                badge.pack(side="left", padx=8)
            
            desc_lbl = tk.Label(texts_frame, text=desc, font=("Consolas", 8), fg="#6E6E80", bg="#15152a", wraplength=260, justify="left")
            desc_lbl.pack(anchor="w", pady=(2, 4))
            
            # Custom styled modern neon progress indicators
            progress_frame = tk.Frame(texts_frame, bg="#15152a")
            progress_frame.pack(fill="x")
            
            # Progress calculation
            pct = min(1.0, float(curr_val) / float(tgt_val)) if tgt_val > 0 else 0.0
            
            canvas_progress = tk.Canvas(progress_frame, bg="#0a0a14", height=4, bd=0, highlightthickness=0)
            canvas_progress.pack(side="left", fill="x", expand=True, pady=5)
            
            # Draw empty progress bar
            def draw_prog(canvas=canvas_progress, fill_pct=pct, is_comp=completed):
                canvas.update()
                w = canvas.winfo_width()
                canvas.create_rectangle(0, 0, w, 4, fill="#0d0d1f", outline="")
                fill_color = "#00E5FF" if is_comp else "#FF007F"
                canvas.create_rectangle(0, 0, int(w * fill_pct), 4, fill=fill_color, outline="")
                
            canvas_progress.bind("<Configure>", lambda e, c=canvas_progress, p=pct, co=completed: draw_prog(c, p, co))
            
            # Numeric tally progress readout
            tally_lbl = tk.Label(progress_frame, text=f" {curr_val}/{tgt_val} {unit}", font=("Consolas", 8, "bold"), fg="#E2E2EC", bg="#15152a")
            tally_lbl.pack(side="right", padx=(8, 0))
            
            # Streak badge on right side of card
            streak_frame = tk.Frame(card, bg="#15152a")
            streak_frame.pack(side="right", padx=15)
            
            streak_lbl = tk.Label(streak_frame, text=f"🔥 {streak}d", font=("Consolas", 10, "bold"), fg="#8A2BE2", bg="#15152a")
            streak_lbl.pack()

    def refresh_gui_loop(self):
        """Regularly pulls state values into Tkinter labels to preserve responsive graphics."""
        try:
            # 1. Telemetry panels updates
            self.lbl_act_proc.config(text=f"ACTIVE PROCESS: {state.active_process}")
            self.lbl_act_title.config(text=f"ACTIVE WINDOW:\n{state.active_window}")
            self.lbl_clicks.config(text=f"MOUSE CLICKS: {state.click_count}")
            self.lbl_keys.config(text=f"KEYBOARD PRESSES: {state.keypress_count}")
            
            elapsed = int(time.time() - state.session_start_time)
            mins, secs = divmod(elapsed, 60)
            self.lbl_session.config(text=f"SESSION TIME: {mins:02d}m {secs:02d}s")
            
            # 2. Connection indicators
            if state.telemetry_connected:
                self.status_out.config(text="Android Server: Connected (Port 9090)", fg="#00E5FF")
            else:
                self.status_out.config(text="Android Server: Offline / Scanning", fg="#FF007F")
                
            self.status_in.config(text=f"Sync Receiver: {state.receiver_status}", fg="#00E5FF")
            self.status_sync.config(text=f"Last Sync: {state.last_sync_time}")
            
            # 3. User score tallies updates
            self.lbl_stats_streak.config(text=f"STREAK: {state.current_streak} days")
            self.lbl_stats_points.config(text=f"SCORE: {state.total_points} pts")
            
            # Re-render habit widget array if sync occurred
            if state.last_sync_time != "Never" and not hasattr(self, 'last_gui_sync_check') or getattr(self, 'last_gui_sync_check', "") != state.last_sync_time:
                self.last_gui_sync_check = state.last_sync_time
                self.update_habits_overlay()
                
        except Exception as e:
            print(f"[GUI Error] {e}")
            
        # Poll again in 500ms
        self.root.after(500, self.refresh_gui_loop)


def main():
    print("=====================================================")
    print(" HELIOS HABIT TRACKER - DESKTOP TWIN COMPANION APP")
    print("=====================================================")
    
    # 1. Fire up background input monitors
    start_input_listeners()
    
    # 2. Spin up outgoing telemetry transmitter daemon
    start_telemetry_outbox()
    
    # 3. Launch sync receiver HTTP Server on Port 9092
    start_incoming_server()
    
    # 4. Launch main graphics loop
    root = tk.Tk()
    app = CyberHUDApp(root)
    
    try:
        root.mainloop()
    except KeyboardInterrupt:
        pass
    finally:
        state.is_running = False
        print("\n[System] Shutting down twin components. Stay mindful!")

if __name__ == "__main__":
    main()
