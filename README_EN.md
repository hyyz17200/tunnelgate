# TunnelGate

TunnelGate is a lightweight **LSPosed module + settings app** for **rooted Android devices**. It automatically enables or disables a proxy / tunnel app based on the current network type and Wi‑Fi SSID policy.

The project started with V2RayTun support, but it still keeps a **generic shell backend**, so it can be extended to other proxy apps later.

---

## Features

- Hooks only `System Framework (android)`.
- Uses `ConnectivityManager.registerDefaultNetworkCallback()` to monitor default network changes.
- Separate actions for:
  - Cellular
  - Blacklisted Wi‑Fi
  - Non-blacklisted Wi‑Fi
  - No network / unvalidated network
- Global automation master switch.
- Configurable boot delay and debounce time.
- Two control backends:
  - **Generic shell commands**
  - **V2RayTun Tasker plugin** (silent, recommended)
- Built-in GUI diagnostics:
  - SU status check
  - LSPosed / system hook online check
  - Simple log window

---

## Why this project exists

Automation apps such as MacroDroid and Tasker are powerful, but many users only need one thing: **switch proxy behavior automatically according to the current network**.

TunnelGate is designed to be:

- lightweight
- direct
- easy to debug
- independent from a full automation platform

---

## How it works

### 1. System side

The LSPosed module hooks `SystemServer` inside the `android` process. After system services are ready, it registers a network callback and listens for default network changes.

### 2. Policy evaluation

Whenever the network changes, the module reads the current configuration and decides whether it should:

- `start`
- `stop`
- or keep the current state

### 3. App side execution

The system-side hook does not directly launch the target proxy app. Instead, it sends an explicit broadcast to TunnelGate's own `ControlReceiver`.

`ControlReceiver` then executes the selected backend:

- shell commands
- V2RayTun Tasker plugin broadcast

This keeps the hook logic small while making the execution backend replaceable.

---

## Control backends

## A. Generic shell commands

This backend works with any proxy app, as long as the app can be controlled by shell / `am` / `cmd` / broadcast or similar mechanisms.

You can edit two commands in the GUI:

- Start command
- Stop command

Example (legacy V2RayTun deeplink compatibility only):

```bash
am start -W -a android.intent.action.VIEW -d v2raytun://control/start -p com.v2raytun.android
am start -W -a android.intent.action.VIEW -d v2raytun://control/stop -p com.v2raytun.android
```

> Note: some deeplinks bring the target app to the foreground, so this is not always suitable for daily use.

## B. V2RayTun Tasker plugin

This is the recommended backend.

V2RayTun exposes a Tasker / Locale plugin interface. TunnelGate can:

- open `TaskerActivity` once to capture the action configuration
- later send `FIRE_SETTING` silently to `TaskerReceiver`

This avoids the deeplink activity from being brought to the foreground.

---

## Requirements

- Android 8.0+
- Root access
- A working **LSPosed** installation
- `su` provided by KSU / Magisk is recommended

---

## Installation

1. Build and install the APK with Android Studio.
2. Open **LSPosed** and enable the TunnelGate module.
3. Scope it only to:
   - `System Framework (android)`
4. Reboot the device.
5. Open TunnelGate.
6. Check the diagnostics section and confirm:
   - SU is granted
   - LSPosed system hook is online

---

## Usage

### General settings

The GUI lets you configure:

- whether automation is enabled
- control backend
- actions for cellular / blacklisted Wi‑Fi / non-blacklisted Wi‑Fi / no network
- whether Wi‑Fi / cellular rules should only apply on `VALIDATED` networks
- SSID blacklist
- debounce time
- boot delay time

### V2RayTun Tasker setup

1. Switch the backend to **V2RayTun Tasker plugin**.
2. Tap **Capture Start**.
3. Save the “start” action in the V2RayTun Tasker page.
4. Return to TunnelGate.
5. Tap **Capture Stop**.
6. Save the “stop” action in V2RayTun.
7. Tap **Save**.

After that, automatic switching will run silently without bringing the V2RayTun main UI to the foreground.

---

## Diagnostics

TunnelGate checks two important pieces of runtime state:

### SU status

It runs `su -c id` to verify that root access is already granted.

### LSPosed status

The GUI sends a status query broadcast to the system-side hook.

If the module is active, the hook reports back:

- whether it is online
- whether monitoring has started
- the latest status message

If no response is received, the usual reasons are:

- module not enabled
- wrong LSPosed scope
- boot sequence not fully completed yet

---

## Logs

A simple built-in log window is available at the bottom of the GUI.

It collects messages from:

- system hook events
- app-side execution results
- Tasker action execution summaries
- error messages

This makes debugging easier than relying on `logcat` alone.


---

## Known limitations

- The silent Tasker backend is currently tailored specifically for **V2RayTun**.
- If V2RayTun changes its Tasker component names or bundle structure, the code may need updates.
- LSPosed online detection depends on a response from the system-side hook, so it may take a moment right after boot.
- The reliability of the generic shell backend depends on whether the target proxy app exposes a stable control interface.
