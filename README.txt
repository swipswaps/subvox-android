
SUBVOX ANDROID PORT
===================

1. HOW WE GOT HERE
Upstream (https://github.com/joeblau/subvox) is a macOS app that reads AirPods Pro
telemetry through CoreMotion. Android has no CMHeadphoneMotionManager. The port
substitutes SensorManager: TYPE_LINEAR_ACCELERATION, TYPE_GYROSCOPE, and
TYPE_ROTATION_VECTOR. Those read the phone IMU.

2. WHERE WE ARE
Implemented:
- Sensor acquisition with TYPE_HEAD_TRACKER fallback.
- DTW with Sakoe-Chiba band (DOI: 10.1109/TASSP.1978.1163055).
- JSON persistence using filesDir (with atomic save).
- Evaluation.kt: LOO accuracy, majority baseline, 500x label-permutation null,
  Monte Carlo p-value with (1+ge)/(1+B) correction (DOI: 10.2202/1544-6115.1585).
- Material 3 UI (NestedScrollView, MaterialButton, ProgressIndicator).
- On-device Voice-to-Text using Vosk (Alpha Cephei) to fill the label field.
- On-device Lip Reading using CameraX + MediaPipe Face Landmarker.

Known limitations:
- The SM-J737T (Android 9, SDK 28) cannot use TYPE_HEAD_TRACKER (requires API 33).
  Head-mounted telemetry requires Android 13+ and a compatible headset.
- Energy-trigger segmentation is not ported; capture is manual.
- Lip landmarks are currently visualised but not yet fused into the DTW classifier.

3. HOW TO USE
Prereqs: Android Studio Jellyfish+, JDK 17, physical device API 26+.
Build locally: ./gradlew --no-daemon assembleDebug
Install: adb install -r app/build/outputs/apk/debug/app-debug.apk

In-app:
  1. Type a word, or tap "Transcribe Audio (3s)" to speak the label.
  2. Tap "Start Camera" to see your lip landmarks in red.
  3. Tap "Record Reference", articulate the word, tap "Stop & Save".
  4. Repeat >=4 takes across >=2 distinct words.
  5. Tap "Evaluate" to see the LOO and permutation test results.

Automation: scripts/automate_recording.py drives the app via ADB and
uiautomator resource-ids, records takes, and generates dist/audit/audit.md.

Voice Label Install (one-time):
  wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
  unzip vosk-model-small-en-us-0.15.zip -d dist/
  adb push dist/vosk-model-small-en-us-0.15 /data/local/tmp/
  adb shell run-as com.example.subvox cp -r /data/local/tmp/vosk-model-small-en-us-0.15 files/

4. REFERENCES
- Sakoe & Chiba (1978), IEEE Trans. ASSP 26(1), DOI: 10.1109/TASSP.1978.1163055
- Phipson & Smyth (2010), Stat. Appl. Genet. Mol. Biol. 9(1), DOI: 10.2202/1544-6115.1585
- Vosk Offline Speech Recognition: https://alphacephei.com/vosk/
- MediaPipe Face Landmarker: https://developers.google.com/mediapipe/solutions/vision/face_landmarker
- Android internal storage (filesDir): https://developer.android.com/training/data-storage/app-specific
