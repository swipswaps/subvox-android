
SUBVOX ANDROID PORT: PRODUCTION SPECIFICATION & FILE MANIFEST

1. SYSTEM ARCHITECTURE & SCIENTIFIC FOUNDATION
Subvox-Android translates the macOS Swift CoreMotion spatial telemetry pipeline into an AOSP-compliant real-time gesture classification framework. Sensor streams are collected from linear accelerometers, gyroscopes, and rotational vector quaternion channels via Android SensorManager. Dynamic time alignment is achieved through non-parametric Dynamic Time Warping (DTW) with Sakoe-Chiba global path constraints.

2. FILE MANIFEST
- build.gradle.kts, settings.gradle.kts, gradle.properties
- gradlew, gradlew.bat, gradle/wrapper/gradle-wrapper.properties, gradle/wrapper/gradle-wrapper.jar
- app/build.gradle.kts, app/proguard-rules.pro
- app/src/main/AndroidManifest.xml
- app/src/main/java/com/example/subvox/MainActivity.kt
- app/src/main/java/com/example/subvox/sensor/MotionStreamCollector.kt
- app/src/main/java/com/example/subvox/ml/DynamicTimeWarping.kt
- app/src/main/java/com/example/subvox/ml/TemplateClassifier.kt
- app/src/main/java/com/example/subvox/data/DatasetRepository.kt
- app/src/main/res/layout/activity_main.xml
- app/src/main/res/values/strings.xml, themes.xml, colors.xml
- app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml, ic_launcher_round.xml

3. COMPILATION & INSTALLATION
$ ./gradlew clean assembleDebug
$ adb install app/build/outputs/apk/debug/app-debug.apk

4. REFERENCES
- Sakoe & Chiba (1978). IEEE Trans. ASSP, 26(1), 43-49. DOI: 10.1109/TASSP.1978.1163055.
- Android SensorManager: https://developer.android.com/guide/topics/sensors/sensors_motion
- Upstream: https://github.com/joeblau/subvox
