
package com.example.subvox.ml
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Dynamic Time Warping with a Sakoe-Chiba band. The band bounds the warping
 * path to |i - j| <= w, reducing O(n*m) to O(n*w) and preventing pathological
 * alignments; the 15% band follows the original spoken-word formulation
 * [reference:6].
 */
object DynamicTimeWarping {
    /**
     * Compute DTW distance between two multi-dimensional sequences.
     * @param bandFraction fraction of the longer sequence used as band width.
     * The band is the Sakoe-Chiba constraint from the 1978 paper [reference:7].
     */
    fun computeDistance(a: List<FloatArray>, b: List<FloatArray>, bandFraction: Float = 0.15f): Float {
        val n = a.size; val m = b.size
        if (n == 0 || m == 0) return Float.MAX_VALUE
        val w = max(1, (bandFraction * max(n, m)).toInt())
        val d = Array(n + 1) { FloatArray(m + 1) { Float.MAX_VALUE } }
        d[0][0] = 0f
        for (i in 1..n) for (j in max(1, i - w)..min(m, i + w)) {
            val c = euclid(a[i - 1], b[j - 1])
            d[i][j] = c + min(d[i - 1][j], min(d[i][j - 1], d[i - 1][j - 1]))
        }
        return d[n][m]
    }
    /** Euclidean distance between two feature vectors; DTW local cost. */
    private fun euclid(x: FloatArray, y: FloatArray): Float {
        var s = 0f
        for (i in 0 until min(x.size, y.size)) { val d = x[i] - y[i]; s += d * d }
        return sqrt(s)
    }
}
