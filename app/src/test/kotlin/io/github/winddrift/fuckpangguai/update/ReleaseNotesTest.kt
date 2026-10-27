package io.github.winddrift.fuckpangguai.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseNotesTest {

    @Test
    fun convertsHeadingsListsBoldAndLinks() {
        val markdown = """
            ## 新功能
            - 关于页
            - 问候语
            **完整变更**：[v1.10.1...v1.11.0](https://github.com/WindDrift/fuck-pangguai/compare/v1.10.1...v1.11.0)
        """.trimIndent()

        assertEquals(
            "新功能\n• 关于页\n• 问候语\n完整变更：v1.10.1...v1.11.0",
            releaseNotesToPlainText(markdown),
        )
    }

    @Test
    fun stripsImagesHtmlCommentsAndTags() {
        val markdown = """
            <!-- 隐藏说明 -->
            ![截图](https://example.com/a.png)
            <div>正文</div>
        """.trimIndent()

        assertEquals("截图\n正文", releaseNotesToPlainText(markdown))
    }

    @Test
    fun stripsCodeFencesAndInlineCode() {
        assertEquals("code", releaseNotesToPlainText("```\ncode\n```"))
        assertEquals("运行 test", releaseNotesToPlainText("运行 `test`"))
    }

    @Test
    fun handlesQuotesNumberedListsAndRules() {
        val markdown = """
            > 引用
            1. 第一项
            2. 第二项
            ---
        """.trimIndent()

        assertEquals("引用\n• 第一项\n• 第二项", releaseNotesToPlainText(markdown))
    }

    @Test
    fun normalizesLineEndingsAndBlankLines() {
        assertEquals("a\n\nb", releaseNotesToPlainText("a\r\n\r\n\r\nb"))
    }

    @Test
    fun returnsEmptyForBlankInput() {
        assertEquals("", releaseNotesToPlainText(""))
        assertEquals("", releaseNotesToPlainText("   \n  "))
    }

    @Test
    fun truncatesOverlongNotes() {
        val notes = releaseNotesToPlainText("a".repeat(7_000))
        assertTrue(notes.length <= 6_001)
        assertTrue(notes.endsWith("…"))
    }
}
