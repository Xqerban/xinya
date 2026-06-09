package com.xinya.dtx.core.speech

import org.json.JSONObject
import org.json.JSONTokener

object MscResultParser {
    fun parseIatResult(json: String): String {
        val result = StringBuilder()
        runCatching {
            val root = JSONObject(JSONTokener(json))
            val words = root.getJSONArray("ws")
            for (i in 0 until words.length()) {
                val items = words.getJSONObject(i).getJSONArray("cw")
                if (items.length() == 0) continue
                result.append(items.getJSONObject(0).optString("w"))
            }
        }
        return result.toString()
    }
}
