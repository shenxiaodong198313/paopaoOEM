package org.fcitx.fcitx5.android.data.theme

/** Resolve once per bitmap, preserving the old clamp unless a valid authored band opts in. */
internal fun skinSliceCaps(size: Int, start: Int, end: Int, asymmetric: Boolean): IntArray {
    require(size > 0)
    // Long arithmetic prevents malformed imported caps from overflowing the validity check.
    if (asymmetric && start >= 0 && end >= 0 && start.toLong() + end < size.toLong()) {
        return intArrayOf(start, end)
    }
    return intArrayOf(start.coerceIn(0, size / 2), end.coerceIn(0, size / 2))
}
