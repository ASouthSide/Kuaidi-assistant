package com.kuaidi.assistant

internal sealed interface PackageRow {
    data class Parcel(val value: Package, val title: String, val collapseKey: String? = null) : PackageRow
    data class Group(val key: String, val location: String, val title: String, val address: String, val count: Int, val expanded: Boolean) : PackageRow
}

internal fun packageRows(
    packages: List<Package>,
    toggled: Set<String>,
    allPackages: List<Package> = packages,
    nameResolver: (String) -> String = { it }
): List<PackageRow> {
    val knownAddresses = allPackages.asSequence().filterNot { it.picked }
        .groupBy({ it.location.trim() }, { it.address.trim() })
        .mapValues { (_, addresses) -> addresses.filter(String::isNotBlank).distinct() }

    fun groupingKey(parcel: Package): Pair<String, String>? {
        if (parcel.picked) return null
        val location = parcel.location.trim()
        val address = parcel.address.trim()
        if (location.isEmpty()) return address.takeIf(String::isNotEmpty)?.let { "" to it }
        if (knownAddresses[location].orEmpty().size <= 1) return location to ""
        return address.takeIf(String::isNotEmpty)?.let { location to it }
    }

    val groups = packages.mapNotNull { pkg -> groupingKey(pkg)?.let { it to pkg } }
        .groupBy({ it.first }, { it.second })
    val emitted = hashSetOf<Pair<String, String>>()
    fun parcelRow(parcel: Package, collapseKey: String? = null) =
        PackageRow.Parcel(parcel, nameResolver(parcel.location), collapseKey)
    fun singleRow(parcel: Package): PackageRow {
        if (parcel.picked) return parcelRow(parcel)
        val key = "parcel:${parcel.id}"
        if (key !in toggled) return parcelRow(parcel, key)
        return PackageRow.Group(key, parcel.location.trim(), nameResolver(parcel.location),
            parcel.address.trim(), 1, false)
    }
    return buildList {
        for (parcel in packages) {
            val place = groupingKey(parcel)
            val members = groups[place].orEmpty()
            if (place == null || members.size < 2) {
                add(singleRow(parcel))
            } else if (emitted.add(place)) {
                val (location, address) = place
                val key = "${location.length}:$location$address"
                val open = key in toggled
                add(PackageRow.Group(key, location, nameResolver(location), address, members.size, open))
                if (open) members.forEach { add(parcelRow(it)) }
            }
        }
    }
}

