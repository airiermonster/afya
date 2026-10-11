package com.maximillianurio.afya.data

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.PathParser
import com.maximillianurio.afya.R
import com.maximillianurio.afya.core.Band
import com.maximillianurio.afya.ui.brand.AfyaMark
import com.maximillianurio.afya.ui.theme.BandHealthy
import com.maximillianurio.afya.ui.theme.BandHigh
import com.maximillianurio.afya.ui.theme.BandLow
import com.maximillianurio.afya.ui.theme.BandWarn
import com.maximillianurio.afya.ui.theme.Brand
import com.maximillianurio.afya.ui.theme.BrandDeep
import com.maximillianurio.afya.ui.theme.BrandWash
import com.maximillianurio.afya.ui.theme.Card
import com.maximillianurio.afya.ui.theme.CardTint
import com.maximillianurio.afya.ui.theme.Hairline
import com.maximillianurio.afya.ui.theme.HueAmber
import com.maximillianurio.afya.ui.theme.HueAqua
import com.maximillianurio.afya.ui.theme.HueCoral
import com.maximillianurio.afya.ui.theme.HueGrass
import com.maximillianurio.afya.ui.theme.HuePlum
import com.maximillianurio.afya.ui.theme.HueRose
import com.maximillianurio.afya.ui.theme.HueSlate
import com.maximillianurio.afya.ui.theme.HueTeal
import com.maximillianurio.afya.ui.theme.Ink
import com.maximillianurio.afya.ui.theme.InkFaint
import com.maximillianurio.afya.ui.theme.InkMuted
import java.io.File
import kotlin.math.max

/**
 * The printed sheet: A4 portrait measured in points, the app's own light palette, and Manrope
 * instanced per weight exactly the way the interface instances it.
 *
 * Paper is always the light scheme. A document a clinician reads has to be black ink on white
 * whatever the phone was set to when it was written, so the night roles are deliberately not
 * consulted here, and every hex comes from `ui/theme` rather than being repeated.
 */
internal object Paper {
    /** A4 portrait at 72 dpi, which is the unit `PdfDocument` draws in. */
    const val WIDTH = 595f
    const val HEIGHT = 842f
    const val MARGIN = 44f

    /** The live width between the two side margins. */
    const val CONTENT = WIDTH - 2 * MARGIN

    /** Nothing in the body may cross this line; the footer owns the strip below it. */
    const val FOOT = HEIGHT - 74f

    val ink = Ink.toArgb()
    val inkMuted = InkMuted.toArgb()
    val inkFaint = InkFaint.toArgb()
    val hairline = Hairline.toArgb()
    val tint = CardTint.toArgb()
    val white = Card.toArgb()
    val brand = Brand.toArgb()
    val brandDeep = BrandDeep.toArgb()
    val brandWash = BrandWash.toArgb()

    /** The metric hues, so a figure in the report is the same colour as it is on screen. */
    fun hue(name: String): Int = when (name) {
        "weight", "bmi" -> HueTeal.toArgb()
        "water", "water_mass" -> HueAqua.toArgb()
        "muscle", "muscle_mass", "skeletal", "skeletal_mass", "lean" -> HueGrass.toArgb()
        "fat", "fat_mass" -> HueAmber.toArgb()
        "visceral", "obesity_degree" -> HueCoral.toArgb()
        "bone", "metabolic_age" -> HuePlum.toArgb()
        "protein", "protein_mass" -> HueSlate.toArgb()
        "bmr" -> HueRose.toArgb()
        else -> HueTeal.toArgb()
    }

    /** The band colour, always printed beside the band's word and never instead of it. */
    fun band(band: Band): Int = when (band) {
        Band.Low -> BandLow.toArgb()
        Band.Below, Band.Above -> BandWarn.toArgb()
        Band.Healthy -> BandHealthy.toArgb()
        Band.High, Band.Obese -> BandHigh.toArgb()
        Band.Unknown -> inkFaint
    }

    /** Same colour with the alpha the screen version uses when it stains a ground. */
    fun alpha(color: Int, fraction: Float): Int {
        val a = (fraction.coerceIn(0f, 1f) * 255f).toInt()
        return (a shl 24) or (color and 0x00FFFFFF)
    }
}

/** One typeface at one weight, plus whether bold has to be faked because the axis was refused. */
internal class Face(val typeface: Typeface, val syntheticBold: Boolean)

/**
 * Manrope is a single variable file in `res/font`, so the sheet copies it to its own cache once and
 * asks the platform to instance the `wght` axis per weight. If the platform refuses the axis the
 * face falls back to the default instance with synthetic bold above 600, which still reads.
 */
internal object PaperType {
    private val faces = HashMap<Int, Face>()
    private var axisWorks = true

    private const val CACHED = "afya-manrope-variable.ttf"

    private fun source(context: Context): File {
        val target = File(context.cacheDir, CACHED)
        if (!target.exists() || target.length() == 0L) {
            context.resources.openRawResource(R.font.manrope_variable).use { input ->
                target.outputStream().use(input::copyTo)
            }
        }
        return target
    }

    fun of(context: Context, weight: Int): Face = synchronized(faces) {
        faces.getOrPut(weight) {
            val file = source(context)
            val instanced = if (axisWorks) {
                runCatching {
                    Typeface.Builder(file.absolutePath)
                        .setFontVariationSettings("'wght' $weight")
                        .build()
                }.getOrNull()
            } else {
                null
            }
            if (instanced != null) {
                Face(instanced, syntheticBold = false)
            } else {
                axisWorks = false
                Face(Typeface.createFromFile(file), syntheticBold = weight >= 600)
            }
        }
    }
}

/**
 * A page at a time, top to bottom. Callers move [y] by the height they just drew, and [needs] breaks
 * to a fresh sheet when a block would collide with the footer. The page count is fixed before the
 * first stroke so the footer can print "3 / 7"; the body of the record is laid out in whole blocks,
 * which is what makes that knowable in advance.
 */
internal class Sheet(
    private val context: Context,
    private val runningHead: String,
    private val footerNote: String,
    private val total: Int,
) {
    private val document = PdfDocument()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var page: PdfDocument.Page? = null
    private var canvas: Canvas? = null
    private val name = context.getString(R.string.app_name)

    /** The top of the next block. */
    var y = 0f
    var number = 0
        private set

    fun addPage() {
        closePage()
        number += 1
        val info = PdfDocument.PageInfo.Builder(Paper.WIDTH.toInt(), Paper.HEIGHT.toInt(), number).create()
        val started = document.startPage(info)
        page = started
        canvas = started.canvas
        y = if (number == 1) Paper.MARGIN else {
            drawRunningHead()
            76f
        }
    }

    fun finish(): ByteArray {
        closePage()
        val out = java.io.ByteArrayOutputStream()
        document.writeTo(out)
        document.close()
        return out.toByteArray()
    }

    /** True when a block of [height] had to move to a new sheet. */
    fun needs(height: Float): Boolean {
        if (y + height > Paper.FOOT) {
            addPage()
            return true
        }
        return false
    }

    private fun drawRunningHead() {
        mark(Paper.MARGIN - 2f, 38f, 16f, Paper.brand)
        text(this.name, Paper.MARGIN + 17f, 42f, 9f, 700, Paper.brandDeep)
        text(runningHead, Paper.WIDTH - Paper.MARGIN, 43f, 8f, 400, Paper.inkMuted, Paint.Align.RIGHT)
        rule(Paper.MARGIN, Paper.MARGIN + Paper.CONTENT, 60f)
    }

    private fun closePage() {
        val c = canvas ?: return
        rule(Paper.MARGIN, Paper.MARGIN + Paper.CONTENT, Paper.HEIGHT - 56f)
        text(footerNote, Paper.MARGIN, Paper.HEIGHT - 49f, 7.2f, 400, Paper.inkMuted)
        text("$number / $total", Paper.WIDTH - Paper.MARGIN, Paper.HEIGHT - 49f, 7.2f, 700, Paper.brandDeep, Paint.Align.RIGHT)
        document.finishPage(page)
        canvas = null
        page = null
    }

    // ---- primitives ----

    /** Draws with the top of the line at [top] and returns the width the text took. */
    fun text(
        value: String,
        x: Float,
        top: Float,
        size: Float,
        weight: Int = 400,
        color: Int = Paper.ink,
        align: Paint.Align = Paint.Align.LEFT,
        tracking: Float = 0f,
    ): Float {
        val c = canvas ?: return 0f
        val p = prepare(size, weight, color, align, tracking)
        val metrics = p.fontMetrics
        c.drawText(value, x, top - metrics.ascent, p)
        return p.measureText(value)
    }

    fun width(value: String, size: Float, weight: Int = 400, tracking: Float = 0f): Float {
        val p = prepare(size, weight, Paper.ink, Paint.Align.LEFT, tracking)
        return p.measureText(value)
    }

    /** Greedy line breaking on spaces, so a paragraph stays inside [maxWidth]. */
    fun wrap(value: String, size: Float, weight: Int, maxWidth: Float): List<String> {
        val p = prepare(size, weight, Paper.ink, Paint.Align.LEFT, 0f)
        val out = ArrayList<String>()
        var line = StringBuilder()
        for (word in value.split(' ')) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(candidate) <= maxWidth || line.isEmpty()) {
                line = StringBuilder(candidate)
            } else {
                out += line.toString()
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) out += line.toString()
        return out
    }

    fun rule(from: Float, to: Float, at: Float, thickness: Float = 0.8f, color: Int = Paper.hairline) {
        val c = canvas ?: return
        paint.pathEffect = null
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = thickness
        paint.color = color
        c.drawLine(from, at, to, at, paint)
        paint.style = Paint.Style.FILL
    }

    fun dashed(from: Float, to: Float, at: Float, color: Int, intervals: Float = 4f) {
        val c = canvas ?: return
        paint.pathEffect = DashPathEffect(floatArrayOf(intervals, intervals * 0.75f), 0f)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 0.9f
        paint.color = color
        c.drawLine(from, at, to, at, paint)
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
    }

    fun fill(left: Float, top: Float, width: Float, height: Float, color: Int, radius: Float = 0f) {
        val c = canvas ?: return
        paint.color = color
        if (radius > 0f) {
            c.drawRoundRect(RectF(left, top, left + width, top + height), radius, radius, paint)
        } else {
            c.drawRect(left, top, left + width, top + height, paint)
        }
    }

    fun stroke(left: Float, top: Float, width: Float, height: Float, color: Int, radius: Float, thickness: Float) {
        val c = canvas ?: return
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = thickness
        paint.color = color
        c.drawRoundRect(RectF(left, top, left + width, top + height), radius, radius, paint)
        paint.style = Paint.Style.FILL
    }

    /** A rounded band in the icon's lit pine teal: the banner ground on the first sheet. */
    fun brandBand(left: Float, top: Float, width: Float, height: Float, radius: Float) {
        val c = canvas ?: return
        paint.shader = LinearGradient(
            left, top, left + width, top + height,
            AfyaMark.GroundLight.toArgb(), AfyaMark.GroundDeep.toArgb(), Shader.TileMode.CLAMP,
        )
        c.drawRoundRect(RectF(left, top, left + width, top + height), radius, radius, paint)
        paint.shader = null
    }

    /** The Afya mark, its 48-unit grid scaled to [side] points with the top-left corner at [left], [top]. */
    fun mark(left: Float, top: Float, side: Float, color: Int) {
        val c = canvas ?: return
        val k = side / AfyaMark.GRID
        c.save()
        c.translate(left, top)
        c.scale(k, k)
        paint.color = color
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        markPaths.forEach { (path, width) ->
            paint.strokeWidth = width
            c.drawPath(path, paint)
        }
        paint.style = Paint.Style.FILL
        c.drawCircle(24f, 24f, AfyaMark.HUB_RADIUS, paint)
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.MITER
        c.restore()
    }

    private val markPaths: List<Pair<Path, Float>> by lazy {
        listOf(
            PathParser.createPathFromPathData(AfyaMark.BOWL) to AfyaMark.BOWL_WIDTH,
            PathParser.createPathFromPathData(AfyaMark.STEM) to AfyaMark.STEM_WIDTH,
            PathParser.createPathFromPathData(AfyaMark.NEEDLE) to AfyaMark.NEEDLE_WIDTH,
        )
    }

    fun dot(cx: Float, cy: Float, radius: Float, color: Int, ring: Int? = null) {
        val c = canvas ?: return
        if (ring != null) {
            paint.color = ring
            c.drawCircle(cx, cy, radius + 1.6f, paint)
        }
        paint.color = color
        c.drawCircle(cx, cy, radius, paint)
    }

    /** Draws one open path (a trend line) and, optionally, the area beneath it down to [base]. */
    fun curve(
        points: List<Pair<Float, Float>>,
        color: Int,
        stroke: Float,
        areaTo: Float? = null,
        areaColor: Int? = null,
    ) {
        val c = canvas ?: return
        if (points.size < 2) return
        val line = Path()
        points.forEachIndexed { index, (x, yy) ->
            if (index == 0) line.moveTo(x, yy) else line.lineTo(x, yy)
        }
        if (areaTo != null && areaColor != null) {
            val area = Path(line)
            area.lineTo(points.last().first, areaTo)
            area.lineTo(points.first().first, areaTo)
            area.close()
            paint.style = Paint.Style.FILL
            paint.color = areaColor
            c.drawPath(area, paint)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = stroke
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color
        c.drawPath(line, paint)
        paint.style = Paint.Style.FILL
        paint.strokeCap = Paint.Cap.BUTT
        paint.strokeJoin = Paint.Join.MITER
    }

    private fun prepare(size: Float, weight: Int, color: Int, align: Paint.Align, tracking: Float): Paint {
        val face = PaperType.of(context, weight)
        paint.typeface = face.typeface
        paint.isFakeBoldText = face.syntheticBold
        paint.textSize = size
        paint.color = color
        paint.textAlign = align
        paint.letterSpacing = tracking
        paint.pathEffect = null
        paint.style = Paint.Style.FILL
        return paint
    }
}
