package com.maximillianurio.afya.core

/**
 * What the person says they are after, taken once as an interview and editable afterwards. Nothing
 * here changes a figure the scale produced: it moves the energy target when no goal weight has been
 * written, and decides which measures the pages lead with. [Aim] is what was asked for, distinct from
 * the direction [Targets] works out.
 */
enum class Aim(val label: String, val line: String, val lead: List<String>) {
    Losing(
        "Lose fat",
        "Body fat and visceral fat come first on your pages, and the energy target sits below what you burn.",
        listOf("bmi", "fat", "visceral", "water", "muscle", "protein", "bone", "metabolic_age"),
    ),
    Maintaining(
        "Hold steady",
        "Muscle and water come first on your pages, and the energy target matches what you burn.",
        listOf("bmi", "muscle", "water", "fat", "protein", "bone", "visceral", "metabolic_age"),
    ),
    Gaining(
        "Build muscle",
        "Muscle and protein come first on your pages, and the energy target sits above what you burn.",
        listOf("bmi", "muscle", "protein", "water", "fat", "bone", "visceral", "metabolic_age"),
    ),
}

enum class Pace(val label: String, val weekKg: Double, val line: String) {
    Gentle("Gentle", 0.25, "About a quarter of a kilo a week — the rate least likely to cost you muscle."),
    Steady("Steady", 0.5, "About half a kilo a week, the rate most people sustain."),
    Fast("Aggressive", 0.75, "Up to three quarters of a kilo a week. Hard to hold, and it reads as an estimate."),
}

enum class Motivation(val label: String) {
    Health("Everyday health"),
    Energy("More energy"),
    Appearance("How I look"),
    Sport("Sport and strength"),
    Numbers("The numbers themselves"),
    Doctor("A clinician asked me to"),
}

enum class Obstacle(val label: String) {
    Time("No time"),
    Hunger("Hunger"),
    Motivation("Staying motivated"),
    Sleep("Sleep"),
    Travel("Travel and routine"),
    None("Nothing so far"),
}

enum class CheckIn(val label: String, val days: Int) {
    Daily("Every morning", 1),
    Workdays("Weekdays", 5),
    Weekly("Once a week", 7),
    Occasionally("When I feel like it", 0),
}

/** The interview's answers. All null until the questions have been answered once. */
data class IntentProfile(
    val aim: Aim? = null,
    val pace: Pace? = null,
    val motivation: Motivation? = null,
    val obstacle: Obstacle? = null,
    val checkIn: CheckIn? = null,
    /** When the interview was last completed; 0 means never, and the plan opens on it. */
    val answeredAtMs: Long = 0L,
) {
    val answered: Boolean get() = answeredAtMs > 0L
}
