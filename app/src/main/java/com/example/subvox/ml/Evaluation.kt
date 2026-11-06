
package com.example.subvox.ml

import com.example.subvox.data.TemplateRecord
import kotlin.math.sqrt
import kotlin.random.Random

data class ConfusionCell(val actual: String, val predicted: String, val count: Int)

data class EvaluationResult(
    val n: Int, val classes: List<String>, val looAccuracy: Double,
    val chanceMajority: Double, val chanceUniform: Double,
    val permutations: Int, val pValue: Double, val nullMean: Double,
    val nullSd: Double, val confusion: List<ConfusionCell>
) {
    fun pretty(): String = buildString {
        append("N = %d takes, k = %d classes\n".format(n, classes.size))
        append("LOO accuracy    = %.3f\n".format(looAccuracy))
        append("chance majority = %.3f\n".format(chanceMajority))
        append("chance uniform  = %.3f\n".format(chanceUniform))
        append("null (shuffled) = %.3f +/- %.3f over %d permutations\n".format(nullMean, nullSd, permutations))
        append("p-value         = %.4f [%s]\n".format(pValue, if (pValue < 0.05) "p < 0.05, above chance" else "n.s."))
        append("confusion (actual -> predicted):\n")
        for (c in confusion) append("  %-10s -> %-10s %d\n".format(c.actual, c.predicted, c.count))
    }
}

/**
 * LOO + 500x label-permutation. p uses the (1+ge)/(1+B) Monte Carlo correction
 * (Phipson & Smyth 2010, DOI:10.2202/1544-6115.1585). The N x N DTW matrix is
 * computed once because DTW distance depends only on the feature sequences,
 * not on labels — shuffling labels changes only the argmin lookup.
 */
object Evaluation {
    const val DEFAULT_PERMUTATIONS = 500

    fun distanceMatrix(records: List<TemplateRecord>): Array<FloatArray> {
        val n = records.size
        val m = Array(n) { FloatArray(n) }
        for (i in 0 until n) for (j in i + 1 until n) {
            val d = DynamicTimeWarping.computeDistance(
                records[i].features.map { it.toFloatArray() },
                records[j].features.map { it.toFloatArray() })
            m[i][j] = d; m[j][i] = d
        }
        return m
    }

    fun leaveOneOutPredictions(dist: Array<FloatArray>, labels: IntArray): IntArray {
        val n = labels.size; val pred = IntArray(n) { -1 }
        for (i in 0 until n) {
            var best = Float.MAX_VALUE; var bestLabel = -1
            for (j in 0 until n) {
                if (i == j) continue
                if (dist[i][j] < best) { best = dist[i][j]; bestLabel = labels[j] }
            }
            pred[i] = bestLabel
        }
        return pred
    }

    fun accuracy(pred: IntArray, truth: IntArray): Double {
        if (truth.isEmpty()) return 0.0
        var ok = 0; for (i in truth.indices) if (pred[i] == truth[i]) ok++
        return ok.toDouble() / truth.size
    }

    fun run(records: List<TemplateRecord>, permutations: Int = DEFAULT_PERMUTATIONS, seed: Long = 0xC0FFEEL): EvaluationResult {
        require(records.size >= 4) { "need at least 4 takes" }
        val names = records.map { it.label }.distinct().sorted()
        require(names.size >= 2) { "need at least 2 distinct labels" }
        val idx = names.withIndex().associate { (i, s) -> s to i }
        val truth = IntArray(records.size) { idx.getValue(records[it].label) }
        val dist = distanceMatrix(records)
        val predObs = leaveOneOutPredictions(dist, truth)
        val accObs = accuracy(predObs, truth)
        val counts = IntArray(names.size); for (t in truth) counts[t]++
        val chanceMajority = counts.max().toDouble() / records.size
        val chanceUniform = 1.0 / names.size
        val rng = Random(seed); val nullAcc = DoubleArray(permutations)
        val shuffled = IntArray(truth.size); var ge = 0
        for (b in 0 until permutations) {
            truth.copyInto(shuffled)
            for (i in shuffled.size - 1 downTo 1) {
                val k = rng.nextInt(i + 1)
                val t = shuffled[i]; shuffled[i] = shuffled[k]; shuffled[k] = t
            }
            val accB = accuracy(leaveOneOutPredictions(dist, shuffled), shuffled)
            nullAcc[b] = accB
            if (accB >= accObs - 1e-12) ge++
        }
        val mean = nullAcc.average()
        val sd = sqrt(nullAcc.sumOf { (it - mean) * (it - mean) } / permutations)
        val p = (1.0 + ge) / (1.0 + permutations)
        val confusion = ArrayList<ConfusionCell>()
        for (a in names.indices) for (b in names.indices) {
            var c = 0; for (i in truth.indices) if (truth[i] == a && predObs[i] == b) c++
            if (c > 0) confusion.add(ConfusionCell(names[a], names[b], c))
        }
        return EvaluationResult(records.size, names, accObs, chanceMajority,
            chanceUniform, permutations, p, mean, sd, confusion)
    }
}
