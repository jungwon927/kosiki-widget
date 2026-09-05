package io.github.jungwon927.kosiki

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.RemoteViews

/** 앱이 띄우는 주소. 화면과 기능은 전부 이 사이트에서 온다. */
const val APP_URL = "https://jungwon927.github.io/index.html?native=1"

/** 위젯이 읽는 저장소. 웹 화면에서 기록할 때마다 여기에 옮겨 적는다. */
object Store {
    private const val NAME = "kosiki"

    fun save(ctx: Context, floor: String, area: String, ts: Long) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString("floor", floor)
            .putString("area", area)
            .putLong("ts", ts)
            .apply()
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun read(ctx: Context): Triple<String, String, Long> {
        val p = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return Triple(
            p.getString("floor", "") ?: "",
            p.getString("area", "") ?: "",
            p.getLong("ts", 0L)
        )
    }
}

/** 웹 화면(JavaScript)에서 부르는 다리. */
class Bridge(private val ctx: Context) {

    @JavascriptInterface
    fun saveSpot(floor: String, area: String, ts: String) {
        Store.save(ctx, floor, area, ts.toDoubleOrNull()?.toLong() ?: System.currentTimeMillis())
        ParkingWidget.refresh(ctx)
    }

    @JavascriptInterface
    fun clearSpot() {
        Store.clear(ctx)
        ParkingWidget.refresh(ctx)
    }
}

class MainActivity : Activity() {

    private lateinit var web: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.webViewClient = WebViewClient()
        web.addJavascriptInterface(Bridge(applicationContext), "KosikiNative")
        web.loadUrl(APP_URL)
    }

    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else super.onBackPressed()
    }
}

class ParkingWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(ctx, mgr, it) }
    }

    companion object {

        fun refresh(ctx: Context) {
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ParkingWidget::class.java))
            ids.forEach { render(ctx, mgr, it) }
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val (floor, area, ts) = Store.read(ctx)
            val views = RemoteViews(ctx.packageName, R.layout.widget_parking)

            val spot = listOf(floor, area).filter { it.isNotEmpty() }.joinToString(" · ")
            views.setTextViewText(R.id.widget_spot, if (spot.isEmpty()) "기록 없음" else spot)
            views.setTextViewText(R.id.widget_ago, if (ts == 0L) "눌러서 기록하기" else ago(ts))

            val open = PendingIntent.getActivity(
                ctx, 0,
                Intent(ctx, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            mgr.updateAppWidget(id, views)
        }

        private fun ago(ts: Long): String {
            val m = (System.currentTimeMillis() - ts) / 60000
            return when {
                m < 1 -> "방금 주차"
                m < 60 -> "${m}분 전 주차"
                m < 60 * 24 -> "${m / 60}시간 전 주차"
                else -> "${m / (60 * 24)}일 전 주차"
            }
        }
    }
}
