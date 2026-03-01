# FrostyFunds Development Guide

Technical documentation for building, running, and contributing to FrostyFunds.

## Building

### Prerequisites

- JDK 11 or higher
- Windows operating system (for mingwX64 target)

### Commands

Build the project:
```bash
./gradlew build
```

Clean and rebuild:
```bash
./gradlew clean build
```

Run tests:
```bash
./gradlew nativeTest
```

## Running from Source

**Important:** Do not run via Gradle (`./gradlew runReleaseExecutableNative`) as it doesn't properly connect stdin, causing `ReadAfterEOFException`.

Run the built executable directly:
```
build\bin\native\releaseExecutable\FrostyFunds.exe
```

## Architecture

### Main.kt

Interactive CLI entry point with two-pass scanning workflow:
- Prompts user for current money/ticket amounts → first scan
- Prompts for updated amounts after in-game transaction → second scan (filters candidates)
- Prompts for desired amounts → writes to discovered addresses
- Uses `readln()` for all input

### WindowsMemory.kt

`ProcessMemory` object providing Windows API wrappers:
- Process discovery via window title (`FindWindowA`, `GetWindowThreadProcessId`)
- Memory reading/writing (int, float, byte arrays)
- Memory region enumeration (`VirtualQueryEx`)
- Value scanning with 1MB chunked reading and 4-byte alignment
- Multi-address batch operations

## Technical Details

| Aspect | Detail |
|--------|--------|
| Language | Kotlin/Native 2.2.20 |
| Platform | Windows (mingwX64) |
| Memory Operations | Windows API (`ReadProcessMemory`, `WriteProcessMemory`, `VirtualQueryEx`) |
| Scanning | 1MB chunks with 4-byte alignment for integer scans |
| Max Address | 0x7FFFFFFFFFFF (64-bit Windows user-mode limit) |

### Windows API Integration

Uses `@OptIn(ExperimentalForeignApi::class)` with `platform.windows`:
- `OpenProcess` with `PROCESS_VM_READ | PROCESS_VM_WRITE | PROCESS_VM_OPERATION`
- `ReadProcessMemory` / `WriteProcessMemory`
- `VirtualQueryEx` - scans MEM_COMMIT regions with readable protection flags

## Project Structure

```
FrostyFunds/
├── src/nativeMain/kotlin/
│   ├── Main.kt           # CLI entry point
│   ├── WindowsMemory.kt  # Windows API wrappers
│   └── ProgressBar.kt    # Terminal progress display
├── build.gradle.kts      # Build configuration
├── README.md             # User documentation
└── DEVELOPMENT.md        # This file
```

## Platform Configuration

- **Target Platform**: Automatically detects host OS in `build.gradle.kts`
- **Primary Target**: Windows (mingwX64)
- **Binary Type**: Executable with entry point `main()`

## Dependencies

- Kotlin 2.2.20
- kotlinx-serialization-json 1.8.0 (declared but not currently used)
- Windows API (platform.windows)

## License

MIT License - see [LICENSE](LICENSE) file for details.
