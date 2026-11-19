package com.kuaidi.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class PackageRowsTest {
    @Test fun expandsPendingAtSamePointAndKeepsPickedSeparate() {
        val pending = listOf(parcel(1), parcel(2))
        val picked = parcel(3).copy(picked = true)
        val all = pending + picked
        val collapsed = packageRows(all, emptySet())
        val group = collapsed.first() as PackageRow.Group
        assertEquals(2, group.count)
        assertEquals(listOf(picked), collapsed.filterIsInstance<PackageRow.Parcel>().map { it.value })

        val open = packageRows(all, setOf(group.key))
        assertEquals(all, open.filterIsInstance<PackageRow.Parcel>().map { it.value })
    }

    @Test fun doesNotCombineConflictingAddressesOrUnknownPoints() {
        val sameName = listOf(parcel(1, address = "南路 1 号"), parcel(2, address = "北路 2 号"), parcel(3))
        val unknown = listOf(parcel(4, location = ""), parcel(5, location = ""))
        assertEquals(5, packageRows(sameName + unknown, emptySet()).filterIsInstance<PackageRow.Parcel>().size)
        assertEquals(2, packageRows(listOf(sameName[0], sameName[2]), emptySet(), sameName).size)
    }

    @Test fun matchesAParcelWithoutAnAddressWhenThePickupNameIsUnambiguous() {
        val collapsed = packageRows(listOf(parcel(1, address = "星河路 8 号"), parcel(2)), emptySet())
        assertEquals(2, (collapsed.single() as PackageRow.Group).count)
    }

    @Test fun foldsAndRestoresOnePendingParcel() {
        val parcel = parcel(1)
        val defaultRow = packageRows(listOf(parcel), emptySet()).single() as PackageRow.Parcel
        val key = requireNotNull(defaultRow.collapseKey)
        val folded = packageRows(listOf(parcel), setOf(key)).single() as PackageRow.Group
        assertEquals(key, folded.key)
        assertEquals(1, folded.count)
        assertEquals(false, folded.expanded)
        assertEquals(defaultRow, packageRows(listOf(parcel), emptySet()).single())
    }

    private fun parcel(id: Long, location: String = "星河驿站", address: String = "") =
        Package(id = id, pickupCode = "73-5-420$id", location = location, address = address, arriveTime = id)
}

