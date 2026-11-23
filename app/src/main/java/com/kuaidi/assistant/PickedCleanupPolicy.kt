package com.kuaidi.assistant

import java.util.concurrent.TimeUnit

internal object PickedCleanupPolicy {
    const val DEFAULT_DAYS = 3
    val daysRange = 1..365

    fun markPicked(pkg: Package, now: Long): Package =
        pkg.copy(picked = true, pickedTime = now, eligibleForPickedCleanup = true)

    fun restore(pkg: Package): Package =
        pkg.copy(picked = false, pickedTime = null, eligibleForPickedCleanup = false)

    fun retain(packages: List<Package>, days: Int?, now: Long): List<Package> =
        if (days == null) packages else packages.filterNot { pkg ->
            expiresAt(pkg, days)?.let { it <= now } == true
        }

    fun nextExpiry(packages: List<Package>, days: Int?): Long? =
        if (days == null) null else packages.mapNotNull { expiresAt(it, days) }.minOrNull()

    private fun expiresAt(pkg: Package, days: Int): Long? {
        if (!pkg.picked || !pkg.eligibleForPickedCleanup || days !in daysRange) return null
        val duration = TimeUnit.DAYS.toMillis(days.toLong())
        return pkg.pickedTime?.takeIf { it in 0..(Long.MAX_VALUE - duration) }?.plus(duration)
    }
}
