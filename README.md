# AppControlX

A powerful Android application for controlling app behavior, battery optimization, and system management — using Root or Shizuku.

## Features

### App Control
- **Freeze/Unfreeze** - Disable apps without uninstalling (keeps data intact)
- **Uninstall** - Remove apps for current user while preserving data
- **Force Stop** - Immediately terminate running applications
- **Clear Cache/Data** - Clean app storage with size preview
- **Batch Operations** - Apply actions to multiple apps with progress tracking

### Battery Optimization
- **Restrict Background** - Block apps from running in background
- **Allow Background** - Permit background execution
- **Real-time Status** - View current background restriction status per app

### Tools
- **Activity Launcher** - Launch hidden activities from any app
- **Extra Dim** - Reduce screen brightness below minimum
- **Notification Log/History** - Access notification records
- **Battery Usage** - View app battery consumption
- **Power Mode** - Quick access to power settings
- **Device Info & Diagnostic** - System information

### UI/UX
- **Material 3 Design** - Modern, clean interface
- **Dark Mode** - Full dark theme support
- **Multi-language** - English & Indonesian
- **Search & Filter** - Quick app discovery
- **Action Logs** - History of all operations

## Platform Support

| Platform | Version | Support |
|----------|---------|---------|
| Android Stock | 10 - 15 | Full |
| MIUI/HyperOS | 12+ | Full |
| Custom ROM | Android 10+ | Full |

## Requirements

- Android 10+ (API 29)
- One of the following:
  - **Root access** (Magisk recommended)
  - **Shizuku** installed and activated

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

## Architecture

MVVM architecture with Hilt dependency injection.

```
com.appcontrolx/
├── di/                 # Hilt modules
├── executor/           # Command execution (Root/Shizuku)
├── model/              # Data classes
├── rollback/           # Action logs & rollback
├── service/            # Business logic
├── ui/                 # Activities, Fragments, Adapters
└── utils/              # Helpers & validators
```

### Key Components

| Component | Description |
|-----------|-------------|
| `PermissionBridge` | Detects execution mode (Root/Shizuku/None) |
| `RootExecutor` | Executes commands via libsu with security validation |
| `BatteryPolicyManager` | Manages appops and battery settings |
| `RollbackManager` | Action logs and state snapshots |
| `SafetyValidator` | Prevents actions on critical system apps |

## Tech Stack

- **Language**: Kotlin 1.9
- **Min SDK**: 29 (Android 10)
- **Target SDK**: 34 (Android 14)
- **Architecture**: MVVM + Hilt
- **UI**: Material 3, ViewBinding
- **Async**: Coroutines + Flow
- **Root**: [libsu](https://github.com/topjohnwu/libsu)
- **Shizuku**: [Shizuku-API](https://github.com/RikkaApps/Shizuku-API)

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
appops set <package> RUN_IN_BACKGROUND ignore    # Restrict
appops set <package> RUN_IN_BACKGROUND allow     # Allow
appops set <package> WAKE_LOCK ignore            # Disable wake lock
```

## Known Issues

| Issue | Status | Impact |
|-------|--------|--------|
| App running detection may fail | Won't Fix (Lazy) | Sorting by running status doesn't work reliably |

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push and open a Pull Request

## License

GPL-3.0 License - see [LICENSE](LICENSE)

## Acknowledgments

- [libsu](https://github.com/topjohnwu/libsu) - Root shell library
- [Shizuku](https://github.com/RikkaApps/Shizuku) - Elevated API access