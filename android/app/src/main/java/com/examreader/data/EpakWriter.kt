package com.examreader.data

import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * App 内生成 .epak（与 framework 产物同格式）：
 *   manifest.json + exam.json + mindmaps/<sectionId>.svg
 * 用 STORE（不压缩）保证与 framework 的 zip 读取实现互通。
 */
object EpakWriter {
    private const val GENERATOR = "examreader-android/1.0.0"

    fun build(exam: CExam, mindmaps: Map<String, String>): ByteArray {
        val entries = mutableListOf<Pair<String, ByteArray>>()

        val manifest = CManifest(
            format = "epak",
            formatVersion = 1,
            id = exam.id,
            title = exam.meta.title,
            full = exam.meta.full,
            builtAt = isoNow(),
            generator = GENERATOR,
            counts = exam.counts,
            files = CManifestFiles(
                exam = "exam.json",
                mindmaps = mindmaps.keys.map { "mindmaps/$it.svg" }
            )
        )
        entries.add("manifest.json" to SegCodec.json.encodeToString(CManifest.serializer(), manifest).toByteArray())
        entries.add("exam.json" to SegCodec.json.encodeToString(CExam.serializer(), exam).toByteArray())
        mindmaps.forEach { (sectionId, svg) ->
            if (svg.isNotBlank()) entries.add("mindmaps/$sectionId.svg" to svg.toByteArray())
        }
        return zipStored(entries)
    }

    private fun zipStored(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, data) ->
                val entry = ZipEntry(name).apply {
                    method = ZipEntry.STORED
                    size = data.size.toLong()
                    compressedSize = data.size.toLong()
                    crc = CRC32().apply { update(data) }.value
                    time = System.currentTimeMillis()
                }
                zip.putNextEntry(entry)
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun isoNow(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
