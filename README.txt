SUBVOX ANDROID PORT

Upstream: https://github.com/joeblau/subvox

A native Android port of Subvox: 1-NN dynamic time warping over 10 kinematic
channels (accelerometer, gyroscope, rotation vector), with leave-one-out
accuracy and a 500x label-permutation p-value computed on-device.

PIPELINE
- android-build.yml : JDK 17 + Android SDK 34 -> assembleDebug -> uploads `subvox-debug-apk`
- deploy-pages.yml  : peaceiris/actions-gh-pages -> gh-pages branch -> legacy Pages builder

HARDWARE
Reads the phone IMU by default. Android 13+ Head Tracker HID (Sony WH/WF-1000XM5,
XM6, ULT WEAR) exposes earbud IMU as Sensor.TYPE_HEAD_TRACKER. No vendor SDK required.

BUILD & INSTALL
1. Download the `subvox-debug-apk` artifact from the android-build Actions run.
2. Install on a connected device:
   $ adb install -r app-debug.apk
3. Launch the app, grant Bluetooth permissions, and use the Record / Classify / Evaluate buttons.

EVALUATION
Record >= 4 takes across >= 2 labels before tapping Evaluate. The permutation
p-value uses the Monte Carlo (1+ge)/(1+B) correction. Report p against the
majority-chance baseline, not 1/K.

REFERENCES
- Sakoe & Chiba (1978), DOI: 10.1109/TASSP.1978.1163055
- Phipson & Smyth (2010), DOI: 10.2202/1544-6115.1585
- Android Head Tracker HID: https://source.android.com/docs/core/interaction/sensors/head-tracker-hid-protocol
