package io.github.winddrift.fuckpangguai.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class DiagExportAndroid(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun write(input: DiagExportInput): File {
        val dir = File(appContext.cacheDir, EXPORT_DIR).apply { mkdirs() }
        val stamp = FILE_STAMP.format(
            Instant.ofEpochMilli(input.generatedAt).atZone(ZoneId.systemDefault()),
        )
        val file = File(dir, "pangguai-diagnostics-$stamp.zip")
        file.writeBytes(DiagExport.buildZip(input))
        prune(dir)
        return file
    }

    fun share(file: File, title: String = "分享诊断日志") {
        val uri = uriFor(file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("diagnostics", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        appContext.startActivity(chooser)
    }

    fun save(uri: Uri, file: File): Boolean = runCatching {
        appContext.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
        } ?: error("无法打开输出文件")
    }.isSuccess

    private fun uriFor(file: File): Uri = FileProvider.getUriForFile(
        appContext,
        "${appContext.packageName}.diagnostics",
        file,
    )

    private fun prune(dir: File) {
        dir.listFiles()
            .orEmpty()
            .filter { it.isFile }
            .sortedByDescending { it.lastModified() }
            .drop(MAX_CACHED_EXPORTS)
            .forEach { it.delete() }
    }

    private companion object {
        const val EXPORT_DIR = "diagnostics/exports"
        const val MAX_CACHED_EXPORTS = 5
        val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}
