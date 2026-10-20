package com.withyou.anniversary

import org.junit.Assert.*
import org.junit.Test

class EmailRulesTest {
    @Test fun normalizesCommonMailAddressesWithoutSpaces() {
        assertEquals("friend+test@qq.com",EmailRules.email(" Friend+test@QQ.COM "))
        for(value in listOf("昵称","a@","@qq.com","a@qq","a b@qq.com","a@qq.com\nBcc:x@example.test")) {
            assertThrows(IllegalArgumentException::class.java) {EmailRules.email(value)}
        }
    }
    @Test fun invitationsAcceptOnlyOurExactOriginAndToken() {
        val token="a1".repeat(16)
        assertEquals(token,EmailRules.invite("https://2269642185.github.io/withyou/register.html#invite=$token"))
        assertEquals(token,EmailRules.invite("withyou://register?invite=$token"))
        assertEquals(token,EmailRules.invite(token))
        for(value in listOf("https://evil.example/withyou/register.html#invite=$token","https://2269642185.github.io.evil.test/withyou/register.html#invite=$token","https://user@2269642185.github.io/withyou/register.html#invite=$token","withyou://register/else?invite=$token","withyou://register?invite=$token&other=1","https://2269642185.github.io:443/withyou/register.html#invite=$token","withyou://register?invite=${"x".repeat(32)}")) assertEquals("",EmailRules.invite(value))
    }
    @Test fun legacyCompatiblePasswordRulesDoNotTrimSecrets() {
        EmailRules.password("Aa123456!")
        for(value in listOf("12345678","abcdefgh","Aa1!","!Aa123456", "a".repeat(33))) assertThrows(IllegalArgumentException::class.java) {EmailRules.password(value)}
    }
}
