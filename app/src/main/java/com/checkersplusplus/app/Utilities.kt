package com.checkersplusplus.app

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

object StorageUtil {
    private const val PREF_NAME = "CheckersPlusPlusPreferences"
    private lateinit var sharedPreferences: SharedPreferences

    fun init(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    fun saveData(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun getData(key: String, defaultValue: String = ""): String {
        return sharedPreferences.getString(key, defaultValue) ?: defaultValue
    }
}

object ResponseUtil {
    fun parseJson(content: String): Map<String, String> {
        val jsonObject = JSONObject(content)
        val map = mutableMapOf<String, String>()

        jsonObject.keys().forEach {
            map[it] = jsonObject.getString(it)
        }

        return map
    }
}