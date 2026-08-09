package com.sugarsaathi.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a one-or-more page PDF of a patient's glucose readings, suitable for
 * handing or sending to a doctor.
 *
 * Deliberately English-only. The readingType values are stored in English, and
 * clinical records in Pakistan are kept in English - a mixed-script document
 * would be harder for a doctor to scan, not easier. The app UI stays bilingual;
 * this artefact is for the clinic.
 */
object GlucoseReport {

    // A4 at 72 dpi, the unit PdfDocument works in.
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f

    private const val TARGET_LOW = 70f
    private const val TARGET_HIGH = 180f
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private val TEAL = AColor.rgb(0x1D, 0x9E, 0x75)
    private val DEEP = AColor.rgb(0x0D, 0x5A, 0x44)
    private val GREY = AColor.rgb(0x75, 0x75, 0x75)
    private val LIGHT = AColor.rgb(0xE0, 0xE0, 0xE0)

    /**
     * @param rangeDays how far back to include
     * @return a content:// Uri ready to share, or null if generation failed
     */
    fun generate(
        context: Context,
        profile: UserProfileData,
        readings: List<GlucoseReading>,
        rangeDays: Int
    ): android.net.Uri? {
        return try {
            val now = System.currentTimeMillis()
            val cutoff = now - rangeDays * DAY_MS
            val inRange = readings.filter { it.timestamp >= cutoff }
                .sortedByDescending { it.timestamp }

            val doc = PdfDocument()
            var pageNo = 1

            var page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
            )
            var canvas = page.canvas
            var y = MARGIN

            y = drawHeader(canvas, y, now)
            y = drawPatient(canvas, y, profile)
            y = drawRangeAndStats(canvas, y, inRange, cutoff, now, rangeDays)

            if (inRange.isNotEmpty()) {
                y = drawChart(canvas, y, inRange, cutoff, now, profile.glucoseUnit)
            }

            y = drawTableHeader(canvas, y + 8f)

            val rowH = 18f
            val bottomLimit = PAGE_H - MARGIN - 30f

            for (r in inRange) {
                if (y + rowH > bottomLimit) {
                    drawFooter(canvas, pageNo)
                    doc.finishPage(page)
                    pageNo++
                    page = doc.startPage(
                        PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create()
                    )
                    canvas = page.canvas
                    y = MARGIN
                    y = drawTableHeader(canvas, y)
                }
                y = drawRow(canvas, y, r)
            }

            if (inRange.isEmpty()) {
                val p = textPaint(11f, GREY)
                canvas.drawText("No readings recorded in this period.", MARGIN, y + 16f, p)
                y += 30f
            }

            drawFooter(canvas, pageNo)
            doc.finishPage(page)

            val dir = File(context.cacheDir, "reports").also { it.mkdirs() }
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date(now))
            val file = File(dir, "GlycoAI_Report_$stamp.pdf")
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()

            FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun share(context: Context, uri: android.net.Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Blood Glucose Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share report"))
    }

    // ── drawing helpers ────────────────────────────────────

    private fun textPaint(size: Float, colour: Int, bold: Boolean = false) =
        Paint().apply {
            this.color = colour
            textSize = size
            isAntiAlias = true
            typeface = android.graphics.Typeface.create(
                "sans-serif", if (bold) android.graphics.Typeface.BOLD
                else android.graphics.Typeface.NORMAL
            )
        }

    private fun drawHeader(c: Canvas, top: Float, now: Long): Float {
        var y = top
        c.drawText("GlycoAI", MARGIN, y + 20f, textPaint(20f, DEEP, bold = true))
        c.drawText(
            "Blood Glucose Report", MARGIN, y + 38f, textPaint(12f, GREY)
        )

        val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
        val gen = textPaint(9f, GREY).apply { textAlign = Paint.Align.RIGHT }
        c.drawText("Generated ${fmt.format(Date(now))}", PAGE_W - MARGIN, y + 20f, gen)

        y += 50f
        c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply {
            color = TEAL; strokeWidth = 1.5f
        })
        return y + 18f
    }

    private fun drawPatient(c: Canvas, top: Float, p: UserProfileData): Float {
        var y = top
        val label = textPaint(9f, GREY)
        val value = textPaint(11f, AColor.BLACK)

        val cols = listOf(
            "Name" to p.name.ifBlank { "-" },
            "Age" to (if (p.age > 0) p.age.toString() else "-"),
            "Sex" to p.sex.ifBlank { "-" },
            "Diabetes" to p.diabetesType.ifBlank { "-" }
        )
        val colW = (PAGE_W - 2 * MARGIN) / cols.size
        cols.forEachIndexed { i, (l, v) ->
            val x = MARGIN + i * colW
            c.drawText(l.uppercase(), x, y + 9f, label)
            c.drawText(v, x, y + 24f, value)
        }
        y += 36f

        val meds = p.medications.filter { it.isNotBlank() }
        if (meds.isNotEmpty()) {
            c.drawText("MEDICATIONS", MARGIN, y + 9f, label)
            c.drawText(meds.joinToString(", "), MARGIN, y + 24f, textPaint(10f, AColor.BLACK))
            y += 34f
        }

        val conds = (p.otherConditions + p.complications).distinct().filter { it.isNotBlank() }
        if (conds.isNotEmpty()) {
            c.drawText("OTHER CONDITIONS", MARGIN, y + 9f, label)
            c.drawText(conds.joinToString(", "), MARGIN, y + 24f, textPaint(10f, AColor.BLACK))
            y += 34f
        }

        if (p.hba1c != null) {
            c.drawText("LAST HbA1c", MARGIN, y + 9f, label)
            c.drawText("${p.hba1c}%", MARGIN, y + 24f, textPaint(10f, AColor.BLACK))
            y += 34f
        }

        return y + 4f
    }

    private fun drawRangeAndStats(
        c: Canvas, top: Float, rows: List<GlucoseReading>,
        from: Long, to: Long, rangeDays: Int
    ): Float {
        var y = top
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

        c.drawText(
            "Last $rangeDays days  ·  ${fmt.format(Date(from))} to ${fmt.format(Date(to))}",
            MARGIN, y + 12f, textPaint(11f, DEEP, bold = true)
        )
        y += 24f

        // All maths in mg/dL, matching sevenDaySummary() in the app.
        val v = rows.map { if (it.unit == "mmol/L") it.value * 18f else it.value }
        val stats = if (v.isEmpty()) listOf(
            "Readings" to "0", "Average" to "-", "Highest" to "-",
            "Lowest" to "-", "In range" to "-"
        ) else listOf(
            "Readings" to v.size.toString(),
            "Average" to "${v.average().toInt()} mg/dL",
            "Highest" to "${v.max().toInt()} mg/dL",
            "Lowest" to "${v.min().toInt()} mg/dL",
            "In range" to "${v.count { it in TARGET_LOW..TARGET_HIGH } * 100 / v.size}%"
        )

        val boxH = 40f
        val boxW = (PAGE_W - 2 * MARGIN) / stats.size
        val bg = Paint().apply { color = AColor.rgb(0xE1, 0xF5, 0xEE) }
        c.drawRoundRect(
            RectF(MARGIN, y, PAGE_W - MARGIN, y + boxH), 6f, 6f, bg
        )
        stats.forEachIndexed { i, (l, value) ->
            val cx = MARGIN + i * boxW + boxW / 2
            val lp = textPaint(8f, GREY).apply { textAlign = Paint.Align.CENTER }
            val vp = textPaint(12f, DEEP, bold = true).apply { textAlign = Paint.Align.CENTER }
            c.drawText(l.uppercase(), cx, y + 14f, lp)
            c.drawText(value, cx, y + 31f, vp)
        }
        return y + boxH + 16f
    }

    private fun drawChart(
        c: Canvas, top: Float, rows: List<GlucoseReading>,
        from: Long, to: Long, displayUnit: String
    ): Float {
        val h = 150f
        val left = MARGIN + 30f
        val right = PAGE_W - MARGIN
        val plotW = right - left
        val isMmol = displayUnit == "mmol/L"

        val pts = rows.sortedBy { it.timestamp }
            .map { it.timestamp to (if (it.unit == "mmol/L") it.value * 18f else it.value) }

        // Scale always includes the target band, so "in range" stays visible
        // even when every reading sits well outside it.
        val vals = pts.map { it.second }
        val rawMin = minOf(vals.min(), TARGET_LOW)
        val rawMax = maxOf(vals.max(), TARGET_HIGH)
        val pad = ((rawMax - rawMin) * 0.15f).coerceAtLeast(20f)
        val yMin = (rawMin - pad).coerceAtLeast(0f)
        val yMax = rawMax + pad

        fun yFor(v: Float) = top + h * (1f - (v - yMin) / (yMax - yMin))
        fun xFor(t: Long) = left + plotW * ((t - from).toFloat() / (to - from).toFloat())

        // Target band
        c.drawRect(
            RectF(left, yFor(TARGET_HIGH), right, yFor(TARGET_LOW)),
            Paint().apply { color = AColor.argb(34, 0x4C, 0xAF, 0x50) }
        )

        val axis = textPaint(8f, GREY).apply { textAlign = Paint.Align.RIGHT }
        listOf(yMax, TARGET_HIGH, TARGET_LOW, yMin).forEach { v ->
            val yy = yFor(v)
            if (v == TARGET_HIGH || v == TARGET_LOW) {
                c.drawLine(left, yy, right, yy, Paint().apply {
                    color = AColor.argb(90, 0x4C, 0xAF, 0x50); strokeWidth = 0.8f
                })
            }
            val lbl = if (isMmol) String.format(Locale.US, "%.1f", v / 18f)
            else v.toInt().toString()
            c.drawText(lbl, left - 4f, yy + 3f, axis)
        }

        // Line, broken across gaps - a straight line through days with no
        // readings would imply measurements that never happened.
        val gap = if ((to - from) <= 7 * DAY_MS) DAY_MS else 3 * DAY_MS
        val linePaint = Paint().apply {
            color = TEAL; strokeWidth = 1.6f; isAntiAlias = true
            style = Paint.Style.STROKE
        }
        for (i in 1 until pts.size) {
            val (t0, v0) = pts[i - 1]
            val (t1, v1) = pts[i]
            if (t1 - t0 <= gap) {
                c.drawLine(xFor(t0), yFor(v0), xFor(t1), yFor(v1), linePaint)
            }
        }

        pts.forEach { (t, v) ->
            val colour = when {
                v < TARGET_LOW -> AColor.rgb(0x21, 0x96, 0xF3)
                v <= 140f -> AColor.rgb(0x4C, 0xAF, 0x50)
                v <= TARGET_HIGH -> AColor.rgb(0xFF, 0xC1, 0x07)
                else -> AColor.rgb(0xF4, 0x43, 0x36)
            }
            c.drawCircle(xFor(t), yFor(v), 2.6f, Paint().apply {
                color = colour; isAntiAlias = true
            })
        }

        val dfmt = SimpleDateFormat("d MMM", Locale.getDefault())
        c.drawText(dfmt.format(Date(from)), left, top + h + 12f, textPaint(8f, GREY))
        c.drawText(
            dfmt.format(Date(to)), right, top + h + 12f,
            textPaint(8f, GREY).apply { textAlign = Paint.Align.RIGHT }
        )

        return top + h + 24f
    }

    private val COL_X = floatArrayOf(40f, 105f, 160f, 235f, 360f)

    private fun drawTableHeader(c: Canvas, top: Float): Float {
        val p = textPaint(8f, GREY, bold = true)
        val heads = listOf("DATE", "TIME", "READING", "WHEN TAKEN", "NOTE")
        heads.forEachIndexed { i, hd -> c.drawText(hd, COL_X[i], top + 9f, p) }
        val y = top + 14f
        c.drawLine(MARGIN, y, PAGE_W - MARGIN, y, Paint().apply {
            color = LIGHT; strokeWidth = 1f
        })
        return y + 6f
    }

    private fun drawRow(c: Canvas, top: Float, r: GlucoseReading): Float {
        val dfmt = SimpleDateFormat("d MMM yy", Locale.getDefault())
        val tfmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val body = textPaint(9f, AColor.BLACK)

        val mgdl = if (r.unit == "mmol/L") r.value * 18f else r.value
        val statusColour = when {
            mgdl < TARGET_LOW -> AColor.rgb(0x21, 0x96, 0xF3)
            mgdl <= 140f -> AColor.rgb(0x2E, 0x7D, 0x32)
            mgdl <= TARGET_HIGH -> AColor.rgb(0xF9, 0xA8, 0x25)
            else -> AColor.rgb(0xD3, 0x2F, 0x2F)
        }

        c.drawText(dfmt.format(Date(r.timestamp)), COL_X[0], top + 10f, body)
        c.drawText(tfmt.format(Date(r.timestamp)), COL_X[1], top + 10f, body)
        c.drawText(
            "${trimNum(r.value)} ${r.unit}", COL_X[2], top + 10f,
            textPaint(9f, statusColour, bold = true)
        )
        c.drawText(ellipsize(r.readingType, 20), COL_X[3], top + 10f, body)
        c.drawText(ellipsize(r.note, 28), COL_X[4], top + 10f, textPaint(8f, GREY))

        return top + 18f
    }

    private fun drawFooter(c: Canvas, pageNo: Int) {
        val y = PAGE_H - MARGIN + 6f
        c.drawLine(MARGIN, y - 14f, PAGE_W - MARGIN, y - 14f, Paint().apply {
            color = LIGHT; strokeWidth = 1f
        })
        c.drawText(
            "Patient-recorded readings. Not a clinical measurement or diagnosis.",
            MARGIN, y, textPaint(7.5f, GREY)
        )
        c.drawText(
            "Page $pageNo", PAGE_W - MARGIN, y,
            textPaint(7.5f, GREY).apply { textAlign = Paint.Align.RIGHT }
        )
    }

    private fun trimNum(v: Float): String =
        if (v == v.toInt().toFloat()) v.toInt().toString()
        else String.format(Locale.US, "%.1f", v)

    private fun ellipsize(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1) + "…"
}