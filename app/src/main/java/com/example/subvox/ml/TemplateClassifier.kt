
package com.example.subvox.ml
import com.example.subvox.data.TemplateRecord
import kotlin.math.sqrt

class TemplateClassifier {
    fun normalize(frames: List<FloatArray>): List<FloatArray> {
        if (frames.isEmpty()) return frames
        val dims = frames[0].size
        val mean = FloatArray(dims); val std = FloatArray(dims)
        for (k in 0 until dims) {
            mean[k] = (frames.sumOf { it[k].toDouble() } / frames.size).toFloat()
            std[k] = sqrt(frames.sumOf { (it[k] - mean[k]).toDouble() * (it[k] - mean[k]) } / frames.size)
                .toFloat().let { if (it == 0f) 1f else it }
        }
        return frames.map { f -> FloatArray(dims) { k -> (f[k] - mean[k]) / std[k] } }
    }
    fun classify(u: List<FloatArray>, ts: List<TemplateRecord>): Pair<String, Float> {
        val n = normalize(u); var label = "UNKNOWN"; var best = Float.MAX_VALUE
        for (t in ts) { val d = DynamicTimeWarping.computeDistance(n, t.features.map { it.toFloatArray() })
            if (d < best) { best = d; label = t.label } }
        return label to best
    }
}
