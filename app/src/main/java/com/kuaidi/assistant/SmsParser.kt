package com.kuaidi.assistant

/**
 * 解析快递驿站/快递公司短信，提取取件码、取件点、详细地址、快递公司。
 * 兼容：取件码/取货码/提货码/提货号/凭…到… 等多种格式（已用真实短信样本验证）。
 */
object SmsParser {

    data class Result(
        val pickupCode: String,
        val location: String,
        val carrier: String = "",
        val address: String = ""
    )

    class Rules(labels: List<String>, aliases: Map<String, String>) {
        internal val code = labels.takeIf { it.isNotEmpty() }?.let { words ->
            val prefix = words.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
            Regex("(?:$prefix)\\s*[:：为是]?\\s*([0-9A-Za-z]{1,4}[-—][0-9A-Za-z]{1,4}(?:[-—][0-9A-Za-z]{1,6})?|[0-9A-Za-z]{4,12})(?![0-9A-Za-z—-])")
        }
        internal val carriers = aliases.toList().sortedByDescending { it.first.length }
    }

    private val CARRIERS = listOf(
        "顺丰", "中通", "圆通", "申通", "韵达", "极兔", "京东", "邮政", "EMS",
        "百世", "德邦", "菜鸟", "丹鸟", "天猫", "丰网", "多多", "壹米滴答", "安能"
    )

    // 取件码/提货号 等 + 带连字符码（11-6-05002）
    private val CODE_KEYWORD = Regex("(?:取件码|取货码|提货码|提取码|提货号|取货号|提件号)[:：为是]?\\s*([0-9A-Za-z]{1,4}[-—][0-9A-Za-z]{1,4}(?:[-—][0-9A-Za-z]{1,6})?)")
    // 纯数字/字母码（853353 / 03B205）
    private val CODE_KEYWORD2 = Regex("(?:取件码|取货码|提货码|提取码|提货号|取货号|提件号)[:：为是]?\\s*([0-9A-Za-z]{4,12})")
    // 凭 7-3-3001
    private val CODE_PING = Regex("凭\\s*([0-9A-Za-z]{1,4}[-—][0-9A-Za-z]{1,4}(?:[-—][0-9A-Za-z]{1,6})?)")
    // 凭 24271（无连字符纯数字/字母码）
    private val CODE_PING2 = Regex("凭\\s*([0-9A-Za-z]{4,12})")
    // 兜底：X-X-XXXX
    private val CODE_DASH = Regex("(?<![0-9A-Za-z])([0-9]{1,3}[-—][0-9]{1,4}(?:[-—][0-9]{1,6})?)(?![0-9A-Za-z])")

    private val LOC = "[\\u4e00-\\u9fa5A-Za-z0-9·\\-]"

    // 已到/已到达/到达 + 取件点（优先，最可靠）
    private val LOCATION_ARRIVE = Regex("(?:已到达|已到|到达)\\s*($LOC{2,30})")
    // 到 + 取件点（凭…到…），止于 取货/取件 等
    private val LOCATION_DAO = Regex("到\\s*($LOC{2,30}?)(?:取货|取件|取包裹|领取|，|,|。|\$)")
    // 兜底：以驿站/快递柜/…/店 结尾的取件点
    private val LOCATION_REGEX = Regex("($LOC{2,24}(?:驿站|快递柜|快递点|服务站|代收点|服务中心|自提点|门店|超市|便利店|取货点|服务点|店))")

    // 详细地址：地址: xxx
    private val ADDRESS_REGEX = Regex("地址[:：]?\\s*($LOC{2,50})")

    fun isExpressSms(body: String): Boolean {
        return body.contains("取件码") || body.contains("取货") || body.contains("提货") ||
                body.contains("驿站") || body.contains("快递柜") || body.contains("取件") ||
                body.contains("包裹") || body.contains("快递") ||
                (body.contains("凭") && (body.contains("-") || body.contains(Regex("\\d"))))
    }

    fun parse(body: String, rules: Rules? = null): Result? {

        val full = body
        // 去掉【发送方】前缀，避免误判为取件点
        val s = body.replace(Regex("^【[^】]*】"), "")

        val explicitCode = rules?.code?.find(s)?.groupValues?.get(1)
            ?: CODE_KEYWORD.find(s)?.groupValues?.get(1)
            ?: CODE_KEYWORD2.find(s)?.groupValues?.get(1)
        val code = explicitCode ?: if (isExpressSms(body)) {
            CODE_PING.find(s)?.groupValues?.get(1)
            ?: CODE_PING2.find(s)?.groupValues?.get(1)
            ?: CODE_DASH.find(s)?.groupValues?.get(1)
        } else null
        if (code == null) return null

        val location = LOCATION_ARRIVE.find(s)?.groupValues?.get(1)
            ?: LOCATION_DAO.find(s)?.groupValues?.get(1)
            ?: LOCATION_REGEX.find(s)?.groupValues?.get(1)
            ?: ""

        val address = ADDRESS_REGEX.find(s)?.groupValues?.get(1) ?: ""

        val carrier = rules?.carriers?.firstOrNull { full.contains(it.first) }?.second ?: when {
            full.contains("兔喜") -> "中通"   // 兔喜生活/兔喜快递柜归属中通
            else -> CARRIERS.firstOrNull { full.contains(it) } ?: ""
        }

        return Result(pickupCode = code, location = location, carrier = carrier, address = address)
    }
}
