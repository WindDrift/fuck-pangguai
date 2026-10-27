package io.github.winddrift.fuckpangguai.update

private const val MAX_RELEASE_NOTES_LENGTH = 6_000

/**
 * 将 GitHub Release 的 Markdown 正文转为适合移动端展示的纯文本。
 * 只保留可读内容，链接正文保留文字、丢弃 URL，完整内容仍可在 Release 页查看。
 */
internal fun releaseNotesToPlainText(raw: String): String {
    if (raw.isBlank()) return ""

    var text = raw.replace("\r\n", "\n").replace('\r', '\n')
    text = text.replace(Regex("<!--[\\s\\S]*?-->"), "")
    text = text.replace(Regex("```[^\\n]*\\n?"), "")
    text = text.replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    text = text.replace(Regex("\\[([^\\]]+)]\\([^)]*\\)"), "$1")
    text = text.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")
    text = text.replace(Regex("(?m)^\\s{0,3}>\\s?"), "")
    text = text.replace(Regex("(?m)^\\s*[-*+]\\s+"), "• ")
    text = text.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "• ")
    text = text.replace(Regex("(?m)^\\s*[-*_]\\s*[-*_]\\s*[-*_]\\s*$"), "")
    text = text.replace(Regex("\\*\\*([^*\\n]+)\\*\\*"), "$1")
    text = text.replace(Regex("__([^_\\n]+)__"), "$1")
    text = text.replace(Regex("`([^`\\n]+)`"), "$1")
    text = text.replace(Regex("<[^>]+>"), "")
    text = text.replace(Regex("[ \\t]+\\n"), "\n")
    text = text.replace(Regex("\\n{3,}"), "\n\n")
    text = text.trim()

    if (text.length > MAX_RELEASE_NOTES_LENGTH) {
        text = text.take(MAX_RELEASE_NOTES_LENGTH).trimEnd() + "…"
    }
    return text
}
