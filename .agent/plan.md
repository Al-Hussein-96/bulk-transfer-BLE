# Project Plan: Create an Android app called "Bulk Transfer" with Sender and Receiver modes. Connectivity is via Bluetooth. The sender sends a list of vouchers to the receiver upon clicking a button, and the receiver displays them. The app must follow Material Design 3 (M3) guidelines, use Jetpack Compose, have an adaptive icon, and support Edge-to-Edge display. Use a vibrant, energetic color scheme. The Receiver will show a QR code, and the Sender will scan it to connect.

## Project Brief

### Features

*   **Role-Based Mode Selection**: A high-energy landing screen to toggle between Sender and Receiver roles with a full edge-to-edge Material 3 layout.
*   **QR-Based Pairing (Receiver)**: Generates a dynamic QR code containing the device's Bluetooth connection details for instant, secure identification.
*   **Camera-Integrated Scanning (Sender)**: Utilizes a built-in scanner to capture the receiver's QR code, automatically establishing the Bluetooth handshake without manual searching.
*   **Bulk Voucher Transmission**: One-tap transfer of voucher data from the sender to the receiver over a stable Bluetooth socket.
*   **Real-Time Voucher List**: A vibrant, reactive interface for the receiver that displays the incoming vouchers as they are successfully transferred.

### High-Level Technical Stack

*   **Language**: Kotlin
*   **UI Framework**: Jetpack Compose with Material Design 3 (M3)
*   **Architecture**: MVVM (Model-View-ViewModel)
*   **Communication**: Android Bluetooth API (RFCOMM Sockets)
*   **QR & Imaging**: CameraX for scanning and ZXing/Barcode-scanning for QR generation/parsing
*   **Asynchrony**: Kotlin Coroutines and Flow
*   **Dependency Injection**: Dagger Hilt
*   **Code Generation**: KSP (Kotlin Symbol Processing)

## Implementation Steps
**Total Duration:** 25m 22s

### Task_1_BaseSetupBluetoothCore: Set up Hilt dependency injection, Material 3 theme with a vibrant color scheme, and implement the core Bluetooth communication layer (Manager/Repository) for handling discovery, pairing, and data transfer.
- **Status:** COMPLETED
- **Updates:** Successfully initialized Hilt, configured a vibrant Material 3 theme with Edge-to-Edge support, and implemented the core Bluetooth communication layer (BluetoothController, DataTransferService) including discovery, pairing, and data transfer logic. Added necessary permissions to AndroidManifest.xml. Project builds successfully.
- **Acceptance Criteria:**
  - Hilt is correctly initialized
  - Vibrant Material 3 theme and Edge-to-Edge display are configured
  - Bluetooth discovery and P2P connection logic is implemented
  - Project builds successfully
- **Duration:** 9m 53s

### Task_2_SenderModeUI: Implement the Mode Selection screen and the Sender dashboard. Create UI for Bluetooth device scanning, selection, and the 'One-Tap' bulk transmission logic to send a list of vouchers.
- **Status:** COMPLETED
- **Updates:** Implemented Mode Selection, Sender Dashboard, and Receiver Dashboard. The Sender can scan for devices, connect, and send vouchers. The Receiver can wait for connections and display received vouchers in a list. All screens follow Material 3 and support Edge-to-Edge. Adaptive icon created. Project builds successfully.
- **Acceptance Criteria:**
  - Mode selection screen allows choosing 'Sender'
  - Sender UI displays nearby Bluetooth devices
  - 'Send Vouchers' button initiates data transmission to the receiver
- **Duration:** 11m 54s

### Task_3_ReceiverModeUI: Implement the Receiver dashboard. Create the real-time voucher feed using Jetpack Compose to display incoming data from the sender instantly.
- **Status:** COMPLETED
- **Updates:** Finalized the Receiver dashboard with a real-time voucher feed. Improved data transfer robustness using Okio and a line-based JSON protocol. Enhanced the UI with animations, gradients, and a premium Material 3 aesthetic. Verified all functionalities and successful project build.
- **Acceptance Criteria:**
  - Mode selection screen allows choosing 'Receiver'
  - Receiver UI waits for incoming connections
  - Voucher feed updates in real-time as data is received
- **Duration:** 3m 34s

### Task_4_ImplementQRPairing: Replace manual Bluetooth scanning with QR code pairing. Implement QR code generation for the Receiver (containing its Bluetooth MAC address) and integrate a CameraX scanner for the Sender to establish the connection.
- **Status:** IN_PROGRESS
- **Acceptance Criteria:**
  - Receiver generates and displays a QR code containing its MAC address
  - Sender successfully scans QR code and initiates Bluetooth connection
  - Manual device scanning UI is replaced by the camera scanner
- **StartTime:** 2026-01-14 15:11:51 GST

### Task_5_RunAndVerify: Apply final visual polishes to the vibrant theme, ensure adaptive icon is correct, and instruct critic_agent to verify application stability, QR pairing flow, and alignment with requirements.
- **Status:** PENDING
- **Acceptance Criteria:**
  - Adaptive app icon is present
  - App builds and runs without crashes
  - QR-based Bluetooth pairing works seamlessly
  - Make sure all existing tests pass, build pass and app does not crash

