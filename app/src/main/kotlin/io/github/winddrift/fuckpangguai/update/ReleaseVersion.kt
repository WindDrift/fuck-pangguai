package io.github.winddrift.fuckpangguai.update

internal data class ReleaseVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<ReleaseVersion> {

    override fun compareTo(other: ReleaseVersion): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        return patch.compareTo(other.patch)
    }
}

private val VERSION_PATTERN = Regex("""^[vV]?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")

internal fun parseReleaseVersion(raw: String): ReleaseVersion? {
    val match = VERSION_PATTERN.matchEntire(raw.trim()) ?: return null
    val (major, minor, patch) = match.destructured
    return ReleaseVersion(
        major = major.toIntOrNull() ?: return null,
        minor = minor.toIntOrNull() ?: return null,
        patch = patch.toIntOrNull() ?: return null,
    )
}
