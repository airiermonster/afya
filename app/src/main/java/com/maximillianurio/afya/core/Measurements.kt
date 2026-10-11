package com.maximillianurio.afya.core

/** Where a tape measure sits on the body. The side decides which column of the sheet it prints in. */
enum class Side { None, Left, Right }

/**
 * One measurement field. [key] is what the database and the JSON backup store, so it never changes
 * once shipped; [label] is what the sheet prints.
 */
data class MeasurementSite(val key: String, val label: String, val side: Side) {
    /** Bilateral sites print as "Left upper arm" / "Right upper arm"; single sites just their name. */
    val fieldLabel: String
        get() = when (side) {
            Side.None -> label
            Side.Left -> "Left ${label.lowerFirst()}"
            Side.Right -> "Right ${label.lowerFirst()}"
        }
}

/** The site names are capitalized as headings; mid-sentence they are not. */
private fun String.lowerFirst(): String = replaceFirstChar { it.lowercaseChar() }

/**
 * The eleven body-measurement sites, in the order a tape is taken down the body. Six are single
 * numbers; five are bilateral, so the sheet prints them as a left column mirroring a right column.
 * Measurements are self-reported with a tape — nothing here comes from the scale.
 */
object MeasurementSites {

    /** Single-number sites, head to hip. */
    private val singles = listOf(
        "neck" to "Neck",
        "chest" to "Chest",
        "waist" to "Waist",
        "navel" to "Navel",
        "hip" to "Hip",
        "buttock" to "Buttock",
    )

    /** Sites the body has one of on each side. */
    private val bilateral = listOf(
        "arm" to "Upper arm",
        "forearm" to "Forearm",
        "wrist" to "Wrist",
        "thigh" to "Thigh",
        "calf" to "Calf",
    )

    val all: List<MeasurementSite> = buildList {
        singles.forEach { (key, label) -> add(MeasurementSite(key, label, Side.None)) }
        bilateral.forEach { (key, label) ->
            add(MeasurementSite("${key}_l", label, Side.Left))
            add(MeasurementSite("${key}_r", label, Side.Right))
        }
    }

    /** Eleven rows on the sheet: six single fields and five left/right pairs. */
    val rowCount: Int get() = singles.size + bilateral.size

    private val byKey: Map<String, MeasurementSite> = all.associateBy { it.key }

    fun site(key: String): MeasurementSite? = byKey[key]

    /** A tape measure can plausibly read from a wrist to a chest; anything else is a mis-entry. */
    fun plausible(centimetres: Double): Boolean = centimetres in 3.0..300.0

    fun keyIsValid(key: String): Boolean = byKey.containsKey(key)

    /** Tape measurements are stored in centimetres; the sheet may print inches. */
    fun lengthText(centimetres: Double, inches: Boolean): Pair<String, String> =
        if (inches) "%.1f".format(centimetres / 2.54) to "in"
        else "%.1f".format(centimetres) to "cm"

    fun toCentimetres(value: Double, inches: Boolean): Double =
        if (inches) value * 2.54 else value
}
