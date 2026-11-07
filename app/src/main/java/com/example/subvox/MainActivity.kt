package com.example.subvox

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.subvox.audio.AudioTranscriber
import com.example.subvox.camera.LipOverlayView
import com.example.subvox.data.DatasetRepository
import com.example.subvox.data.TemplateRecord
import com.example.subvox.ml.Evaluation
import com.example.subvox.ml.TemplateClassifier
import com.example.subvox.sensor.MotionStreamCollector
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import androidx.camera.view.PreviewView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

const val APP_ID = "com.example.subvox"
private const val REQ_AUDIO = 200
private const val REQ_CAMERA = 201

class MainActivity : AppCompatActivity() {
    private lateinit var c: MotionStreamCollector
    private lateinit var r: DatasetRepository
    private lateinit var at: AudioTranscriber
    private val clf = TemplateClassifier()
    private val buf = mutableListOf<FloatArray>()
    private var cap = false

    private lateinit var previewView: PreviewView
    private lateinit var lipOverlay: LipOverlayView
    private var faceLandmarker: FaceLandmarker? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var isCameraOn = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        c = MotionStreamCollector(this); r = DatasetRepository(this); at = AudioTranscriber(this)

        val st   = findViewById<TextView>(R.id.statusText)
        val cnt  = findViewById<TextView>(R.id.takeCountText)
        val inp  = findViewById<TextInputEditText>(R.id.inputLabel)
        val rec  = findViewById<MaterialButton>(R.id.recordButton)
        val trn  = findViewById<MaterialButton>(R.id.transcribeButton)
        val dec  = findViewById<MaterialButton>(R.id.classifyButton)
        val eva  = findViewById<MaterialButton>(R.id.evaluateButton)
        val exp  = findViewById<MaterialButton>(R.id.exportButton)
        val clr  = findViewById<MaterialButton>(R.id.clearButton)
        val prog = findViewById<CircularProgressIndicator>(R.id.evalProgress)
        val camBtn = findViewById<MaterialButton>(R.id.cameraToggleButton)
        val camHint = findViewById<TextView>(R.id.cameraHintText)
        previewView = findViewById(R.id.cameraPreview)
        lipOverlay = findViewById(R.id.lipOverlay)

        fun refreshCount() {
            val (takes, labels) = r.count()
            val msg = when {
                takes == 0 -> "No takes yet."
                takes < 4 || labels < 2 -> "$takes takes, $labels labels. Need >= 4 takes and >= 2 labels."
                else -> "$takes takes, $labels labels"
            }
            cnt.text = msg
        }
        refreshCount()

        st.text = if (c.hasHeadTracker) "Head tracker HID active" else "Phone IMU"

        lifecycleScope.launch {
            c.motionFlow.collect { f ->
                if (cap) buf.add(floatArrayOf(*f.acceleration, *f.rotationRate, *f.rotationVector))
            }
        }

        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .setDelegate(Delegate.CPU).build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setNumFaces(1)
            .setResultListener { result: FaceLandmarkerResult, _: MPImage ->
                val face = result.faceLandmarks().firstOrNull()
                runOnUiThread {
                    if (face != null) {
                        lipOverlay.landmarks = face
                        camHint.text = "Face detected! Mouth a word."
                        camHint.setTextColor(0xFF00FF00.toInt()) // Green
                        st.text = "Camera active. Face detected."
                    } else {
                        lipOverlay.landmarks = null
                        camHint.text = "Center your face in the frame"
                        camHint.setTextColor(0xFFFFFFFF.toInt()) // White
                        st.text = "Camera active. Looking for face..."
                    }
                }
            }
            .setErrorListener { e ->
                runOnUiThread { st.text = "MediaPipe error: ${e.message}" }
            }
            .build()

        try {
            faceLandmarker = FaceLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            st.text = "FaceLandmarker init failed: ${e.message}"
        }

        camBtn.setOnClickListener {
            if (!isCameraOn) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQ_CAMERA)
                } else {
                    startCamera(st)
                }
            } else {
                stopCamera(st)
            }
        }

        rec.setOnClickListener {
            if (!cap) {
                buf.clear(); cap = true
                rec.text = "Stop & Save"; st.text = "Recording..."
            } else {
                cap = false; rec.text = "Record Reference"
                val l = inp.text?.toString()?.ifEmpty { "unnamed" } ?: "unnamed"
                val n = clf.normalize(buf)
                val all = r.loadTemplates().toMutableList()
                all.add(TemplateRecord(l, n.map { it.toList() }))
                r.saveTemplates(all)
                st.text = "Saved ${n.size} frames for '$l'"
                refreshCount()
            }
        }

        trn.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
                st.text = "Grant microphone permission, then tap Transcribe again."
                return@setOnClickListener
            }
            st.text = "Loading Vosk model..."
            lifecycleScope.launch {
                if (!at.ensureModel()) {
                    st.text = "Vosk model not installed."
                    return@launch
                }
                st.text = "Listening (3s)..."
                val text = at.transcribe3s().trim()
                if (text.isEmpty()) st.text = "No speech detected."
                else {
                    inp.setText(text)
                    st.text = "Transcribed: $text (edit if wrong, then Record)"
                }
            }
        }

        dec.setOnClickListener {
            if (!cap) {
                buf.clear(); cap = true
                dec.text = "Stop & Classify"; st.text = "Listening..."
            } else {
                cap = false; dec.text = "Run Classification"
                val ts = r.loadTemplates()
                if (ts.isEmpty()) st.text = "No templates recorded."
                else {
                    val res = clf.classify(buf, ts)
                    st.text = if (res.accepted) "-> ${res.label} d=%.2f".format(res.distance)
                             else "Rejected: d=%.2f exceeds threshold.".format(res.distance)
                }
            }
        }

        eva.setOnClickListener {
            val ts = r.loadTemplates()
            if (ts.size < 4 || ts.map { it.label }.distinct().size < 2) {
                st.text = "Need >= 4 takes and >= 2 labels."; return@setOnClickListener
            }
            st.text = "Evaluating LOO + ${Evaluation.DEFAULT_PERMUTATIONS} permutations..."
            prog.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.Default) {
                val res = Evaluation.run(ts)
                withContext(Dispatchers.Main) {
                    prog.visibility = View.GONE
                    st.text = res.pretty()
                }
            }
        }

        exp.setOnClickListener {
            try {
                val f = File(cacheDir, "subvox_dataset.json")
                f.writeText(r.exportJson())
                val uri = FileProvider.getUriForFile(this, "$APP_ID.fileprovider", f)
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(i, "Export Subvox dataset"))
            } catch (e: Exception) { st.text = "Export failed: ${e.message}" }
        }

        clr.setOnClickListener {
            r.clearTemplates()
            refreshCount()
            st.text = "Dataset cleared."
        }
    }

    private fun startCamera(st: TextView) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        if (faceLandmarker != null) {
                            val bitmap = imageProxy.toBitmap()
                            val mpImage = BitmapImageBuilder(bitmap).build()
                            faceLandmarker?.detectAsync(mpImage, System.currentTimeMillis())
                        }
                    } catch (e: Exception) {
                        runOnUiThread { st.text = "Analyzer error: ${e.message}" }
                    } finally {
                        imageProxy.close()
                    }
                }
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(this, cameraSelector, preview, analysis)
                previewView.visibility = View.VISIBLE
                isCameraOn = true
                camHint.text = "Looking for face..."
                st.text = "Camera active. Looking for face..."
            } catch (e: Exception) {
                st.text = "Camera start failed: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera(st: TextView) {
        cameraProvider?.unbindAll()
        previewView.visibility = View.GONE
        lipOverlay.landmarks = null
        isCameraOn = false
        camHint.text = "Tap 'Start Camera' to begin"
        st.text = "Camera stopped."
    }

    override fun onResume() { super.onResume(); c.startListening() }
    override fun onPause() { super.onPause(); c.stopListening() }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        faceLandmarker?.close()
    }
}
