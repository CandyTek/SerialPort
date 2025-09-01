# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is an Android serial port communication library project consisting of two modules:
- `app`: Demo application showcasing serial port usage
- `module_serialport`: Android library module containing the serial port implementation

The project provides a comprehensive serial port solution with support for configurable parameters including baud rate, data bits, parity, stop bits, and flow control.

## Build Commands

```bash
# Build the project
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Build the library module only
./gradlew :module_serialport:build

# Clean build artifacts
./gradlew clean
```

## Test Commands

```bash
# Run unit tests
./gradlew test

# Run unit tests for specific module
./gradlew :module_serialport:test
./gradlew :app:test

# Run instrumented tests on connected device
./gradlew connectedAndroidTest
```

## Architecture

### Core Components

- **SerialPortManager**: Main class that extends SerialPort, handles opening/closing serial ports and managing read/write operations
- **SerialPort**: Base class containing native JNI methods for low-level serial port operations
- **SerialPortFinder**: Utility class to discover available serial port devices on the system
- **OnSerialPortListener**: Interface for handling serial port events (success, fail, close, data received/sent)
- **SerialPortReadThread**: Background thread for reading data from serial port

### Native Layer

The library includes a native C component (`module_serialport/src/main/cpp/`) that provides the actual serial port implementation using JNI. The CMake build system is configured to compile the native library.

### Key Files

- `SerialPortManager.java`: Primary API for serial port operations
- `MainActivity.java`: Demo app showing complete usage example
- `SerialPort.c/h`: Native implementation for POSIX serial port operations
- `CMakeLists.txt`: Native build configuration

### Data Flow

1. SerialPortManager opens serial port using native methods
2. SerialPortReadThread continuously reads incoming data
3. Data is sent via Handler-based message queue to ensure thread safety
4. All operations are reported through OnSerialPortListener callbacks

## Development Notes

- Project uses Gradle 7.4.2 with Android Gradle Plugin
- Target SDK: 29, Min SDK: 21, Compile SDK: 33
- Uses DataBinding for UI in demo app
- Native code targets ARM v7a architecture
- Serial port parameters are fully configurable: baud rate, data bits (5-8), parity (none/odd/even/space), stop bits (1-2), flow control (none/RTS-CTS/XON-XOFF)

## Serial Port Configuration

The library supports comprehensive serial port configuration:
- **Baud Rate**: Any standard baud rate
- **Data Bits**: 5, 6, 7, or 8 bits
- **Parity**: 0 (none), 1 (odd), 2 (even), 3 (space)
- **Stop Bits**: 1 or 2 bits
- **Flow Control**: 0 (none), 1 (RTS/CTS), 2 (XON/XOFF)