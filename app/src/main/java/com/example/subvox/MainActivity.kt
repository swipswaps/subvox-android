
package com.example.subvox
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.subvox.data.DatasetRepository
import com.example.subvox.data.TemplateRecord
import com.example.subvox.ml.Evaluation
import com.example.subvox.ml.TemplateClassifier
import com.example.subvox.sensor.MotionStreamCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {
    private lateinit var c: MotionStreamCollector
    private lateinit var r: DatasetRepository
    private val clf = TemplateClassifier()
    private val buf = mutableListOf<FloatArray>()
    private var cap = false

    /**
     * BLUETOOTH_CONNECT is a runtime permission on API 31+; without it the
     * head-tracker HID sensor can remain hidden [reference:10].
     */
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        c = MotionStreamCollector(this)
        r = DatasetRepository(this)
        if (Build.VERSION.SDK_INT >= 31 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 101
            )
        }

        val st  = findViewById<TextView>(R.id.statusText)
        val inp = findViewById<EditText>(R.id.inputLabel)
        val rec = findViewById<Button>(R.id.recordButton)
        val dec = findViewById<Button>(R.id.classifyButton)
        val eva = findViewById<Button>(R.id.evaluateButton)

        st.text = if (c.hasHeadTracker) "Head tracker HID active" else "Phone IMU (no head tracker HID)"

        lifecycleScope.launch {
            c.motionFlow.collect { f ->
                if (cap) buf.add(floatArrayOf(*f.acceleration, *f.rotationRate, *f.rotationVector))
            }
        }

        rec.setOnClickListener {
            if (!cap) { buf.clear(); cap = true; rec.text = "Stop & Save"; st.text = "Recording..." }
            else {
                cap = false; rec.text = "Record Reference"
                val l = inp.text.toString().ifEmpty { "unnamed" }
                val n = clf.normalize(buf)
                val all = r.loadTemplates().toMutableList()
                all.add(TemplateRecord(l, n.map { it.toList() }))
                r.saveTemplates(all)
                st.text = "Saved ${n.size} frames for '$l'"
            }
        }

        dec.setOnClickListener {
            if (!cap) { buf.clear(); cap = true; dec.text = "Stop & Classify"; st.text = "Listening..." }
            else {
                cap = false; dec.text = "Run Classification"
                val ts = r.loadTemplates()
                if (ts.isEmpty()) st.text = "No templates recorded."
                else {
                    val res = clf.classify(buf, ts)
                    st.text = if (res.accepted)
                        "-> ${res.label} d=%.2f margin=%.2f".format(res.distance, res.margin)
                    else
                        "rejected best=${res.label} d=%.2f margin=%.2f".format(res.distance, res.margin)
                }
            }
        }

        eva.setOnClickListener {
            val ts = r.loadTemplates()
            if (ts.size < 4) { st.text = "Need >= 4 takes."; return@setOnClickListener }
            if (ts.map { it.label }.distinct().size < 2) { st.text = "Need >= 2 distinct labels."; return@setOnClickListener }
            st.text = "Evaluating LOO + ${Evaluation.DEFAULT_PERMUTATIONS} permutations..."
            lifecycleScope.launch(Dispatchers.Default) {
                val res = Evaluation.run(ts)
                withContext(Dispatchers.Main) { st.text = res.pretty() }
            }
        }
    }
    override fun onResume() { super.onResume(); c.startListening() }
    override fun onPause()  { super.onPause();  c.stopListening()  }
}
