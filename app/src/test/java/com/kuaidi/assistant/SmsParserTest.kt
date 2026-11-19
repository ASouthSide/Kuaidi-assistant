package com.kuaidi.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {
    @Test fun extractsDashedCodeAndAddress() {
        val sms = "【菜鸟】包裹已到达星河驿站，取件码：11-6-05002，地址：星河路8号"
        assertEquals(SmsParser.Result("11-6-05002", "星河驿站", "菜鸟", "星河路8号"), SmsParser.parse(sms))
    }

    @Test fun handlesNumericPickupCodeAndCarrier() {
        val result = SmsParser.parse("【兔喜】凭24271到星河店取货")
        assertEquals("24271", result?.pickupCode)
        assertEquals("中通", result?.carrier)
    }

    @Test fun recognisesImportedLabelsLiterally() {
        val rules = SmsParser.Rules(listOf("柜号(A)"), mapOf("星河配送" to "测试快递"))
        val sms = "【星河配送】柜号(A)：03B205"
        assertNull(SmsParser.parse(sms))
        assertEquals(SmsParser.Result("03B205", "", "测试快递"), SmsParser.parse(sms, rules))
        assertNull(SmsParser.parse("柜号A：03B205", rules))
    }

    @Test fun suggestsPickupLabelFromAnInAppSample() {
        assertEquals("自提口令", SmsRules.suggestLabel("【星河】自提口令：73-5-4206"))
        assertNull(SmsRules.suggestLabel("登录验证码：853353"))
    }

    @Test fun ignoresOrdinaryVerificationCodes() {
        assertNull(SmsParser.parse("【某服务】验证码：853353，请勿泄露"))
    }

    @Test fun matchesAnExplicitPickupLabelWithoutOtherShippingWords() {
        assertEquals("03B205", SmsParser.parse("提取码：03B205")?.pickupCode)
    }
}

