package com.kuaidi.assistant

import org.json.JSONObject

data class Package(
    val id: Long,
    val trackingNo: String = "",
    val pickupCode: String,
    val location: String,
    val carrier: String = "",
    val address: String = "",        // 详细地址（短信里「地址：xxx」）
    val arriveTime: Long,          // 到站时间（毫秒时间戳）
    val picked: Boolean = false,
    val pickedTime: Long? = null   // 取件时间
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("trackingNo", trackingNo)
        put("pickupCode", pickupCode)
        put("location", location)
        put("carrier", carrier)
        put("address", address)
        put("arriveTime", arriveTime)
        put("picked", picked)
        put("pickedTime", pickedTime ?: -1L)
    }

    companion object {
        fun fromJson(o: JSONObject): Package = Package(
            id = o.getLong("id"),
            trackingNo = o.optString("trackingNo", ""),
            pickupCode = o.getString("pickupCode"),
            location = o.getString("location"),
            carrier = o.optString("carrier", ""),
            address = o.optString("address", ""),
            arriveTime = o.getLong("arriveTime"),
            picked = o.getBoolean("picked"),
            pickedTime = o.getLong("pickedTime").takeIf { it >= 0 }
        )
    }
}
