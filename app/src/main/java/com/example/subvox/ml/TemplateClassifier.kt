
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

/**
 * z-scored 1-NN template matcher with margin rejection. Per-sequence z-scoring
 * removes amplitude variation before DTW, matching the upstream preprocessing
 * step. The acceptance gate mirrors upstream maxDistance/minConfidence.
 */
class TemplateClassifier(
    val maxDistance: Float = Float.MAX_VALUE,
    val minConfidence: Float = 0f
) {
    /**
     * Standardize each channel of a sequence to zero mean and unit variance.
     * If a channel is constant, std is replaced by 1 to avoid division by zero.
     */
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

    /**
     * 1-NN search over stored templates. The runner-up distance is tracked so
     * the margin (second - best)/second can reject ambiguous matches.
     */
    fun classify(unknown: List<FloatArray>, ts: List<TemplateRecord>): ClassificationResult {
        val n = normalize(unknown)
        var best = Float.MAX_VALUE
        var second = Float.MAX_VALUE
        var label = "UNKNOWN"
        for (t in ts) {
            val d = DynamicTimeWarping.computeDistance(n, t.features.map { it.toFloatArray() })
            if (d < best) { second = best; best = d; label = t.label }
            else if (d < second) { second = d }
        }
        val margin = if (second.isFinite() && second > 0f) (second - best) / second else 1f
        val accepted = best <= maxDistance && margin >= minConfidence
        return ClassificationResult(label, best, second, margin, accepted)
    }
}
