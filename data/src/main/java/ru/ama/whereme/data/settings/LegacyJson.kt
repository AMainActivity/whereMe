package ru.ama.whereme.data.settings

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.Strictness
import java.io.IOException
import ru.ama.whereme.domain.entity.SettingsDomModel
import ru.ama.whereme.domain.entity.SettingsUserInfoDomModel

/** Explicit fields avoid Gson's allocation of partially initialized Kotlin models. */
object LegacyJson {
    private val gson = GsonBuilder().setStrictness(Strictness.STRICT).create()
    private fun parse(raw: String): JsonObject = gson.fromJson(raw, JsonObject::class.java)
        ?: throw IOException("Empty legacy object")
    private fun JsonObject.string(key: String): String {
        val value = get(key) ?: throw IOException("Missing legacy field: $key")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
        return value.asString
    }
    private fun JsonObject.int(key: String): Int {
        val value = get(key) ?: throw IOException("Missing legacy field: $key")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber)
        val number = value.asBigDecimal
        require(number.stripTrailingZeros().scale() <= 0 &&
            number >= Int.MIN_VALUE.toBigDecimal() && number <= Int.MAX_VALUE.toBigDecimal())
        return number.toInt()
    }
    private fun JsonObject.bool(key: String): Boolean {
        val value = get(key) ?: throw IOException("Missing legacy field: $key")
        require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean)
        return value.asBoolean
    }
    private fun JsonObject.optionalString(key: String): String? =
        if (!has(key) || get(key).isJsonNull) null else string(key)

    fun settings(raw: String): SettingsDomModel = checked("settings") {
        val obj = parse(raw)
        val days = obj.getAsJsonArray("days").map { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
            value.asString
        }
        SettingsDomModel(days, obj.string("start"), obj.string("end"), obj.int("minDist"),
            obj.int("accuracy"), obj.int("timeOfWaitAccuracy"), obj.int("timeOfWorkingWM"), obj.bool("isEnable"))
    }

    fun profile(raw: String): SettingsUserInfoDomModel = checked("session") {
        val obj = parse(raw)
        SettingsUserInfoDomModel(obj.string("tokenJwt"), obj.int("posId"), obj.int("famId"),
            obj.optionalString("name"), obj.optionalString("url"), obj.bool("isActivate"))
    }

    private fun <T> checked(kind: String, block: () -> T): T = try { block() } catch (_: Exception) {
        // Never include raw JSON, profile links or tokens in the error.
        throw IOException("Cannot migrate/read $kind; original data retained")
    }
}
