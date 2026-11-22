package com.notandrej.wristradio.data

/**
 * The browse shortcuts offered on the watch. A watch is a terrible place to
 * type, so the app leads with fixed lists instead of a search box and only
 * offers free-text search as a secondary route.
 */
object Directory {

    /** Curated tags that reliably return a good list of live stations. */
    val genres: List<String> = listOf(
        "pop", "rock", "jazz", "classical", "news", "talk", "electronic",
        "dance", "blues", "reggae", "metal", "country", "oldies", "chillout",
        "ambient", "lounge", "soul", "funk", "indie", "folk", "latin", "80s",
        "90s", "disco", "techno", "house", "punk", "gospel", "ska",
    )

    /** Country code to display name, ordered by how much radio is in them. */
    val countries: List<Pair<String, String>> = listOf(
        "US" to "United States",
        "GB" to "United Kingdom",
        "DE" to "Germany",
        "FR" to "France",
        "IT" to "Italy",
        "ES" to "Spain",
        "NL" to "Netherlands",
        "CA" to "Canada",
        "AU" to "Australia",
        "BR" to "Brazil",
        "MX" to "Mexico",
        "PL" to "Poland",
        "RU" to "Russia",
        "SE" to "Sweden",
        "NO" to "Norway",
        "DK" to "Denmark",
        "FI" to "Finland",
        "AT" to "Austria",
        "CH" to "Switzerland",
        "BE" to "Belgium",
        "PT" to "Portugal",
        "IE" to "Ireland",
        "GR" to "Greece",
        "TR" to "Turkey",
        "UA" to "Ukraine",
        "CZ" to "Czechia",
        "RO" to "Romania",
        "HU" to "Hungary",
        "EE" to "Estonia",
        "HR" to "Croatia",
        "AR" to "Argentina",
        "CL" to "Chile",
        "CO" to "Colombia",
        "ZA" to "South Africa",
        "IN" to "India",
        "JP" to "Japan",
        "CN" to "China",
        "KR" to "South Korea",
        "NZ" to "New Zealand",
    )
}
