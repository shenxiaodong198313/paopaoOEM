package com.paopaoime.aikeyboard.ime.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class McpStorage(private val context: Context) {
    fun listMcps(): List<McpEntry> {
        val json = runCatching {
            context.assets.open("mcp/mcp_list.json").bufferedReader().use { it.readText() }
        }.getOrElse {
            return emptyList()
        }
        val root = JSONObject(json)
        val array = root.optJSONArray("mcps") ?: JSONArray()
        val result = mutableListOf<McpEntry>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val name = obj.optString("name")
            val description = obj.optString("description")
            val capsArray = obj.optJSONArray("capabilities") ?: JSONArray()
            val caps = mutableListOf<String>()
            for (j in 0 until capsArray.length()) {
                caps.add(capsArray.optString(j))
            }
            result.add(McpEntry(name, description, caps))
        }
        return result
    }
}
