# FrostyFunds

A Kotlin/Native memory scanning tool for modifying money values in the "[Sledding Game Demo](https://store.steampowered.com/app/3438850/Sledding_Game/)". This tool demonstrates process memory manipulation using Windows API calls through Kotlin's C interop.

> **Note:** The money in Sledding Game Demo is only used for purchasing cosmetic items. This tool is for educational purposes and personal experimentation.

Make sure to Wishlist the game on Steam!

## Features

- 🔍 Interactive memory scanning with two-pass filtering
- 💰 Automatic memory value modification
- 🎯 User-friendly CLI interface
- 🪟 Native Windows executable with no dependencies

## How It Works

1. **First Scan**: Enter your current money amount, and the tool scans the game's memory for matching values
2. **Second Scan**: Buy or sell something in-game, then enter the new amount to narrow down the exact memory addresses
3. **Modify**: Enter your desired money amount, and the tool writes it to the discovered addresses
4. **Update**: The in-game display updates after you buy or sell something

## Building

### Prerequisites

- JDK 11 or higher
- Windows operating system (for mingwX64 target)

### Build Commands

Build the project:
```bash
./gradlew build
```

Clean and rebuild:
```bash
./gradlew clean build
```

## Running

**Important:** Do not run via Gradle (`./gradlew run`) as it doesn't properly connect stdin. Instead, run the built executable directly.

### Executable Location

After building, find the executable at:
```
build\bin\native\releaseExecutable\FrostyFunds.exe
```

### Usage

1. Start the Sledding Game Demo
2. Run `FrostyFunds.exe` (as Administrator if needed)
3. Follow the interactive prompts:
   - Enter your current money amount
   - Buy/sell something in-game
   - Enter your new money amount
   - Enter your desired money amount
4. Buy or sell something in-game to see the updated value

## Technical Details

- **Language**: Kotlin/Native 2.2.20
- **Platform**: Windows (mingwX64)
- **Memory Operations**: Uses Windows API (`ReadProcessMemory`, `WriteProcessMemory`, `VirtualQueryEx`)
- **Scanning**: Chunks memory into 1MB blocks with 4-byte alignment for integer scans

## Architecture

- `Main.kt` - Interactive CLI entry point with two-pass scanning workflow
- `WindowsMemory.kt` - `ProcessMemory` object providing Windows API wrappers for process discovery, memory operations, and value scanning

## License

MIT License - see [LICENSE](LICENSE) file for details.

## Disclaimer

This tool is for educational purposes only. Use responsibly.
