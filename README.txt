
SUBVOX ANDROID PORT

1. HOW WE GOT HERE
Upstream (https://github.com/joeblau/subvox) is a macOS app that reads AirPods Pro
telemetry through CoreMotion. Android has no CMHeadphoneMotionManager. The port
substitutes SensorManager: TYPE_LINEAR_ACCELERATION, TYPE_GYROSCOPE, and
TYPE_ROTATION_VECTOR. Those read the phone IMU.

2. WHERE WE ARE
Implemented:
- Sensor acquisition with TYPE_HEAD_TRACKER fallback.
- DTW with Sakoe-Chiba band (DOI: 10.1109/TASSP.1978.1163055).
- JSON persistence using filesDir (Android internal storage best practice).
- Evaluation.kt: LOO accuracy, majority baseline, 500x label-permutation null,
  Monte Carlo p-value with (1+ge)/(1+B) correction (DOI: 10.2202/1544-6115.1585).
- GitHub Actions: android-build.yml builds the APK; deploy-pages.yml deploys
  the site via peaceiris.

Known limitations:
- The SM-J737T (Android 9, SDK 28) cannot use TYPE_HEAD_TRACKER (requires API 33).
  Head-mounted telemetry requires Android 13+ and a compatible headset.
- Energy-trigger segmentation is not ported; capture is manual.

3. HOW TO USE
Prereqs: Android Studio Jellyfish+, JDK 17, physical device API 26+.
Build locally: ./gradlew --no-daemon assembleDebug
Install: adb install -r app/build/outputs/apk/debug/app-debug.apk
In-app: type a word, Record Reference, articulate, Stop & Save.
Repeat >=4 takes across >=2 distinct words.
Tap Evaluate (LOO + 500 permutations). Read p against majority chance.

Automation: scripts/automate_recording.py drives the app via ADB and
uiautomator resource-ids, records takes, and generates dist/audit/audit.md.

Voice labels (optional): tap 'Transcribe Audio (3s)' to speak the label
into the phone microphone. Vosk 0.3.47 (Apache-2.0) transcribes on-device
and fills the text field. The classifier still uses only the IMU signal;
audio is used solely to populate the ground-truth label.

Model install (one-time):
  wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip
  unzip vosk-model-small-en-us-0.15.zip -d dist/
  adb push dist/vosk-model-small-en-us-0.15 /data/local/tmp/
  adb shell run-as com.example.subvox cp -r /data/local/tmp/vosk-model-small-en-us-0.15 files/
  adb shell rm -rf /data/local/tmp/vosk-model-small-en-us-0.15

4. REFERENCES
- Sakoe & Chiba (1978), IEEE Trans. ASSP 26(1), 43-49, DOI: 10.1109/TASSP.1978.1163055
- Phipson & Smyth (2010), Stat. Appl. Genet. Mol. Biol. 9(1), DOI: 10.2202/1544-6115.1585
- Android Material Design 3 (scrolling layouts): https://developer.android.com/develop/ui/compose/layouts
- Android internal storage (filesDir): https://developer.android.com/training/data-storage/app-specific#internal
- GitHub Actions CI for Android: https://developer.android.com/studio/build/continuous-integration
- adb uiautomator dump (resource-id): https://developer.android.com/tools/adb#uiautomator
