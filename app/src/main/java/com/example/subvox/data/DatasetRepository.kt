
package com.example.subvox.data
import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * One take: a label and a list of feature rows. List<Float> (not FloatArray)
 * is used so the generated serializer is portable across Kotlin versions;
 * @Serializable is the supported entry point for automatic JSON mapping.
 */
@Serializable
data class TemplateRecord(val label: String, val features: List<List<Float>>)

class DatasetRepository(ctx: Context) {
    private val f = File(ctx.filesDir, "subvox_dataset.json")
    private val j = Json { ignoreUnknownKeys = true; prettyPrint = true }
    fun saveTemplates(t: List<TemplateRecord>) = f.writeText(j.encodeToString(t))
    /** Load templates; a corrupt or partial file returns an empty list, never crashes. */
    fun loadTemplates(): List<TemplateRecord> =
        if (!f.exists()) emptyList()
        else runCatching { j.decodeFromString<List<TemplateRecord>>(f.readText()) }.getOrDefault(emptyList())
}
