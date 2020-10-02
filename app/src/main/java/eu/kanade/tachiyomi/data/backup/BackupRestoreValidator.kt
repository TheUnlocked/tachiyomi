package eu.kanade.tachiyomi.data.backup

import android.content.Context
import android.net.Uri
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.backup.models.Backup
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.source.SourceManager
import uy.kohesive.injekt.injectLazy

object BackupRestoreValidator {

    private val sourceManager: SourceManager by injectLazy()
    private val trackManager: TrackManager by injectLazy()

    /**
     * Checks for critical backup file data.
     *
     * @throws Exception if version or manga cannot be found.
     * @return List of missing sources or missing trackers.
     */
    fun validate(context: Context, uri: Uri): Results {
        val reader = JsonReader(context.contentResolver.openInputStream(uri)!!.bufferedReader())
        val json = JsonParser.parseReader(reader).asJsonObject

        val version = json.get(Backup.VERSION)
        val mangasJson = json.get(Backup.MANGAS)
        if (version == null || mangasJson == null) {
            throw Exception(context.getString(R.string.invalid_backup_file_missing_data))
        }

        val mangas = mangasJson.asJsonArray
        if (mangas.size() == 0) {
            throw Exception(context.getString(R.string.invalid_backup_file_missing_manga))
        }

        val sources = getSourceMapping(json)
        val missingSources = sources
            .filter { sourceManager.get(it.key) == null }
            .values
            .sorted()

        val trackers = mangas
            .filter { it.asJsonObject.has("track") }
            .flatMap { it.asJsonObject["track"].asJsonArray }
            .map { it.asJsonObject["s"].asInt }
            .distinct()
        val missingTrackers = trackers
            .mapNotNull { trackManager.getService(it) }
            .filter { !it.isLogged }
            .map { it.name }
            .sorted()

        return Results(missingSources, missingTrackers)
    }

    fun getSourceMapping(json: JsonObject): Map<Long, String> {
        val extensionsMapping = json.get(Backup.EXTENSIONS) ?: return emptyMap()

        return extensionsMapping.asJsonArray
            .map {
                val items = it.asString.split(":")
                items[0].toLong() to items[1]
            }
            .toMap()
    }

    data class Results(val missingSources: List<String>, val missingTrackers: List<String>)
}
