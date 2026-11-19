package com.kuaidi.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal object SmsRules {
    private const val PREFS = "sms_rules"
    private const val KEY = "json"
    const val LIMIT = 64

    data class Custom(val labels: List<String> = emptyList(), val carriers: Map<String, String> = emptyMap()) {
        fun parserRules() = SmsParser.Rules(labels, carriers)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun parse(context: Context, body: String): SmsParser.Result? =
        SmsParser.parse(body, load(context).parserRules())

    fun load(context: Context): Custom {
        val saved = prefs(context).getString(KEY, null) ?: return Custom()
        return runCatching {
            val data = JSONObject(saved)
            val labels = data.getJSONArray("pickupLabels")
            val carriers = data.getJSONObject("carrierAliases")
            Custom(List(labels.length()) { labels.getString(it) },
                carriers.keys().asSequence().associateWith(carriers::getString))
        }.getOrDefault(Custom())
    }

    fun save(context: Context, rules: Custom) {
        require(rules.labels.size <= LIMIT && rules.carriers.size <= LIMIT)
        require((rules.labels + rules.carriers.keys + rules.carriers.values).all(::validWord))
        val data = JSONObject().apply {
            put("version", 1)
            put("pickupLabels", JSONArray(rules.labels))
            put("carrierAliases", JSONObject(rules.carriers))
        }
        prefs(context).edit().putString(KEY, data.toString()).apply()
    }

    fun validWord(word: String) = word.length in 1..32 && word == word.trim() && word.none(Char::isISOControl)

    fun suggestLabel(sample: String): String? {
        val candidate = Regex("([\\p{IsHan}]{2,16})\\s*[:：]\\s*[0-9A-Za-z][0-9A-Za-z—-]{3,}")
        return candidate.findAll(sample).map { it.groupValues[1] }
            .firstOrNull { label ->
                listOf("验证码", "手机号", "运单号", "电话", "订单号", "单号").none(label::endsWith) &&
                    SmsParser.parse(sample, SmsParser.Rules(listOf(label), emptyMap())) != null
            }
    }
}

