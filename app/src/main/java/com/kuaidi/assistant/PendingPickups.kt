package com.kuaidi.assistant

internal object PendingPickups {
    fun ordered(packages: List<Package>): List<Package> =
        packages.filterNot { it.picked }.sortedWith(compareBy(Package::arriveTime, Package::id))

    fun address(pkg: Package, aliases: Map<String, String>): String =
        pkg.address.trim().ifBlank {
            PackageStore.displayName(pkg.location, aliases).trim().ifBlank { "未填写取件地址" }
        }

    fun title(count: Int): String = "$count 件快递待取"
}
