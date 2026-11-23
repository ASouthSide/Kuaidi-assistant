package com.kuaidi.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.concurrent.TimeUnit

class PickedCleanupPolicyTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val pickedAt = 100 * day

    @Test
    fun deletesNewPickedRecordsOnlyAtTheConfiguredDeadline() {
        val pending = parcel(1)
        val legacy = parcel(2).copy(picked = true, pickedTime = pickedAt - 30 * day)
        val picked = PickedCleanupPolicy.markPicked(parcel(3), pickedAt)
        val records = listOf(pending, legacy, picked)

        val deadline = pickedAt + 3 * day
        assertEquals(3, PickedCleanupPolicy.DEFAULT_DAYS)
        assertEquals(deadline, PickedCleanupPolicy.nextExpiry(records, 3))
        assertEquals(records, PickedCleanupPolicy.retain(records, 3, deadline - 1))
        assertEquals(listOf(pending, legacy), PickedCleanupPolicy.retain(records, 3, deadline))
    }

    @Test
    fun disablingCleanupCancelsExpiryAndChangingDaysRecalculatesFromPickup() {
        val picked = PickedCleanupPolicy.markPicked(parcel(1), pickedAt)
        val records = listOf(picked)

        assertNull(PickedCleanupPolicy.nextExpiry(records, null))
        assertEquals(records, PickedCleanupPolicy.retain(records, null, pickedAt + 365 * day))
        assertEquals(records, PickedCleanupPolicy.retain(records, 5, pickedAt + 3 * day))
        assertEquals(emptyList<Package>(), PickedCleanupPolicy.retain(records, 1, pickedAt + 3 * day))
    }

    @Test
    fun restoringPendingClearsEligibilityAndMarkingAgainStartsANewPeriod() {
        val legacy = parcel(1).copy(picked = true, pickedTime = pickedAt - 30 * day)
        val pending = PickedCleanupPolicy.restore(legacy)
        assertFalse(pending.picked)
        assertFalse(pending.eligibleForPickedCleanup)
        assertNull(pending.pickedTime)
        assertNull(PickedCleanupPolicy.nextExpiry(listOf(pending), 3))

        val pickedAgain = PickedCleanupPolicy.markPicked(pending, pickedAt)
        val restored = PickedCleanupPolicy.restore(pickedAgain)
        assertEquals(listOf(restored), PickedCleanupPolicy.retain(listOf(restored), 3, pickedAt + 5 * day))
        val newest = PickedCleanupPolicy.markPicked(restored, pickedAt + 1)
        assertEquals(pickedAt + 3 * day + 1, PickedCleanupPolicy.nextExpiry(listOf(newest), 3))
    }

    private fun parcel(id: Long) = Package(id = id, pickupCode = "QA", location = "星河驿站", arriveTime = 0)
}
