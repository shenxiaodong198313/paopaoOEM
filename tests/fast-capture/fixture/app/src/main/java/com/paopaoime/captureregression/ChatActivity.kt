package com.paopaoime.captureregression
import android.app.Application
import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.widget.*
import com.paopaoime.aikeyboard.sdk.core.OemHostRuntime
class RegressionApplication : Application() {
    override fun onCreate() { super.onCreate(); OemHostRuntime.initialize(this) }
}
class ChatActivity : Activity() {
    lateinit var scroll: ScrollView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        scroll = ScrollView(this)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(225,245,238)) }
        repeat(80) { i -> rows.addView(TextView(this).apply { text = "TEST CHAT ${i + 1}: synthetic message for capture and swipe regression."; textSize = 22f; setTextColor(Color.BLACK); setPadding(24,32,24,32) }) }
        scroll.addView(rows); setContentView(scroll)
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }
}
