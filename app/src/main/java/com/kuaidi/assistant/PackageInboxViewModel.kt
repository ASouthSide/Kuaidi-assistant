package com.kuaidi.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import java.util.Calendar

class PackageInboxViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PackageRepository(application)
    var packages: List<Package> = emptyList()
        private set
    var aliases: Map<String, String> = emptyMap()
        private set

    fun reload() {
        packages = repository.packages()
        aliases = repository.aliases()
    }

    fun add(pkg: Package) = repository.add(pkg)
    fun remove(id: Long) = repository.remove(id)
    fun saveAlias(original: String, alias: String) = repository.saveAlias(original, alias)

    fun togglePicked(id: Long) {
        repository.togglePicked(id)
        packages = repository.packages()
    }

    fun filter(dateSel: String, carrierSel: String, addrSel: String, picked: Boolean?): List<Package> {
        val now = System.currentTimeMillis()

        return packages.filter { p ->
            val dateOk = when (dateSel) {
                "今天" -> isSameDay(p.arriveTime, now)
                "近3天" -> now - p.arriveTime < 3L * 86400000
                "近7天" -> now - p.arriveTime < 7L * 86400000
                else -> true
            }
            val carrierOk = carrierSel == "全部" || p.carrier == carrierSel
            val addrOk = addrSel == "全部" || PackageStore.displayName(p.location, aliases) == addrSel
            dateOk && carrierOk && addrOk && (picked == null || p.picked == picked)
        }.sortedBy { it.picked }
    }

    private fun isSameDay(t1: Long, t2: Long): Boolean {
        val c1 = Calendar.getInstance().apply { timeInMillis = t1 }
        val c2 = Calendar.getInstance().apply { timeInMillis = t2 }
        return c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR) &&
                c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR)
    }
}
