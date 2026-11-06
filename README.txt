
SUBVOX ANDROID PORT: SPECIFICATION, HARDWARE, AND USAGE

1. HOW WE GOT HERE
Upstream (https://github.com/joeblau/subvox) is a macOS app that reads AirPods Pro
telemetry through CoreMotion. Android has no CMHeadphoneMotionManager. The port
substitutes SensorManager:

    userAcceleration -> TYPE_LINEAR_ACCELERATION
    rotationRate     -> TYPE_GYROSCOPE
    attitude         -> TYPE_ROTATION_VECTOR

SensorManager is the AOSP entry point for hardware sensors. Those three channels
read the phone IMU. A head-mounted phone works; a pocketed phone classifies the
pocket, not the head.

2. BLUETOOTH WORKAROUND: ANDROID HEAD TRACKER HID
Android 13+ defines the Head Tracker HID protocol: a compatible headset connects over
Bluetooth or USB and exposes its IMU as Sensor.TYPE_HEAD_TRACKER through the sensor
framework [reference:12]. No vendor SDK, no root. TYPE_HEAD_TRACKER is API 33+ and is
“typically not available for apps to use” unless a compatible HID device is present
[reference:13]. Confirmed compatible: Sony WH-1000XM5, WH-1000XM6, WF-1000XM5,
WF-1000XM6, ULT WEAR. Confirmed dead end: Jabra Elite 10 Gen 2 (orientation never
crosses GATT; buds spatialise on-device). The app registers TYPE_HEAD_TRACKER on
API 33+ and falls back to phone IMU otherwise; the status line reports which mode is
active.

3. WHERE WE ARE
Implemented:
- Sensor acquisition with TYPE_HEAD_TRACKER and phone-IMU fallback.
- DTW with Sakoe-Chiba band [reference:14]; z-scored 1-NN with margin rejection.
- JSON persistence, one TemplateRecord per take.
- Evaluation.kt: leave-one-out accuracy, majority/uniform chance baselines, 500x
  label-permutation null, Monte Carlo p-value with (1+ge)/(1+B) correction
  [reference:15], confusion matrix. The N x N DTW matrix is computed once; this is
  exact because DTW distance depends only on the feature sequences, not labels.
- CI builds a debug APK on every push to main and uploads subvox-debug-apk.
- scripts/build-and-install.sh: idempotent build + adb install.

Known limitations:
- Energy-trigger segmentation is not ported; capture is manual.
- TYPE_HEAD_TRACKER values[] index layout is device-dependent and has not been
  validated on hardware.
- Per-sequence z-scoring, not per-training-set z-scoring.
- No unit tests.

4. HOW TO USE
Prereqs: Android Studio Jellyfish+, JDK 17, physical device API 26+.
Build:   $ ./gradlew --no-daemon assembleDebug
Install: $ adb install -r app/build/outputs/apk/debug/app-debug.apk
In-app:  type token, Record Reference, articulate, Stop & Save. Repeat >=4 takes across
         >=2 tokens. Run Classification. Tap Evaluate for LOO + 500 permutations.
         Read p against chance MAJORITY, not 1/k; a skewed dataset beats 1/k trivially.
         A single significant p on one session is weak evidence. Repeat across days and
         across Normal / Whisper / Silent conditions.

5. REFERENCES
- Blau, J. Subvox. https://github.com/joeblau/subvox
- Sakoe & Chiba (1978), IEEE Trans. ASSP 26(1), 43-49,
  DOI: 10.1109/TASSP.1978.1163055 [reference:16]
- Phipson & Smyth (2010), Stat. Appl. Genet. Mol. Biol. 9(1),
  DOI: 10.2202/1544-6115.1585 [reference:17]
- Android Head Tracker HID:
  https://source.android.com/docs/core/interaction/sensors/head-tracker-hid-protocol
  [reference:18]
- TYPE_HEAD_TRACKER: https://developer.android.com/reference/android/hardware/Sensor#TYPE_HEAD_TRACKER
  [reference:19]
