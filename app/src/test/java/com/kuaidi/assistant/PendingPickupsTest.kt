package com.kuaidi.assistant

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingPickupsTest {
    @Test
    fun ordersUnpickedParcelsByArrivalAndUsesIdToBreakTies() {
        val pending = PendingPickups.ordered(listOf(
            parcel(4, 20),
            parcel(8, 5).copy(picked = true),
            parcel(3, 20),
            parcel(1, 10)
        ))
        assertEquals(listOf(1L, 3L, 4L), pending.map(Package::id))
        assertEquals(emptyList<Package>(), PendingPickups.ordered(pending.map { it.copy(picked = true) }))
    }

    @Test
    fun usesTheDetailedAddressBeforeThePickupPointAlias() {
        val pkg = parcel(1, 10)
        val aliases = mapOf("北门驿站" to "北门")
        assertEquals("星河路 8 号", PendingPickups.address(pkg.copy(address = " 星河路 8 号 "), aliases))
        assertEquals("北门", PendingPickups.address(pkg, aliases))
        assertEquals("未填写取件地址", PendingPickups.address(pkg.copy(location = ""), emptyMap()))
    }

    private fun parcel(id: Long, arrives: Long) =
        Package(id = id, pickupCode = "73-5-4206", location = "北门驿站", arriveTime = arrives)
}
