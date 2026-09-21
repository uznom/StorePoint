# StorePoint — Offline-First POS & Inventory Management System

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-M3-brightgreen.svg)](https://developer.android.com/jetpack/compose)
[![MinSdk](https://img.shields.io/badge/MinSdk-34%20(Android%2014)-orange.svg)](https://developer.android.com/about/versions)
[![TargetSdk](https://img.shields.io/badge/TargetSdk-36-teal.svg)](https://developer.android.com/about/versions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**StorePoint** is a production-grade, offline-first Point of Sale (POS) and inventory management Android application engineered specifically for micro-retailers, neighborhood groceries, and Philippine *sari-sari* stores. Built entirely with **Jetpack Compose** and **Room Database**, StorePoint runs 100% locally with zero internet dependency, zero subscription costs, and full data privacy.

---

## Highlights & Features

### 🛒 High-Speed Cashier POS
- **Instant Product Search & Barcode Scanning**: Integrated camera scanner (powered by Google ML Kit) and hardware USB/Bluetooth barcode scanner support.
- **Dynamic Multi-UOM & "Tingi" Packaging**: Built-in support for single pieces (*tingi*), 3-packs, 12-pack cases, reams, and master cases with automatic inventory multiplier deductions.
- **Cart & Order Park/Resume**: Put customer orders on hold while attending to another customer, and resume with a single tap.
- **Flexible Discounts**: Percentage discounts and custom peso discounts applied at the line-item or cart level.
- **Payment Methods**: Cash, GCash, and Maya payment modes with GCash/Maya wallet Cash-in / Cash-out tracking and automated bank-fee calculations. (Split payment and a Store Credit / Accounts Receivable ledger are planned, not yet implemented.)

### 🔁 Returns, Refunds & Item Exchanges
- **Receipt Lookup**: Quickly retrieve past receipts by Transaction ID, date, or customer name.
- **Item-Level Returns**: Choose individual line items to return, adjust quantities, and select return reasons (*Defective / Spoiled*, *Wrong Item*, *Customer Return*).
- **Restocking Control**: Toggle whether returned merchandise is returned to active stock or written off as shrinkage.
- **Payout Options**: Refund directly from the cash drawer, or apply as an exchange/store-credit note (recorded as a return; no separate AR ledger is maintained).

### 📦 Inventory & Stock Control
- **Low-Stock Alerts & Thresholds**: Automatic badge indicators and real-time dashboard notifications for running-out items.
- **Expiration Date Tracking**: Highlight expiring products to prevent waste and loss.
- **Batch Stock Adjustments & Cost Management**: Real-time margin calculation, weighted average unit costs, and profit calculations.

### 🚚 Supplier & Purchase Order (PO) Management
- **Vendor Directory**: Maintain supplier contacts, delivery frequencies, and payment terms (COD, Net 7, Net 30).
- **Purchase Order Lifecycle**: Create purchase orders with custom items, unit costs, and expected delivery dates.
- **Receive Shipments**: Automatically update inventory stock levels and log landed costs when receiving PO items.

### 💵 Cash Drawer & Shift Management
- **Shift Tracking**: Opening float / starting cash input, real-time drawer balance tracking, and closing counts with reconciliation reports.

### 🖨️ ESC/POS Thermal Printing & Digital Receipts
- **Bluetooth Thermal Printers**: Compatible with standard 58mm and 80mm ESC/POS receipt printers.
- **Customizable Receipt Headers & Footers**: Configure store name, address, contact numbers, TIN, and custom thank-you messages.
- **Digital Sharing & Export**: Generate and share receipt summaries via messaging apps or save receipts as PDF/CSV.

### 📊 Reports & Business Insights
- **Sales Analytics**: Daily, weekly, monthly, and custom date range revenue, cost of goods sold (COGS), gross profit, and profit margin analysis.
- **Rush-Hour Heatmap**: Identify the store's busiest hours to optimize staffing and stocking.
- **Top Performers**: Ranked lists of best-selling products by quantity sold and revenue generated.

### 🔒 Kiosk Mode & Security
- **Role-Based Access**: Multi-user support with PIN protection separating Admin dashboards from Cashier POS terminals.
- **Dedicated Kiosk Mode**: Optional full-screen kiosk lock to prevent cashiers from exiting to the Android launcher or accessing system settings.

### 💾 Backup & Data Portability
- **100% Offline-First**: All data is stored locally in a Room SQLite database. Exported/shared backups can be optionally password-encrypted (AES-256 + HMAC-SHA256).
- **JSON Backup & Restore**: Export complete store databases to external storage or flash drives, and restore seamlessly on any replacement device.

---

## Architecture & Tech Stack

StorePoint follows modern Android architecture patterns (MVVM + Clean Architecture) with 100% declarative UI:

```
┌────────────────────────────────────────────────────────┐
│             Jetpack Compose UI (Material 3)            │
│  CashierPOSScreen │ AdminDashboard │ SetupScreen etc.   │
└───────────────────────────▲────────────────────────────┘
                            │ UI State (StateFlow)
                            │ Events / User Actions
┌───────────────────────────┴────────────────────────────┐
│                  StorePointViewModel                   │
│   Cart State │ Active Session │ Scanner State │ Drawer │
└───────────────────────────▲────────────────────────────┘
                            │ Coroutines & Flows
┌───────────────────────────┴────────────────────────────┐
│                 StorePointRepository                   │
│  Data Abstraction │ In-Memory Caches │ Pre-Population  │
└───────────────────────────▲────────────────────────────┘
                            │ Room DAOs
┌───────────────────────────┴────────────────────────────┐
│               Room Database (SQLite)                   │
│ Products │ Categories │ Transactions │ Suppliers │ POs │
└────────────────────────────────────────────────────────┘
```

- **Language**: Kotlin 2.0+
- **UI Framework**: Jetpack Compose with Material Design 3
- **Local Persistence**: Android Jetpack Room 2.7.0 (KSP code generation)
- **State Management**: Android ViewModel, Kotlin Coroutines, and `StateFlow`
- **Camera & Barcodes**: CameraX + Google ML Kit Barcode Scanning
- **Hardware Integration**: Android Bluetooth SPP for ESC/POS thermal printers
- **Testing**: JUnit 4, Robolectric, and Roborazzi screenshot verification

---

## Project Structure

```
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/
│   │   │   │   ├── MainActivity.kt               # Entry activity, window flags, permission flows
│   │   │   │   ├── StorePointApplication.kt      # Application class, crash reporting initialization
│   │   │   │   ├── StorePointDeviceAdminReceiver # Optional device administrator for kiosk lockdown
│   │   │   │   ├── data/
│   │   │   │   │   ├── AppDatabase.kt            # Room database definition, schema migrations
│   │   │   │   │   ├── Entities.kt               # Room entity data classes
│   │   │   │   │   ├── Daos.kt                   # Data access objects with Kotlin Coroutine flows
│   │   │   │   │   └── Repository.kt             # StorePointRepository implementation
│   │   │   │   ├── ui/
│   │   │   │   │   ├── StorePointViewModel.kt    # Main MVVM ViewModel
│   │   │   │   │   ├── CameraPreviewView.kt      # CameraX barcode scanner view
│   │   │   │   │   ├── theme/                    # Material 3 Color, Type, Shape & Theme
│   │   │   │   │   └── screens/
│   │   │   │   │       ├── CashierPOSScreen.kt   # Core POS terminal, cart, and drawer UI
│   │   │   │   │       ├── AdminDashboardScreen.kt# Products, Analytics, Users, Config
│   │   │   │   │       ├── ReturnRefundDialog.kt # Returns and refund management
│   │   │   │   │       ├── ProductVariantsDialog.kt # Multi-UOM and packaging variant picker
│   │   │   │   │       ├── SuppliersAndPurchasesScreen.kt # Supplier and PO manager
│   │   │   │   │       ├── LoginScreen.kt        # Cashier PIN login
│   │   │   │   │       ├── SetupScreen.kt        # First-time setup wizard
│   │   │   │   │       └── OnboardingScreen.kt   # Walkthrough guides
│   │   │   │   └── util/
│   │   │   │       ├── EscPosHelper.kt           # ESC/POS thermal receipt formatting engine
│   │   │   │       ├── DatabaseBackupManager.kt  # JSON Export & Import utility
│   │   │   │       └── CrashDiagnosticsManager.kt# Local crash diagnostics and recovery
│   │   │   └── res/                              # Drawables, strings, colors, XML manifests
│   │   └── test/                                 # Unit & Robolectric test suites
│   ├── build.gradle.kts                          # App-level build configuration
│   └── proguard-rules.pro                        # Proguard and R8 optimization rules
├── docs/                                         # Technical & User documentation
│   ├── ARCHITECTURE.md                           # Architecture and schema deep dive
│   └── USER_GUIDE.md                             # Step-by-step user and cashier manual
├── build.gradle.kts                              # Root build configuration
├── settings.gradle.kts                           # Module settings
├── metadata.json                                 # AI Studio platform configuration
└── README.md                                     # Project overview (this file)
```

---

## Getting Started

### Prerequisites
- **Android Studio** Ladybug (2024.2+) or newer
- **JDK**: Java Development Kit 17 or 21
- **Android Device or Emulator**: Android 8.0 (API level 26) or higher

### Building from Source

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-username/storepoint.git
   cd storepoint
   ```

2. **Open the project in Android Studio**:
   - Select **Open an Existing Project** and select the cloned root directory.
   - Allow Gradle to sync dependencies automatically.

3. **Build via Command Line**:
   ```bash
   # Assemble the debug APK
   ./gradlew assembleDebug

   # Run local JVM and Robolectric tests
   ./gradlew testDebugUnitTest
   ```

4. **Install on Device**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## Automated GitHub Releases (CI/CD)

The repository includes a ready-to-use **GitHub Actions** workflow (`.github/workflows/release.yml`) that automatically compiles the APK and publishes a new GitHub Release with downloadable assets.

### How to trigger a release:

- **Method 1: Push a Git Tag**
  ```bash
  git tag v1.0.0
  git push origin v1.0.0
  ```
- **Method 2: One-Click Manual Trigger (GitHub Web UI)**
  1. In your GitHub repository, go to the **Actions** tab.
  2. Select **Build & Publish Release APK** in the left sidebar.
  3. Click **Run workflow**, enter the tag (e.g., `v1.0.0`), and click **Run workflow**.

Once finished (usually ~2–3 minutes), the APK (`StorePoint-v1.0.0.apk`) and checksums (`SHA256SUMS.txt`) will be published and available for download under the repository's **Releases** tab.


---

## Database & Data Persistence

StorePoint uses **Android Jetpack Room** with incremental migrations:
- Current Database Version: **`9`**
- Fallback migration is enabled to safeguard against catastrophic upgrades, but structured migrations (`MIGRATION_1_2` through `MIGRATION_8_9`) guarantee preservation of historic sales, products, and customer balances across app updates.
- Raw database files can be backed up directly from the app via **Admin Dashboard → Settings → Database Backup & Restore**.

---

## Hardware Integration Guide

### 1. Barcode Scanners
- **Camera Scanning**: Uses Google ML Kit Vision Barcode SDK. Works with standard camera phones; recommended to place items 15–20cm away from the lens.
- **Physical USB / Bluetooth Scanners**: Standard HID barcode guns work out-of-the-box as hardware keyboard inputs without additional setup.

### 2. Thermal Receipt Printers
- Compatible with all 58mm and 80mm Bluetooth ESC/POS thermal printers (e.g., ZJ-58, Xprinter, Epson, GOOJPRT).
- In the app: Pair the printer in Android Bluetooth settings, then navigate to **Admin Dashboard → Printer Settings** and select the paired device.

---

## Security & Secrets Management (GitHub Sync)

StorePoint is pre-configured with strict gitignore rules and offline-first architectural safety to prevent credentials or sensitive files from ever leaking into GitHub or public version control:

- **Keystores & Signing Keys**: `*.keystore`, `*.keystore.base64`, `*.jks`, `*.key`, and `debug.keystore.base64` are strictly gitignored. Production signing keys are provided via environment variables (`KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD`).
- **Offline Self-Contained Architecture**: StorePoint requires no external API keys or cloud AI tokens; everything runs securely and locally.
- **Service Accounts & Cloud Credentials**: `google-services.json`, `credentials.json`, and cloud service account keys are automatically ignored by `.gitignore`.
- **Local Data Protection**: All customer accounts and admin authorization PINs are hashed using PBKDF2 with unique salts before being saved to local SQLite.

## Kiosk Lockdown & Terminal Provisioning (ADB)

For dedicated checkout counters and POS tablets, StorePoint supports Android enterprise device owner lockdown and permanent launcher configuration via ADB:

```bash
# 1. True Kiosk Mode via Device Owner (disables exit gestures & system pull-down)
# (Ensure all Google/user accounts are removed in Android Settings > Accounts first)
adb shell dpm set-device-owner com.munzo.storepoint/.StorePointDeviceAdminReceiver

# 2. Set StorePoint as the permanent default Home launcher
adb shell cmd package set-home-activity com.munzo.storepoint/.MainActivity

# 3. Permanent full-screen immersive mode (hide navigation bar system-wide)
adb shell settings put global policy_control immersive.full=*

# 4. Keep display awake while connected to charger
adb shell settings put global stay_on_while_plugged_in 3

# De-provision Device Owner if testing on a personal device:
adb shell dpm remove-active-admin com.munzo.storepoint/.StorePointDeviceAdminReceiver
```

See the [User Guide — Kiosk Lockdown Guide](docs/USER_GUIDE.md#7-dedicated-terminal--kiosk-lockdown-adb-commands) for comprehensive terminal setup instructions.

---

## Documentation

- [Architecture & Technical Design](docs/ARCHITECTURE.md) — Detailed explanation of state management, Room DAOs, and data flow.
- [User Guide & Cashier Manual](docs/USER_GUIDE.md) — Step-by-step instructions for store owners and cashiers.

---

## License

```text
Copyright 2026 StorePoint Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
