package com.notandrej.wristradio.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Local favourites, stored as one JSON array in the app's own preferences.
 *
 * A watch has no account and this app has no server, so "saved" simply means
 * "written to this device". The whole set is small enough to hold in memory and
 * republish as a [StateFlow] for the UI.
 */
object Favourites {

    private const val PREFS = "wristradio"
    private const val KEY = "favourites"

    private lateinit var prefs: SharedPreferences

    private val _items = MutableStateFlow<List<Station>>(emptyList())
    val items: StateFlow<List<Station>> = _items.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _items.value = read()
    }

    fun isFavourite(uuid: String): Boolean = _items.value.any { it.uuid == uuid }

    /** Adds the station if it is not saved, removes it if it is. */
    fun toggle(station: Station) {
        val current = _items.value
        _items.value = if (current.any { it.uuid == station.uuid }) {
            current.filterNot { it.uuid == station.uuid }
        } else {
            listOf(station) + current
        }
        persist()
    }

    fun remove(uuid: String) {
        val current = _items.value
        val next = current.filterNot { it.uuid == uuid }
        if (next.size != current.size) {
            _items.value = next
            persist()
        }
    }

    private fun read(): List<Station> = runCatching {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.let(Station::from)
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val array = JSONArray()
        _items.value.forEach { array.put(it.toJson()) }
        prefs.edit { putString(KEY, array.toString()) }
    }
}
