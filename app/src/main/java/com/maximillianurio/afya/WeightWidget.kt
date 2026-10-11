package com.maximillianurio.afya

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.widget.RemoteViews
import com.maximillianurio.afya.core.BodyComposition
import com.maximillianurio.afya.core.DateFmt
import com.maximillianurio.afya.core.TrendWeight
import com.maximillianurio.afya.core.Units
import com.maximillianurio.afya.core.deltaParts
import com.maximillianurio.afya.data.LocalStore
import com.maximillianurio.afya.data.ReadingsDb

/**
 * The home-screen card: the last weight in the profile unit, its change against the one before,
 * and the last thirty days as a line, with the trend drawn over it once there is enough history.
 * It holds nothing of its own; it is redrawn from the database whenever a weigh-in is filed or
 * deleted, and tapping it opens the app on the platform.
 */
class WeightWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val done = goAsync()
        Thread {
            try {
                render(context.applicationContext, manager, ids)
            } finally {
                done.finish()
            }
        }.start()
    }

    companion object {
        private const val DAYS = 30
        private const val SPARK_W = 600
        private const val SPARK_H = 150

        /** Redraw every placed widget. Safe to call when none are placed; it does nothing then. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, WeightWidget::class.java))
            if (ids.isEmpty()) return
            Thread { render(app, manager, ids) }.start()
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val unit = LocalStore(context).unit
            val month = ReadingsDb(context).use { db ->
                db.since(System.currentTimeMillis() - DAYS * 86_400_000L)
                    .filter { BodyComposition.bodyPlausible(it.grams) }
            }
            val last = ReadingsDb(context).use { db -> db.recent(2) }
                .filter { BodyComposition.bodyPlausible(it.grams) }
            val newest = last.firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_weight)

            if (newest == null) {
                views.setTextViewText(R.id.widget_weight, context.getString(R.string.widget_empty_figure))
                views.setTextViewText(R.id.widget_unit, "")
                views.setTextViewText(R.id.widget_change, context.getString(R.string.widget_empty))
                views.setImageViewBitmap(R.id.widget_spark, null)
            } else {
                views.setTextViewText(R.id.widget_weight, Units.valueText(newest.grams, unit))
                views.setTextViewText(R.id.widget_unit, unit.label)
                val before = last.getOrNull(1)
                val change = before?.let { deltaParts(newest.kilograms - it.kilograms, unit).second }
                views.setTextViewText(
                    R.id.widget_change,
                    listOfNotNull(change, DateFmt.dayLabel(newest.timestampMs)).joinToString("  ·  "),
                )
                val points = month.asReversed().map { it.timestampMs to it.kilograms }
                views.setImageViewBitmap(R.id.widget_spark, spark(context, points))
            }
            views.setContentDescription(
                android.R.id.background,
                newest?.let {
                    context.getString(R.string.widget_spoken, Units.format(it.grams, unit), DateFmt.dayLabel(it.timestampMs))
                } ?: context.getString(R.string.widget_empty),
            )
            views.setOnClickPendingIntent(
                android.R.id.background,
                PendingIntent.getActivity(
                    context,
                    1,
                    MainActivity.weighInIntent(context),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            manager.updateAppWidget(ids, views)
        }

        /** The month as a line: readings faint, the trend over them when it means something. */
        private fun spark(context: Context, points: List<Pair<Long, Double>>): Bitmap? {
            if (points.size < 2) return null
            val bitmap = Bitmap.createBitmap(SPARK_W, SPARK_H, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val brand = context.getColor(R.color.widget_brand)
            val trend = if (TrendWeight.meaningful(points)) TrendWeight.series(points) else null
            val values = points.map { it.second } + trend.orEmpty().map { it.second }
            var lo = values.min()
            var hi = values.max()
            if (hi - lo < 0.6) {
                val middle = (hi + lo) / 2
                lo = middle - 0.3
                hi = middle + 0.3
            }
            val t0 = points.first().first.toDouble()
            val span = (points.last().first - points.first().first).toDouble().coerceAtLeast(1.0)
            val pad = 8f
            fun x(time: Long) = pad + ((time - t0) / span).toFloat() * (SPARK_W - 2 * pad)
            fun y(value: Double) = SPARK_H - pad - ((value - lo) / (hi - lo)).toFloat() * (SPARK_H - 2 * pad)
            fun pathOf(series: List<Pair<Long, Double>>) = Path().apply {
                series.forEachIndexed { i, (time, value) ->
                    if (i == 0) moveTo(x(time), y(value)) else lineTo(x(time), y(value))
                }
            }
            val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = brand
            }
            if (trend == null) {
                canvas.drawPath(pathOf(points), stroke.apply { strokeWidth = 6f })
            } else {
                canvas.drawPath(pathOf(points), stroke.apply { strokeWidth = 3f; alpha = 90 })
                canvas.drawPath(pathOf(trend), stroke.apply { strokeWidth = 7f; alpha = 255 })
            }
            val (lastTime, lastValue) = points.last()
            canvas.drawCircle(x(lastTime), y(lastValue), 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = brand })
            return bitmap
        }
    }
}
