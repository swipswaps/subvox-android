
package com.example.subvox
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.subvox.data.DatasetRepository
import com.example.subvox.data.TemplateRecord
import com.example.subvox.ml.TemplateClassifier
import com.example.subvox.sensor.MotionStreamCollector
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var c: MotionStreamCollector
    private lateinit var r: DatasetRepository
    private val clf = TemplateClassifier()
    private val buf = mutableListOf<FloatArray>()
    private var cap = false

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_main)
        c = MotionStreamCollector(this)
        r = DatasetRepository(this)
        val st  = findViewById<TextView>(R.id.statusText)
        val inp = findViewById<EditText>(R.id.inputLabel)
        val rec = findViewById<Button>(R.id.recordButton)
        val dec = findViewById<Button>(R.id.classifyButton)
        lifecycleScope.launch {
            c.motionFlow.collect { f ->
                if (cap) buf.add(floatArrayOf(*f.acceleration, *f.rotationRate, *f.rotationVector))
            }
        }
        rec.setOnClickListener {
            if (!cap) { buf.clear(); cap = true; rec.text = "Stop & Save"; st.text = "Recording…" }
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
            if (!cap) { buf.clear(); cap = true; dec.text = "Stop & Classify"; st.text = "Listening…" }
            else {
                cap = false; dec.text = "Run Classification"
                val ts = r.loadTemplates()
                if (ts.isEmpty()) st.text = "No templates recorded."
                else {
                    val (l, d) = clf.classify(buf, ts)
                    st.text = "→ $l (DTW = %.2f)".format(d)
                }
            }
        }
    }
    override fun onResume() { super.onResume(); c.startListening() }
    override fun onPause()  { super.onPause();  c.stopListening()  }
}
