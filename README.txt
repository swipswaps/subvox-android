
SUBVOX ANDROID PORT
===================

A native Android port of joeblau/subvox: a small-vocabulary template
recognizer that asks whether head/device motion carries enough information
to distinguish silently mouthed or whispered words, plus the statistical
test that says whether the answer is real.

No language model. No cloud. 1-NN dynamic time warping over 10 kinematic
channels, a leave-one-out accuracy, and a 500x label-permutation p-value,
all computed on the handset.


1. HOW WE GOT HERE
------------------

The upstream prototype (https://github.com/joeblau/subvox) is a macOS app.
It reads 3-axis user acceleration, 3-axis rotation rate, and attitude
through Apple's CMHeadphoneMotionManager, which streams head-tracking
telemetry from AirPods Pro. A CaptureController segments the stream, a
MotionFeatures stage mean-centers each channel and trims quiet lead-in and
lead-out frames, and a 1-nearest-neighbour DTW matcher with a Sakoe-Chiba
band assigns a label. Evaluation.swift then runs leave-one-out
classification and a 500x label-permutation test, reporting accuracy
against chance, a p-value, and a confusion matrix.

Android has no CMHeadphoneMotionManager. There is no public, vendor-neutral
API that exposes an arbitrary pair of Bluetooth earbuds as an IMU. The
substitution made here is:

    CMHeadphoneMotionManager.userAcceleration  ->  Sensor.TYPE_LINEAR_ACCELERATION
    CMHeadphoneMotionManager.rotationRate      ->  Sensor.TYPE_GYROSCOPE
    CMHeadphoneMotionManager.attitude          ->  Sensor.TYPE_ROTATION_VECTOR

These read the phone's IMU, not the earbuds'. If the phone is worn on the
head (headband, helmet, temple mount) the physical signal is comparable to
the upstream setup. If the phone is in a pocket or on a desk, it is not:
you are classifying the phone, not the head. This is a hardware limitation,
not a software one. Binding to a BLE headset that publishes its own IMU
requires a vendor SDK or a GATT client against a documented characteristic
UUID, and is out of scope for this port.

The algorithmic core transfers unchanged. Kotlin has no dependency on
CoreMotion, and the DTW matrix, the z-scoring, and the permutation test are
pure arithmetic.


2. WHERE WE ARE
---------------

Implemented and building:

  - Sensor acquisition: MotionStreamCollector registers the three sensors at
    10 ms (100 Hz requested, throttled to ~50 Hz fused emission) and emits
    a single MotionFrame per tick. Requires HIGH_SAMPLING_RATE_SENSORS above
    200 Hz; at 100 Hz the permission is declared but not strictly needed.

  - Persistence: DatasetRepository writes JSON to filesDir. One
    TemplateRecord per take, so a label can have many takes. Multiple takes
    per label are required for a meaningful leave-one-out estimate.

  - Matching: DynamicTimeWarping with a Sakoe-Chiba band sized as
    max(1, 0.15 * max(n, m)); TemplateClassifier z-scores each sequence and
    returns label, best distance, runner-up distance, margin, and an
    accept/reject flag. Rejection mirrors upstream: a segment is refused if
    the best distance exceeds maxDistance or the margin falls below
    minConfidence.

  - Evaluation: Evaluation.kt performs leave-one-out classification over
    every stored take and a 500x label-permutation null. Reports LOO
    accuracy, chance majority, chance uniform, null mean and sd, Monte Carlo
    p-value, and a confusion matrix.

  - CI: .github/workflows/android-build.yml builds a debug APK on every push
    to main and uploads it as the subvox-debug-apk artifact.

  - Local helper: scripts/build-and-install.sh builds and, if exactly one
    adb device is attached, installs with adb install -r.

Known limitations, stated plainly:

  - Reads the phone IMU, not the earbuds' (see section 1).
  - Manual capture only. The upstream energy-trigger segmentation with
    calibrated noise floor, pre-roll, and hangover is not ported.
  - Attitude is stored as a raw quaternion; upstream derives attitude change
    since segment start. The two are not equivalent under rotation.
  - Per-sequence z-scoring, not per-training-set z-scoring as upstream does.
    This changes the distance scale but not the ranking within a label.
  - No unit tests. DTW and Evaluation are pure functions and should have
    JVM tests before any claim is published.


3. HOW TO USE
-------------

Prerequisites
  - Android Studio Jellyfish/Koala or newer, or a command-line SDK with
    platform 34 and build-tools 34.
  - JDK 17.
  - A physical device running API 26+. Emulators do not produce realistic
    motion.

Build locally
  $ cd subvox-android
  $ ./gradlew --no-daemon assembleDebug
  APK: app/build/outputs/apk/debug/app-debug.apk

  Or, for build plus install in one step:
  $ ./scripts/build-and-install.sh

Build in CI
  Push to main. Open the Actions tab, select the run, download the
  subvox-debug-apk artifact. No secrets required for a debug build.

Install
  $ adb install -r app/build/outputs/apk/debug/app-debug.apk
  Or sideload: copy the APK to the device, enable Install unknown apps for
  your file manager, tap the APK.

In-app workflow
  1. Train. Type a token in the field, tap Record Reference, hold the phone
     against your head, mouth or whisper the token, tap Stop & Save. Repeat
     for the same token at least 4-5 times, then repeat for other tokens.
     You need at least 4 takes total across at least 2 labels before
     evaluation will run.
  2. Evaluate. Tap Evaluate. The app computes the full DTW matrix once, then
     runs LOO and 500 label permutations on Dispatchers.Default. Expect the
     confusion matrix to be sparse: with 5 words x 10 takes, a good result
     is 40-70% accuracy against a 20% chance level.
  3. Decode. Tap Run Classification, articulate, tap again. If the margin is
     too small the result is reported as rejected rather than guessed.

Reading the evaluation output
  - LOO accuracy is the fraction of held-out takes assigned the correct
    label by 1-NN over the remaining takes.
  - Chance majority is the accuracy of always predicting the most frequent
    label; chance uniform is 1/k. Report LOO against majority, not against
    1/k, because a skewed dataset can beat 1/k trivially.
  - p is the Monte Carlo permutation p-value with the +1 correction:
        p = (1 + #{null accuracy >= observed}) / (1 + B)
    with B = 500. The correction keeps p > 0 and the test exact at level
    alpha. p < 0.05 means the motion stream distinguishes your words better
    than shuffled labels do.
  - A single significant p on one session is weak evidence. Re-run across
    days, across feature subsets, and across Normal / Whisper / Silent
    conditions before drawing conclusions.

Experiment protocol
  Record 5 words, 10 takes each, under three conditions (normal speech,
  whisper, silent mouthing). Evaluate each condition separately. Then repeat
  with acceleration only, then rotation only, to see which channel carries
  the signal. The upstream prototype exposes those as feature checkboxes;
  here you would filter the columns in TemplateRecord.features.


4. SCIENTIFIC NOTES
-------------------

DTW is computed with a Sakoe-Chiba global path constraint, which bounds the
warping path to |i - j| <= w and reduces complexity from O(n*m) to
O(n*w). The band width is 15% of the longer sequence, following the usual
practice for small-vocabulary spoken-word matching.

The permutation test is exact under the null that labels are exchangeable
with respect to the feature distribution. It does not assume normality and
does not rely on the asymptotic behaviour of any classifier. The +1
correction is the standard Monte Carlo estimator that avoids reporting
p = 0 from a finite number of shuffles.

The distance-matrix precomputation is exact, not an optimization shortcut.
DTW distance is a function of the two feature sequences alone. Permuting
labels changes which distance is compared against which ground truth, never
the distances themselves. Computing the matrix once and reusing it for all
500 shuffles therefore yields bit-identical results to recomputing DTW
inside every permutation, at a fraction of the cost.

Caveat on chance level. With small N and few classes, chance-level
accuracies are not sharply defined, and cross-validated accuracy can exceed
the nominal chance level even for pure noise. This is why the permutation
null is reported alongside the theoretical chance level rather than
replacing it. Cite the null, not 1/k.


5. REFERENCES
-------------

  [1] Blau, J. Subvox: AirPods motion-to-text macOS prototype. GitHub.
      https://github.com/joeblau/subvox
  [2] Sakoe, H., & Chiba, S. (1978). Dynamic programming algorithm
      optimization for spoken word recognition. IEEE Transactions on
      Acoustics, Speech, and Signal Processing, 26(1), 43-49.
      DOI: 10.1109/TASSP.1978.1163055
  [3] Phipson, B., & Smyth, G. K. (2010). Permutation p-values should never
      be zero: calculating exact p-values when permutations are randomly
      drawn. Statistical Applications in Genetics and Molecular Biology,
      9(1), Article 39. DOI: 10.2202/1544-6115.1585
  [4] Ojala, M., & Garriga, G. C. (2010). Permutation tests for studying
      classifier performance. Journal of Machine Learning Research, 11,
      1833-1863.
  [5] Combrisson, E., & Jerbi, K. (2015). Exceeding chance level by chance:
      the caveat of theoretical chance levels in brain signal
      classification and statistical assessment of decoding accuracy.
      Journal of Neuroscience Methods, 250, 126-136.
      DOI: 10.1016/j.jneumeth.2015.01.010
  [6] Google Developers. Sensors Overview. Android Open Source Project.
      https://developer.android.com/guide/topics/sensors/sensors_motion
  [7] GitHub Docs. Store and share data with workflow artifacts.
      https://docs.github.com/en/actions/using-workflows/storing-workflow-data-as-artifacts
