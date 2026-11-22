package com.notandrej.wristradio.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Client for the [radio-browser.info](https://www.radio-browser.info/) community
 * directory.
 *
 * Two things about that API are worth knowing and are why this is a singleton:
 *
 * - It is a round-robin of independent volunteer mirrors. `all.api.radio-browser.info`
 *   is a DNS alias over the healthy ones; [resolveMirrors] asks `/json/servers`
 *   for the concrete list and we try each of them in turn on every request, so
 *   one mirror going down is not an outage.
 * - It requires a descriptive `User-Agent` and asks clients not to hammer it.
 *   We send a stable one and only fetch when the user asks for something.
 */
object RadioBrowser {

    const val USER_AGENT = "WristRadio/1.0 (+https://github.com/NotAndrej/WristRadio)"

    private const val TAG = "WristRadio.Api"

    /** Directory calls: bounded, because they are small JSON documents. */
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Audio/metadata calls: these stream forever, so a call timeout must never
     * be applied. Used by the best-effort ICY metadata reader.
     */
    val streamHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    /** Known-good hosts, in the order we would like to try them. */
    private val fallbacks = listOf(
        "de1.api.radio-browser.info",
        "de2.api.radio-browser.info",
        "all.api.radio-browser.info",
    )

    @Volatile
    private var discovered: List<String> = emptyList()

    private val hosts: List<String>
        get() = (discovered + fallbacks).distinct()

    /**
     * Asks the directory which mirrors exist right now and remembers them.
     * Failure is not interesting - the hard-coded fallbacks still work.
     */
    suspend fun resolveMirrors() = withContext(Dispatchers.IO) {
        for (host in fallbacks) {
            val servers = fetchArray("https://$host/json/servers") ?: continue
            val names = LinkedHashSet<String>()
            for (i in 0 until servers.length()) {
                servers.optJSONObject(i)?.optString("name")?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let(names::add)
            }
            if (names.isNotEmpty()) {
                discovered = names.toList()
                Log.i(TAG, "mirrors: ${discovered.joinToString()}")
                return@withContext
            }
        }
        Log.w(TAG, "mirror discovery failed; using ${fallbacks.joinToString()}")
    }

    // ---- directory queries -------------------------------------------------

    /** Free-text station search. */
    suspend fun search(query: String, limit: Int = 60): List<Station> = stations(
        "/json/stations/search?name=${encode(query)}" +
            "&hidebroken=true&order=clickcount&reverse=true&limit=$limit"
    )

    /** Stations carrying an exact tag, e.g. `jazz`. */
    suspend fun byTag(tag: String, limit: Int = 60): List<Station> = stations(
        "/json/stations/bytagexact/${encode(tag)}" +
            "?hidebroken=true&order=clickcount&reverse=true&limit=$limit"
    )

    /** Stations whose country code matches exactly, e.g. `GB`. */
    suspend fun byCountry(countryCode: String, limit: Int = 60): List<Station> = stations(
        "/json/stations/bycountrycodeexact/${encode(countryCode)}" +
            "?hidebroken=true&order=clickcount&reverse=true&limit=$limit"
    )

    /** What other listeners are listening to right now. */
    suspend fun popular(limit: Int = 60): List<Station> =
        stations("/json/stations/topclick/$limit")

    /**
     * Tells the directory that a station was actually played. Fire-and-forget;
     * it is how the community ranking works and it is polite to report back.
     */
    suspend fun reportClick(uuid: String) = withContext(Dispatchers.IO) {
        val host = hosts.firstOrNull() ?: return@withContext
        runCatching {
            val request = Request.Builder()
                .url("https://$host/json/url/$uuid")
                .header("User-Agent", USER_AGENT)
                .post(ByteArray(0).toRequestBody(null))
                .build()
            http.newCall(request).execute().close()
        }
    }

    // ---- plumbing ----------------------------------------------------------

    private suspend fun stations(path: String): List<Station> {
        val array = fetch(path)
        val out = ArrayList<Station>(array.length())
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let { Station.from(it)?.let(out::add) }
        }
        // The same stream is frequently listed several times under different ids.
        return out.distinctBy { it.url }
    }

    /** Runs a directory path against the first mirror that answers. */
    private suspend fun fetch(path: String): JSONArray = withContext(Dispatchers.IO) {
        for (host in hosts) {
            fetchArray("https://$host$path")?.let { return@withContext it }
        }
        throw IOException("can't reach any radio-browser.info mirror")
    }

    private fun fetchArray(url: String): JSONArray? = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            JSONArray(response.body?.string() ?: return@use null)
        }
    }.onFailure { Log.w(TAG, "GET $url failed: ${it.message}") }
        .getOrNull()

    private fun encode(value: String): String =
        URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20")
}
