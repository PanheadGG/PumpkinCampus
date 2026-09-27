package com.pgigi.pumpkincampus.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** JSON 序列化工具（移植自 Pumpkin-Toolkit）。 */
object JsonUtil {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun <T> toJson(value: T, serializer: KSerializer<T>): String {
        return json.encodeToString(serializer, value)
    }

    fun <T> parseJson(jsonString: String, serializer: KSerializer<T>): T {
        return json.decodeFromString(serializer, jsonString)
    }

    fun <T> toListJson(list: List<T>, serializer: KSerializer<T>): String {
        val listSerializer = ListSerializer(serializer)
        return json.encodeToString(listSerializer, list)
    }

    fun <T> parseListJson(jsonString: String, serializer: KSerializer<T>): List<T> {
        val listSerializer = ListSerializer(serializer)
        return json.decodeFromString(listSerializer, jsonString)
    }
}
