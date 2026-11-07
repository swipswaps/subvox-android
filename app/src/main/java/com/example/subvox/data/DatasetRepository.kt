package com.example.subvox.data
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class TemplateRecord(val label: String, val features: List<List<Float>>)

class DatasetRepository(ctx: Context) {
    private val f = File(ctx.filesDir, "subvox_dataset.json")
    private val j = Json { ignoreUnknownKeys = true; prettyPrint = true }

    fun saveTemplates(t: List<TemplateRecord>) = f.writeText(j.encodeToString(t))
    fun loadTemplates(): List<TemplateRecord> =
        if (!f.exists()) emptyList()
        else runCatching { j.decodeFromString<List<TemplateRecord>>(f.readText()) }
            .getOrDefault(emptyList())
    fun clearTemplates() { if (f.exists()) f.delete() }
    fun count(): Pair<Int, Int> {
        val t = loadTemplates()
        return Pair(t.size, t.map { it.label }.distinct().size)
    }
    fun exportJson(): String = j.encodeToString(loadTemplates())
}
