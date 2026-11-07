package com.example.subvox.ml

import com.example.subvox.data.TemplateRecord
import org.junit.Assert.assertTrue
import org.junit.Test

class EvaluationTest {
    @Test
    fun testSeparableClassesYieldSignificantP() {
        // Generate two distinct synthetic classes to test the DTW + permutation logic
        val records = mutableListOf<TemplateRecord>()
        // Class A: flat line at y=0
        for (i in 0 until 5) {
            records.add(TemplateRecord("A", List(20) { listOf(0f, 0f, 0f) }))
        }
        // Class B: flat line at y=100
        for (i in 0 until 5) {
            records.add(TemplateRecord("B", List(20) { listOf(100f, 100f, 100f) }))
        }

        val result = Evaluation.run(records, permutations = 100)
        assertTrue("LOO accuracy should be 1.0 for perfect classes", result.looAccuracy >= 0.99)
        assertTrue("p-value should be significant (p < 0.05)", result.pValue < 0.05)
    }
}
