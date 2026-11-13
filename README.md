# SHAPKS - Multi-App Android Suite & CI/CD Pipeline

A modular Android repository hosting multiple utility and security apps, automatically compiled and packaged using **GitHub Actions CI/CD**.

---

## 📱 Included Apps

### 1. 🛡️ BioVault (`apps/biovault`)
* **Core Feature**: Strict biometric & device security gate.
* **Authentication Options**: Fingerprint, Face Unlock, or fallback to device PIN / Pattern / Password using AndroidX `BiometricPrompt`.
* **Security Guard**:
  * Auto-locks immediately upon leaving or minimizing the app (`onStop`).
  * `FLAG_SECURE` enabled to prevent screenshots, screen mirroring, and previews in Android's recent apps switcher.
  * Encrypted local vault using AES-256 GCM to safely store confidential passwords, notes, PINs, and crypto seeds.
  * Easy reveal/mask, copy-to-clipboard, and delete actions.

### 2. 🚨 StealthAlarm (`apps/stealthalarm`)
* **Core Feature**: Anti-theft sensor security alarm.
* **Movement Detection**: Listens to device accelerometer. If someone moves or picks up your device, an emergency siren and vibration pattern trigger.
* **Charger Detach Guard**: Detects if charging cable is detached in public places (airports, libraries, cafes).
* **Biometric Disarm Gate**: The alarm **cannot** be dismissed without fingerprint or device PIN authentication.

### 3. 🔬 SensorLab (`apps/sensorlab`)
* **Core Feature**: Live hardware telemetry and environmental sensor monitor.
* **Real-time Sensors**:
  * 📈 3-Axis Accelerometer (X, Y, Z + total G-force)
  * 🧭 Digital Compass (Azimuth heading in degrees with cardinal direction)
  * 🔄 Gyroscope (Pitch, Roll, Yaw angular rates)
  * 💡 Ambient Light Sensor (Lux) & Proximity Sensor (Distance / Obstruction)
  * 📱 Device Profile (SoC ABIs, Android OS & API level, Battery status & charging rate)

---

## ⚙️ Project Architecture

```
shapks/
├── .github/
│   └── workflows/
│       └── build-apks.yml       # GitHub Actions automated build pipeline
├── apps/
│   ├── biovault/                # Biometric security app
│   ├── stealthalarm/            # Anti-theft movement & charger alarm
│   └── sensorlab/               # Hardware sensor telemetry app
├── gradle/wrapper/              # Gradle 8.11.1 wrapper
├── settings.gradle.kts          # Root multi-project setup
├── build.gradle.kts             # AGP 8.7.2 + Kotlin 2.0.21
└── gradle.properties
```

---

## 🚀 Automated Compilation via GitHub Actions

Every time you push commits or open a Pull Request to `main` / `master`, GitHub Actions automatically:
1. Provisions JDK 17 and Android SDK 35.
2. Compiles and packages all APKs:
   - `BioVault-v1.0.0-debug.apk`
   - `StealthAlarm-v1.0.0-debug.apk`
   - `SensorLab-v1.0.0-debug.apk`
3. Uploads the compiled APKs to GitHub Actions **Artifacts**, ready for one-click download.
4. If a git tag (e.g. `v1.0.0`) is pushed, creates a GitHub Release and attaches the APKs directly.

### Triggering Manual Builds
You can trigger the build manually at any time:
1. Navigate to the **Actions** tab on your GitHub repository.
2. Select **Build and Package Android APKs**.
3. Click **Run workflow** -> Select branch -> Click **Run workflow**.
4. Download the generated APK zip file under the workflow run's **Artifacts**.

---

## 🛠️ Adding More APKs

To add another APK to this repository:
1. Create a new directory under `apps/yournewapp/`.
2. Add its `build.gradle.kts` and Android source tree (`src/main/...`).
3. Add `include(":apps:yournewapp")` to [`settings.gradle.kts`](file:///home/baba01mods/shapks/settings.gradle.kts).
4. Update [`.github/workflows/build-apks.yml`](file:///home/baba01mods/shapks/.github/workflows/build-apks.yml) to copy its output APK into `dist/apks/`.

---

## 💻 Local Build Instructions

If you have Android SDK installed locally, you can compile all APKs from the command line:

```bash
# Build all APKs
./gradlew assembleDebug

# Build only BioVault
./gradlew :apps:biovault:assembleDebug

# Build only StealthAlarm
./gradlew :apps:stealthalarm:assembleDebug

# Build only SensorLab
./gradlew :apps:sensorlab:assembleDebug
```
Output APKs will be located in each app's `build/outputs/apk/debug/` directory.
