package com.monuk7735.nope.remote.repository

import android.content.Context
import androidx.lifecycle.MutableLiveData
import com.monuk7735.nope.remote.infrared.IrCsvParser
import com.monuk7735.nope.remote.models.retrofit.DeviceBrandsRetrofitModel
import com.monuk7735.nope.remote.models.retrofit.DeviceCodesRetrofitModel
import com.monuk7735.nope.remote.models.retrofit.DeviceTypesRetrofitModel

/** Small bundled source for remotes that do not require a database download. */
class BuiltInRemoteRepository internal constructor(private val readProfiles: () -> String) :
    IRSourceRepository {

    constructor(context: Context) : this({
        context.assets.open("foxsky_tv.psv").bufferedReader().use { it.readText() }
    })

    override fun isRepoInstalled() = true

    override suspend fun getTypes() = listOf(DeviceTypesRetrofitModel("TV"))

    override suspend fun getBrands(type: String) = if (type.equals("TV", ignoreCase = true)) {
        listOf(DeviceBrandsRetrofitModel("TV", "Foxsky"))
    } else {
        emptyList()
    }

    override suspend fun getCodes(
        type: String,
        brand: String,
        progress: MutableLiveData<Pair<Int, Int>?>?,
    ): List<DeviceCodesRetrofitModel> {
        if (!type.equals("TV", ignoreCase = true) || !brand.equals("Foxsky", ignoreCase = true)) {
            return emptyList()
        }
        return parseProfiles(readProfiles())
    }

    companion object {
        /** Rows contain model, carrier frequency, button name and microsecond timings. */
        internal fun parseProfiles(data: String): List<DeviceCodesRetrofitModel> {
            val profiles = linkedMapOf<String, Pair<Int, MutableMap<String, String>>>()
            data.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                val fields = line.split('|')
                require(fields.size == 4) { "Invalid Foxsky profile row" }
                val (model, frequencyText, name, timingsText) = fields
                val frequency = frequencyText.toInt()
                val timings = timingsText.split(',').map { it.toInt() }
                require(frequency > 0 && timings.size >= 4 && timings.size % 2 == 0)
                require(timings.all { it > 0 })
                val profile = profiles.getOrPut(model) { frequency to linkedMapOf() }
                require(profile.first == frequency && name !in profile.second)
                profile.second[name] = IrCsvParser.encodeToProntoHex(frequency, timings)
            }
            return profiles.map { (model, profile) ->
                DeviceCodesRetrofitModel("TV", "Foxsky", profile.second, model)
            }
        }
    }
}
