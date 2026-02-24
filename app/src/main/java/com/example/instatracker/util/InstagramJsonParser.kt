package com.example.instatracker.util

import com.google.gson.JsonParser
import java.io.InputStream

object InstagramJsonParser {
    fun parseFollowersJson(input: InputStream): List<String> {
        val text = input.bufferedReader().readText()
        return try {
            val arr = JsonParser.parseString(text).asJsonArray
            arr.mapNotNull { el ->
                val sld = el.asJsonObject.getAsJsonArray("string_list_data")
                if (sld != null && sld.size() > 0)
                    sld[0].asJsonObject.get("value")?.asString?.lowercase()?.trim()
                else null
            }.filter { it.isNotBlank() }.distinct()
        } catch (e: Exception) {
            parseSimpleList(text)
        }
    }

    fun parseSimpleList(text: String): List<String> =
        text.lines().map { it.trim().removePrefix("@").lowercase() }
            .filter { it.isNotBlank() }.distinct()
}
