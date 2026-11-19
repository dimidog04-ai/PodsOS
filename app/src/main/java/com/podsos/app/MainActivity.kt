package com.podsos.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {

    private val bg = Color.rgb(12, 14, 20)
    private val panel = Color.rgb(27, 30, 40)
    private val accent = Color.rgb(150, 125, 255)
    private val muted = Color.rgb(155, 160, 177)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 28, 22, 24)
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this)
        scroll.addView(root)

        root.addView(text("PodsOS", 32, Color.WHITE, true))
        root.addView(text("YOUR AUDIO. YOUR CONTROL.", 11, muted, true))
        root.addView(space(26))

        val device = card()
        device.addView(text("YOUR DEVICE", 11, muted, true))
        device.addView(space(10))
        device.addView(text("AirPods 4 ANC", 23, Color.WHITE, true))
        device.addView(text("Not connected", 14, muted))
        device.addView(space(18))

        val battery = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        battery.addView(batteryItem("LEFT", "--"), LinearLayout.LayoutParams(0, -2, 1f))
        battery.addView(batteryItem("RIGHT", "--"), LinearLayout.LayoutParams(0, -2, 1f))
        battery.addView(batteryItem("CASE", "--"), LinearLayout.LayoutParams(0, -2, 1f))

        device.addView(battery)
        root.addView(device)

        root.addView(space(22))
        root.addView(text("NOISE CONTROL", 13, muted, true))
        root.addView(space(10))

        val modes = listOf(
            "Noise Cancellation",
            "Transparency",
            "Adaptive Audio",
            "Off"
        )

        modes.forEach { mode ->
            val button = Button(this).apply {
                text = mode
                textSize = 14f
                setTextColor(Color.WHITE)
                background = rounded(panel, 18)
                isAllCaps = false

                setOnClickListener {
                    Toast.makeText(
                        this@MainActivity,
                        "Connect AirPods to use this feature",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            root.addView(
                button,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    58
                ).apply {
                    bottomMargin = 8
                }
            )
        }

        root.addView(space(20))
        root.addView(text("QUICK SETTINGS", 13, muted, true))
        root.addView(space(10))

        val settings = card()
        settings.addView(text("AirPods Controls", 18, Color.WHITE, true))
        settings.addView(space(8))
        settings.addView(
            text(
                "Battery monitoring, ear detection, gestures and advanced audio settings.",
                14,
                muted
            )
        )

        root.addView(settings)
        root.addView(space(28))
        root.addView(text("PodsOS  •  Version 1.0.0", 12, muted))

        setContentView(scroll)
    }

    private fun batteryItem(name: String, value: String): LinearLayout {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        item.addView(text(name, 10, muted, true))
        item.addView(text(value, 22, Color.WHITE, true))
        return item
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            background = rounded(panel, 22)
        }
    }

    private fun text(
        value: String,
        size: Int,
        color: Int,
        bold: Boolean = false
    ): TextView {
        return TextView(this).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }
    }

    private fun rounded(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }
}
