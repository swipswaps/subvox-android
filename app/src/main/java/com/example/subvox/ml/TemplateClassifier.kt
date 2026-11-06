
package com.example.subvox.ml
import com.example.subvox.data.TemplateRecord
import kotlin.math.sqrt

data class ClassificationResult(
    val label: String,
    val distance: Float,
    val runnerUpDistance: Float,
    val margin: Float,
    val accepted: Boolean
)

class TemplateClassifier(
    val maxDistance: Float = Float.MAX_VALUE,
    val minConfidence: Float = 0f
) {
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
    fun classify(unknown: List<FloatArray>, ts: List<TemplateRecord>): ClassificationResult {
        val n = normalize(unknown)
        var best = Float.MAX_VALUE; var second = Float.MAX_VALUE; var label = "UNKNOWN"
        for (t in ts) {
            val d = DynamicTimeWarping.computeDistance(n, t.features.map { it.toFloatArray() })
            if (d < best) { second = best; best = d; label = t.label }
            else if (d < second) { second = d }
        }
        val margin = if (second.isFinite() && second > 0f) (second - best) / second else 1f
        return ClassificationResult(label, best, second, margin, best <= maxDistance && margin >= minConfidence)
    }
}
