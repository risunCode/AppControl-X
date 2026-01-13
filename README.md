# AppControlX

A powerful Android application for controlling app behavior, system monitoring, and device management — using Root or Shizuku.

## What's New in v2.0.0 🎉

Complete rewrite with modern architecture and new features:
- **Dashboard** - Real-time system monitoring (CPU, RAM, Battery, Storage, Network, GPU)
- **Setup Wizard** - Guided first-time setup with mode selection
- **Mode Loss Detection** - Automatic detection when Root/Shizuku access is lost
- **Display Refresh Rate Control** - Set min/max refresh rate
- **Clean Architecture** - MVVM + Hilt DI with organized package structure

## Features

### 🏠 Dashboard
Real-time system monitoring with DevCheck-style cards:
- **CPU** - Usage percentage, temperature, core count
- **Battery** - Level, charging status, temperature, health
- **RAM** - Used, free, total memory
- **Storage** - Internal storage usage
- **Network** - Connection type, status, WiFi SSID
- **Display** - Resolution, refresh rate
- **GPU** - Model and vendor (requires root)
- **Device** - Brand, model, Android version, uptime

### 📱 App Control
- **Freeze/Unfreeze** - Disable apps without uninstalling (keeps data intact)
- **Uninstall** - Remove apps for current user while preserving data
- **Force Stop** - Immediately terminate running applications
- **Clear Cache/Data** - Clean app storage with size preview
- **Batch Operations** - Apply actions to multiple apps with progress tracking

### 🔋 Battery Optimization
- **Restrict Background** - Block apps from running in background
- **Allow Background** - Permit background execution
- **Real-time Status** - View current background restriction status per app
- **Multiple AppOps** - RUN_IN_BACKGROUND, RUN_ANY_IN_BACKGROUND, WAKE_LOCK, BOOT_COMPLETED

### 🖥️ Display Settings
- **Refresh Rate Control** - Set minimum and maximum refresh rate
- **Reset to Default** - Restore system default settings
- *Requires Root or Shizuku*

### 📜 Action Logs & Rollback
- **Action History** - Track all operations with timestamps
- **Rollback** - Reverse battery actions (Freeze/Unfreeze, Restrict/Allow)
- **State Snapshots** - Automatic backup before actions

### 🎨 UI/UX
- **Material 3 Design** - Modern interface with dynamic colors (Android 12+)
- **Dark Mode** - Full dark theme support
- **Bottom Navigation** - Dashboard, Apps, Settings
- **Search & Filter** - Quick app discovery by name, package, or status

## Screenshots

| Setup | Main | App Info | Batch | Activity Launcher |
|:-----:|:----:|:--------:|:-----:|:-----------------:|
| ![Setup 1](https://github.com/user-attachments/assets/b54ea7eb-d2cb-452e-8914-435f0126fd1c) | ![Main Apps](https://github.com/user-attachments/assets/2f45871b-f2b0-4b1b-aa01-433516caa608) | ![App Info](https://github.com/user-attachments/assets/1085fad6-011e-4ab6-9e48-a7a1310319da) | ![Batch](https://github.com/user-attachments/assets/88001520-721a-436a-9dfc-c372ebe03790) | ![Activity Launcher](https://github.com/user-attachments/assets/1e634bb6-4d75-4072-87f8-ead4d4eac504) |

| Tools | Settings | About | Blocklist |
|:-----:|:--------:|:-----:|:---------:|
| ![Tools](https://github.com/user-attachments/assets/cc4cac8c-cc98-417c-b46c-c338aa017383) | ![Settings](https://github.com/user-attachments/assets/1bd61725-a9e1-40d3-8725-6b709cf0abdf) | ![About](https://github.com/user-attachments/assets/c5074985-cb74-4097-b051-6df73d5ce89e) | ![Blocklist](https://github.com/user-attachments/assets/71e4b960-6ce5-4805-8f44-e86d53eb675a) |

## Platform Support

- Android 10+ (API 29) - Stock, AOSP, Custom ROMs

### Protected System Apps
SafetyValidator blocks critical system packages from being disabled/frozen to prevent bricking. Covers AOSP, Google, Xiaomi, Samsung, OPPO, Vivo, Huawei, OnePlus, Nothing, ASUS, Sony, Motorola, and more.

## Requirements

- Android 10+ (API 29)
- One of the following:
  - **Root access** (Magisk/KernelSU recommended)
  - **Shizuku** installed and activated (full features, no root needed)

### Features Without Root/Shizuku
These features work in View-Only mode:
- **Dashboard** - System monitoring (limited GPU info)
- **App List** - Browse installed apps
- **Settings** - Theme and preferences

## Installation

### From Release
1. Download the latest APK from [Releases](https://github.com/risunCode/AppControl-X/releases)
2. Install on your device
3. Complete the setup wizard

### Build from Source
```bash
git clone https://github.com/risunCode/AppControl-X.git
cd AppControl-X
./gradlew assembleDebug
```
 

### Key Components

| Component | Description |
|-----------|-------------|
| `PermissionBridge` | Detects execution mode (Root/Shizuku/None) |
| `RootExecutor` | Executes commands via libsu with security validation |
| `ShizukuExecutor` | Executes commands via Shizuku UserService |
| `SystemMonitor` | Real-time system info (CPU, RAM, Battery, etc.) |
| `AppScanner` | Accurate app detection using dumpsys + PackageManager |
| `AppControlManager` | App actions (freeze, uninstall, force stop, etc.) |
| `BatteryManager` | Background restriction via appops |
| `ActionLogger` | Action history and rollback |
| `SafetyValidator` | Prevents actions on critical system apps |
| `ModeWatcher` | Detects Root/Shizuku access loss |

## Tech Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| Kotlin | 1.9 | Primary language |
| Min SDK | 29 (Android 10) | Minimum supported |
| Target SDK | 34 (Android 14) | Target version |
aApps/Shizuku) - Elevated API access
- Built with [Kiro](https://kiro.dev) AI Assistant
 rate
settings delete system min_refresh_rate       # Reset to default
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for full history.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push and open a Pull Request

## License

GPL-3.0 License - see [LICENSE](LICENSE)

## Credits

Made with ❤️ by [risunCode](https://github.com/risunCode)

### Acknowledgments

- [libsu](https://github.com/topjohnwu/libsu) - Root shell library
- [Shizuku](https://github.com/RikkBOOT_COMPLETED ignore      # Disable boot receiver
```

### Display Settings
```bash
settings put system min_refresh_rate <hz>     # Set min refresh rate
settings put system peak_refresh_rate <hz>    # Set max refreshe <package>                    # Unfreeze
pm uninstall -k --user 0 <package>    # Uninstall
am force-stop <package>                # Force stop
pm clear --cache-only <package>        # Clear cache
pm clear <package>                     # Clear data
```lin | 1.9 | Primary language |
| Min SDK | 29 (Android 10) | Minimum supported |
| Target SDK | 34 (Android 14) | Target version |
| Hilt | 2.50 | Dependency Injection |
| libsu | 5.2.2 | Root access |
| Shizuku | 13.1.5 | Non-root privileged access |
| Navigation | 2.7.6 | Fragment navigation |
| Coroutines | 1.7.3 | Async operations |
| Material 3 | 1.11.0 | UI components |

## Commands Reference

### App Control
```bash
pm disable-user --user 0 <package>    # Freeze
pm enable <package>                    # Unfreeze
pm uninstall -k --user 0 <package>    # Uninstall
am force-stop <package>                # Force stop
pm clear --cache-only <package>        # Clear cache
pm clear <package>                     # Clear data
```

### Battery Control
```bash
appops set <package> RUN_IN_BACKGROUND ignore       # Restrict
appops set <package> RUN_ANY_IN_BACKGROUND ignore   # Restrict (extended)
appops set <package> WAKE_LOCK ignore               # Disable wake lock
appops set <package> BOOT_COMPLETED ignore          # Disable boot receiver
```

### Display Settings
```bash
settings put system min_refresh_rate <hz>     # Set min refresh rate
settings put system peak_refresh_rate <hz>    # Set max refresh rate
settings delete system min_refresh_rate       # Reset to default
```

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for full history.

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push and open a Pull Request

## License

GPL-3.0 License - see [LICENSE](LICENSE)

## Credits

Made with ❤️ by [risunCode](https://github.com/risunCode)

### Acknowledgments

- [libsu](https://github.com/topjohnwu/libsu) - Root shell library
- [Shizuku](https://github.com/RikkaApps/Shizuku) - Elevated API access
- Built with [Kiro](https://kiro.dev) AI Assistant
