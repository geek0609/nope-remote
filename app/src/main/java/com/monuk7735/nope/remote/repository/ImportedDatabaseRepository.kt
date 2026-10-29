package com.monuk7735.nope.remote.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.AtomicFile
import androidx.lifecycle.MutableLiveData
import com.monuk7735.nope.remote.infrared.IrCsvParser
import com.monuk7735.nope.remote.models.retrofit.DeviceBrandsRetrofitModel
import com.monuk7735.nope.remote.models.retrofit.DeviceCodesRetrofitModel
import com.monuk7735.nope.remote.models.retrofit.DeviceTypesRetrofitModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Imports only profiles whose signal format is understood; the original data stays on device. */
class ImportedDatabaseRepository(private val context: Context) : IRSourceRepository {
    companion object {
        private const val MAX_BYTES = 128L * 1024 * 1024
        private const val SHAPE = "p.type=1 AND length(p.param)=33 AND " +
            "hex(substr(p.param,1,2))='0004' AND " +
            "hex(substr(p.param,5,2))='0100' AND " +
            "hex(substr(p.param,17,3))='010004' AND " +
            "hex(substr(p.param,24,4))='00000106'"
        private fun databaseFile(context: Context) = File(context.filesDir, "imported-ir.db")

        /** Import into a temporary atomic file; leave the previous database intact on error. */
        suspend fun import(context: Context, uri: Uri): Int = withContext(Dispatchers.IO) {
            val pending = File.createTempFile("ir-import-", ".db", context.cacheDir)
            try {
                val input =
                    requireNotNull(context.contentResolver.openInputStream(uri)) {
                        "Cannot open file"
                    }
                input.use { source ->
                    pending.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = source.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= MAX_BYTES) { "Database exceeds 128 MB" }
                            output.write(buffer, 0, n)
                        }
                    }
                }
                val count = SQLiteDatabase.openDatabase(
                    pending.path,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                ).use { db ->
                    db.rawQuery("PRAGMA quick_check(1)", null).use { c ->
                        require(c.moveToFirst() && c.getString(0) == "ok") { "Invalid database" }
                    }
                    // Referencing every required column also rejects unrelated SQLite databases.
                    db.rawQuery(
                        "SELECT b.name, b.lang_code, m.device_type_id, " +
                            "m.brand_id, " +
                            "m.remote_id, p.frequency, p.param, k.function_id, " +
                            "k.pulse_data, " +
                            "f.display_name FROM RcCountryBrand b JOIN " +
                            "RcBrandRemoteMap m " +
                            "ON b.brand_id=m.brand_id JOIN RcRemoteController p " +
                            "ON p.remote_id=m.remote_id " +
                            "JOIN RcRemoteKey k ON k.remote_id=p.remote_id " +
                            "LEFT JOIN RcCountryFunctions f ON " +
                            "f.function_id=k.function_id LIMIT 1",
                        null,
                    ).use { it.count }
                    db.rawQuery(
                        "SELECT count(*) FROM RcRemoteController p WHERE $SHAPE",
                        null,
                    ).use { c ->
                        c.moveToFirst()
                        c.getInt(0)
                    }
                }
                require(count > 0) { "No supported signal profiles found" }
                val atomic = AtomicFile(databaseFile(context))
                val output = atomic.startWrite()
                try {
                    pending.inputStream().use { it.copyTo(output) }
                    atomic.finishWrite(output)
                } catch (e: Exception) {
                    atomic.failWrite(output)
                    throw e
                }
                count
            } finally {
                pending.delete()
            }
        }
    }

    override fun isRepoInstalled(): Boolean = runCatching {
        AtomicFile(databaseFile(context)).openRead().use { }
        true
    }.getOrDefault(false)

    private inline fun <T> database(block: (SQLiteDatabase) -> T): T = SQLiteDatabase.openDatabase(
        databaseFile(context).path,
        null,
        SQLiteDatabase.OPEN_READONLY,
    ).use(block)

    private fun typeName(id: Int): String = when (id) {
        1 -> "Set-top box"
        2 -> "TV"
        5 -> "Air conditioner"
        9 -> "Camera"
        else -> "Device type $id"
    }
    private fun typeId(name: String): Int? = when (name) {
        "Set-top box" -> 1
        "TV" -> 2
        "Air conditioner" -> 5
        "Camera" -> 9
        else -> name.removePrefix("Device type ").toIntOrNull()
    }

    override suspend fun getTypes(): List<DeviceTypesRetrofitModel> = withContext(Dispatchers.IO) {
        database { db ->
            db.rawQuery(
                "SELECT DISTINCT m.device_type_id FROM " +
                    "RcBrandRemoteMap m " +
                    "JOIN RcRemoteController p ON " +
                    "p.remote_id=m.remote_id WHERE $SHAPE",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) add(DeviceTypesRetrofitModel(typeName(c.getInt(0))))
                }
            }
        }
    }

    override suspend fun getBrands(type: String): List<DeviceBrandsRetrofitModel> =
        withContext(Dispatchers.IO) {
            val id = typeId(type) ?: return@withContext emptyList()
            database { db ->
                db.rawQuery(
                    "SELECT DISTINCT b.name FROM RcCountryBrand b " +
                        "JOIN RcBrandRemoteMap m " +
                        "ON m.brand_id=b.brand_id JOIN " +
                        "RcRemoteController p ON p.remote_id=m.remote_id " +
                        "WHERE b.lang_code='en' AND " +
                        "m.device_type_id=? AND $SHAPE",
                    arrayOf(id.toString()),
                ).use { c ->
                    buildSet {
                        while (c.moveToNext()) {
                            val name = runCatching {
                                ImportedSignalDecoder.decodeText(c.getString(0))
                            }.getOrNull()
                            if (!name.isNullOrBlank()) add(name)
                        }
                    }.sorted().map { DeviceBrandsRetrofitModel(type, it) }
                }
            }
        }

    override suspend fun getCodes(
        type: String,
        brand: String,
        progress: MutableLiveData<Pair<Int, Int>?>?,
    ): List<DeviceCodesRetrofitModel> = withContext(Dispatchers.IO) {
        val id = typeId(type) ?: return@withContext emptyList()
        database { db ->
            val brandIds = db.rawQuery(
                "SELECT brand_id,name FROM RcCountryBrand " +
                    "WHERE lang_code='en'",
                null,
            ).use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val name = runCatching {
                            ImportedSignalDecoder.decodeText(c.getString(1))
                        }.getOrNull()
                        if (name.equals(brand, ignoreCase = true)) add(c.getString(0))
                    }
                }
            }
            val profiles = linkedMapOf<String, MutableMap<String, String>>()
            for (brandId in brandIds) {
                val query = "SELECT DISTINCT " +
                    "p.remote_id,p.frequency,p.param,k.pulse_data, " +
                    "f.display_name,k.function_id " +
                    "FROM RcBrandRemoteMap m JOIN " +
                    "RcRemoteController p ON p.remote_id=m.remote_id " +
                    "JOIN RcRemoteKey k ON " +
                    "k.remote_id=p.remote_id LEFT JOIN RcCountryFunctions f " +
                    "ON f.function_id=k.function_id AND f.lang_code='en' " +
                    "WHERE m.device_type_id=? AND m.brand_id=? AND $SHAPE " +
                    "AND length(k.pulse_data) BETWEEN 1 AND 1024"
                db.rawQuery(query, arrayOf(id.toString(), brandId)).use { c ->
                    while (c.moveToNext()) {
                        val frequency = c.getInt(1)
                        if (frequency !in 10000..100000) continue
                        val payload =
                            runCatching {
                                ImportedSignalDecoder.decodeBytes(
                                    c.getBlob(3),
                                ).toString(Charsets.US_ASCII)
                            }.getOrNull()
                                ?: continue
                        val cycles = ImportedSignalDecoder.cycles(c.getBlob(2), payload) ?: continue
                        val timings = cycles.map { it * (1000000 / frequency) }
                        val name = (
                            c.getString(
                                4,
                            ) ?: "Key ${c.getInt(5)}"
                            ).uppercase().replace('_', ' ')
                        val remoteId = c.getString(0)
                        val model = runCatching {
                            ImportedSignalDecoder.decodeText(remoteId)
                        }.getOrDefault(remoteId)
                        profiles.getOrPut(model) {
                            linkedMapOf()
                        }.putIfAbsent(name, IrCsvParser.encodeToProntoHex(frequency, timings))
                    }
                }
            }
            profiles.map { (model, codes) -> DeviceCodesRetrofitModel(type, brand, codes, model) }
        }
    }
}
