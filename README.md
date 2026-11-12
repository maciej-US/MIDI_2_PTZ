# MIDI2PTZ

MIDI2PTZ is a Java desktop application that allows PTZ cameras to be controlled using MIDI hardware controllers.

The application translates MIDI events into camera control commands and provides real-time feedback through controller LEDs and indicators. Multiple cameras can be assigned dynamically to physical MIDI panels and controlled using configurable mappings.

![Java](https://img.shields.io/badge/Java-25-blue)
![Platform](https://img.shields.io/badge/Platform-Windows%20%7C%20Linux-green)
![License](https://img.shields.io/badge/License-MIT-orange)

---

## Features

### PTZ Camera Control

- VISCA-over-IP camera control
- Pan / Tilt / Zoom support
- Camera preset management
- Camera status monitoring
- Dynamic camera reassignment

### MIDI Controller Support

- Generic Java MIDI devices
- Direct USB MIDI transport (usb4java)
- LED feedback
- Hardware or software blinking indicators
- Jog wheel support
- Runtime panel reassignment

### Configuration

- Graphical configuration editor
- Camera configuration import/export
- MIDI mapping configuration via JSON
- Multi-camera layouts
- Portable configuration files

---

## Architecture

```text
MIDI Controller
      │
      ▼
MIDI2PTZ
      │
      ▼
Control Event Bus
      │
      ▼
PTZ Cameras
```

Main subsystems:

- MIDI Controller Layer
- Event Bus
- PTZ Camera Manager
- VISCA Transport Layer
- Swing GUI

---

## Supported MIDI Transports

### General MIDI

Uses the standard Java Sound MIDI subsystem.

Examples:

- Behringer X-Touch Mini
- Korg nanoKONTROL
- Akai MIDImix
- Any controller exposed through Java MIDI

### USB Direct

Uses direct USB communication via usb4java.

This mode allows communication with controllers that expose raw USB MIDI interfaces without relying on operating system MIDI services.

---

## Requirements

### Runtime

- Java 25+
- Windows 10/11
- Linux (tested on Ubuntu)

### Build

- Maven 3.9+
- JDK 25

---

## Building

```bash
mvn clean package
```

Generated artifact:

```text
target/midi2PTZ-1.0.jar
```

---

## Packaging

Example using jpackage:

```bash
jpackage \
  --type app-image \
  --runtime-image <runtime> \
  --name MIDI2PTZ \
  --input target \
  --main-jar midi2PTZ-1.0.jar \
  --main-class uci.midi2ptz.Midi2PTZ \
  --icon midi2ptz.ico
```

---

## Configuration Files

### MIDI Mapping

Controller definitions are stored as JSON files.

Example elements:

- Buttons
- LEDs
- Jog wheels
- Status requests
- Reassignable controls

### Camera Definitions

Each camera specifies:

- Display name
- IP address
- Port
- VISCA definition file
- MIDI panel assignment

---

## Project Status

Active development.

The software was originally developed for controlling VISCA-compatible PTZ cameras using compact MIDI control surfaces during live production and streaming workflows.

---

## License

MIT License
