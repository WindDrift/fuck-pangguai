package io.github.winddrift.fuckpangguai.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRulesTest {

    @Test
    fun `accounts sort by last used descending`() {
        val sorted = sortAccounts(
            listOf(
                account("a", lastUsedAt = 10),
                account("b", lastUsedAt = 30),
                account("c", lastUsedAt = 20),
            ),
        )
        assertEquals(listOf("b", "c", "a"), sorted.map { it.localId })
    }

    @Test
    fun `display name falls back from alias to nickname to masked phone`() {
        assertEquals("别名", displayName(account("a", alias = "别名", nickname = "昵称")))
        assertEquals("昵称", displayName(account("a", nickname = "昵称", phoneMasked = "138****0000")))
        assertEquals("138****0000", displayName(account("a", phoneMasked = "138****0000")))
        assertEquals("未知账号", displayName(account("a")))
    }

    @Test
    fun `phone is masked in the middle`() {
        assertEquals("138****0000", maskPhone("13800000000"))
        assertEquals("123456", maskPhone("123456"))
        assertEquals("", maskPhone(""))
    }

    @Test
    fun `merge target prefers server id then phone hash`() {
        val byPhone = account("phone", phoneHash = phoneHash("13800000000"))
        val byServer = account("server", serverId = "u1")
        val accounts = listOf(byPhone, byServer)

        assertEquals("server", findMergeTarget(accounts, "u1", phoneHash("13900000000"))?.localId)
        assertEquals("phone", findMergeTarget(accounts, "", phoneHash("13800000000"))?.localId)
        assertNull(findMergeTarget(accounts, "u2", phoneHash("13700000000")))
    }

    @Test
    fun `merge keeps target identity and source token`() {
        val target = account(
            "target",
            serverId = "u1",
            alias = "旧别名",
            nickname = "旧昵称",
            lastUsedAt = 10,
        )
        val source = account(
            "source",
            serverId = "u1",
            nickname = "新昵称",
            phoneMasked = "138****0000",
            token = "new-token",
            lastUsedAt = 20,
        )

        val merged = mergedAccount(target, source)

        assertEquals("target", merged.localId)
        assertEquals("旧别名", merged.alias)
        assertEquals("新昵称", merged.nickname)
        assertEquals("new-token", merged.tokenCipher)
        assertEquals(20, merged.lastUsedAt)
        assertTrue(!merged.invalid)
    }

    @Test
    fun `merge clears logged out state`() {
        val target = account("target", phoneHash = "hash", invalid = true, loggedOut = true)
        val source = account("source", token = "new-token")

        val merged = mergedAccount(target, source)

        assertTrue(!merged.invalid)
        assertTrue(!merged.loggedOut)
    }

    @Test
    fun `next account skips invalid and empty tokens`() {
        val accounts = listOf(
            account("active", lastUsedAt = 40),
            account("invalid", lastUsedAt = 30, invalid = true),
            account("empty", lastUsedAt = 20, token = ""),
            account("valid", lastUsedAt = 10),
        )
        assertEquals("valid", nextAccountAfterDelete(accounts, "active")?.localId)
        assertNull(nextAccountAfterDelete(listOf(account("only")), "only"))
    }

    private fun account(
        localId: String,
        serverId: String = "",
        alias: String = "",
        nickname: String = "",
        phoneMasked: String = "",
        phoneHash: String = "",
        token: String = "token-$localId",
        invalid: Boolean = false,
        loggedOut: Boolean = false,
        lastUsedAt: Long = 0,
    ) = StoredAccount(
        localId = localId,
        serverId = serverId,
        alias = alias,
        nickname = nickname,
        phoneMasked = phoneMasked,
        phoneHash = phoneHash,
        tokenCipher = token,
        tokenIv = if (token.isBlank()) "" else "iv",
        invalid = invalid,
        loggedOut = loggedOut,
        lastUsedAt = lastUsedAt,
    )
}
