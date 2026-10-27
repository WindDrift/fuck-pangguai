package io.github.winddrift.fuckpangguai.scan

import io.github.winddrift.fuckpangguai.account.StoredAccount
import org.junit.Assert.assertEquals
import org.junit.Test

class ScanGreetingTest {

    @Test
    fun splitsDayIntoSixHourBuckets() {
        assertEquals("凌晨好", greetingForHour(0))
        assertEquals("凌晨好", greetingForHour(5))
        assertEquals("早上好", greetingForHour(6))
        assertEquals("早上好", greetingForHour(10))
        assertEquals("中午好", greetingForHour(11))
        assertEquals("中午好", greetingForHour(13))
        assertEquals("下午好", greetingForHour(14))
        assertEquals("下午好", greetingForHour(17))
        assertEquals("晚上好", greetingForHour(18))
        assertEquals("晚上好", greetingForHour(23))
    }

    @Test
    fun prefersAliasThenNicknameThenMaskedPhone() {
        assertEquals(
            "别名",
            greetingName(
                StoredAccount(
                    localId = "1",
                    alias = "别名",
                    nickname = "昵称",
                    phoneMasked = "138****0000",
                ),
            ),
        )
        assertEquals(
            "昵称",
            greetingName(
                StoredAccount(
                    localId = "1",
                    nickname = "昵称",
                    phoneMasked = "138****0000",
                ),
            ),
        )
        assertEquals(
            "138****0000",
            greetingName(StoredAccount(localId = "1", phoneMasked = "138****0000")),
        )
    }

    @Test
    fun fallsBackToClassmateWhenNoNameAvailable() {
        assertEquals("同学", greetingName(null))
        assertEquals("同学", greetingName(StoredAccount(localId = "1", alias = "  ", nickname = "")))
        assertEquals("早上好，同学", greetingText(null, 8))
    }

    @Test
    fun buildsFullGreeting() {
        assertEquals(
            "晚上好，浮笙",
            greetingText(StoredAccount(localId = "1", alias = "浮笙"), 20),
        )
    }
}
