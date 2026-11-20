
package com.podsos.app

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothA2dp
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.*
import android.widget.FrameLayout

class MainActivity : Activity() {

    private val isDark: Boolean
        get() = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

    private val bg: Int
        get() = if (isDark) Color.rgb(9, 7, 19) else Color.rgb(247, 243, 255)

    private val panel: Int
        get() = if (isDark) Color.rgb(27, 16, 42) else Color.WHITE

    private val muted: Int
        get() = if (isDark) Color.rgb(181, 160, 205) else Color.rgb(105, 83, 130)

    private val accent = Color.rgb(168, 85, 247)
    private val glow = Color.rgb(192, 132, 252)

    private val primaryText: Int
        get() = if (isDark) Color.WHITE else Color.rgb(32, 19, 48)

    private val borderColor: Int
        get() = if (isDark) Color.rgb(88, 52, 127) else Color.rgb(218, 197, 240)

    private lateinit var statusText: TextView

    private var receiverRegistered = false
    private var a2dpProxy: BluetoothProfile? = null
    private var checkingProfile = false
    private lateinit var batteryScanner: AirPodsBattery
    private lateinit var scanStatusText: TextView

    private val batteryValueViews = mutableMapOf<String, TextView>()

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

            if (!::statusText.isInitialized) return

            try {
                val device = getBluetoothDevice(intent)
                val name = device?.name ?: ""

                if (!name.contains("AirPods", ignoreCase = true)) {
                    return
                }

                when (intent.action) {

                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(
                            BluetoothProfile.EXTRA_STATE,
                            BluetoothProfile.STATE_DISCONNECTED
                        )

                        when (state) {
                            BluetoothProfile.STATE_CONNECTED ->
                                statusText.text = "Connected: $name"

                            BluetoothProfile.STATE_DISCONNECTED ->
                                statusText.text = "Disconnected"
                        }
                    }

                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        statusText.text = "Connected: $name"
                    }

                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        statusText.text = "Disconnected"
                    }
                }

            } catch (e: SecurityException) {
                statusText.text = "Bluetooth permission required"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.statusBarColor = bg
        window.navigationBarColor = bg

        if (Build.VERSION.SDK_INT >= 23) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                if (isDark) 0 else (
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                    View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                )
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
        }

        val pageContainer = FrameLayout(this)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 24)
        }

        fun heading(value: String): TextView {
            return text(value, 12, glow, true).apply {
                letterSpacing = 0.12f
            }
        }

        fun cyberCard(): LinearLayout {
            return card().apply {
                background = rounded(panel, 26).apply {
                    setStroke(1, borderColor)
                }
            }
        }

        // HEADER
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        brand.addView(text("PodsOS", 32, primaryText, true))
        brand.addView(
            text("NEXT-GEN AUDIO INTERFACE", 10, muted, true)
        )

        header.addView(
            brand,
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        val online = TextView(this).apply {
            text = "  SYSTEM ONLINE  "
            textSize = 10f
            setTextColor(glow)
            setPadding(12, 12, 12, 12)
            background = rounded(
                if (isDark) Color.rgb(48, 28, 68)
                else Color.rgb(237, 222, 255), 30
            )
        }

        header.addView(online)
        content.addView(header)
        content.addView(space(26))

        // DEVICE PANEL
        val device = cyberCard()

        device.addView(heading("01 / DEVICE CONNECTION"))
        device.addView(space(16))

        device.addView(
            text("AirPods 4 ANC", 25, primaryText, true)
        )
        device.addView(space(8))

        statusText = text(
            "Checking Bluetooth connection...",
            14,
            muted
        )
        device.addView(statusText)
        device.addView(space(20))

        val batteryTitle = text(
            "BATTERY TELEMETRY",
            11,
            glow,
            true
        )
        device.addView(batteryTitle)
        device.addView(space(12))

        val battery = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        battery.addView(
            batteryItem("LEFT", "--"),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        battery.addView(
            batteryItem("RIGHT", "--"),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        battery.addView(
            batteryItem("CASE", "--"),
            LinearLayout.LayoutParams(0, -2, 1f)
        )

        device.addView(battery)
        device.addView(space(18))

        scanStatusText = text(
            "Battery scanner waiting for permission...",
            11,
            muted
        )
        device.addView(scanStatusText)

        content.addView(device)
        content.addView(space(18))

        batteryScanner = AirPodsBattery(
            onBatteryUpdate = { data ->
                runOnUiThread {
                    updateBatteryDisplay(data)
                }
            },
            onStatusUpdate = { message ->
                runOnUiThread {
                    scanStatusText.text = message
                }
            }
        )

        // CONNECTION REFRESH
        val refresh = Button(this).apply {
            text = "⟳   REFRESH CONNECTION"
            textSize = 13f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(accent, 18)
            setOnClickListener {
                checkBluetoothPermission()
            }
        }

        content.addView(
            refresh,
            LinearLayout.LayoutParams(
                -1, 54
            )
        )
        content.addView(space(26))

        // AUDIO ENVIRONMENT
        content.addView(heading("02 / AUDIO ENVIRONMENT"))
        content.addView(space(12))

        val audioPanel = cyberCard()
        audioPanel.addView(
            text("Noise Control", 20, primaryText, true)
        )
        audioPanel.addView(space(14))

        val audioModes = listOf(
            "Noise Cancellation",
            "Transparency",
            "Adaptive Audio",
            "Off"
        )

        audioModes.forEach { mode ->
            val modeButton = Button(this).apply {
                text = mode
                textSize = 13f
                isAllCaps = false
                setTextColor(primaryText)
                background = rounded(
                    if (isDark) Color.rgb(53, 34, 72)
                    else Color.rgb(241, 231, 255), 16
                )

                setOnClickListener {
                    Toast.makeText(
                        this@MainActivity,
                        "AirPods mode control is not available yet",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            audioPanel.addView(
                modeButton,
                LinearLayout.LayoutParams(-1, 52).apply {
                    bottomMargin = 8
                }
            )
        }

        content.addView(audioPanel)
        content.addView(space(26))

        // QUICK SETTINGS
        content.addView(heading("03 / QUICK ACCESS"))
        content.addView(space(12))

        val quick = cyberCard()
        quick.addView(
            text("AirPods Companion", 19, primaryText, true)
        )
        quick.addView(space(8))
        quick.addView(
            text(
                "Bluetooth monitoring, device information and future audio features.",
                13,
                muted
            )
        )

        content.addView(quick)
        content.addView(space(26))

        // SYSTEM DIAGNOSTICS
        content.addView(heading("04 / SYSTEM TELEMETRY"))
        content.addView(space(12))

        val diagnostics = cyberCard()

        diagnostics.addView(
            text("Bluetooth monitor", 14, primaryText, true)
        )
        diagnostics.addView(space(8))
        diagnostics.addView(
            text("Connection monitoring enabled", 12, muted)
        )
        diagnostics.addView(space(16))
        diagnostics.addView(
            text("Battery telemetry", 14, primaryText, true)
        )
        diagnostics.addView(space(8))
        diagnostics.addView(
            text("Battery decoding is still in development", 12, muted)
        )

        content.addView(diagnostics)
        content.addView(space(28))

        content.addView(
            text("PodsOS  •  CYBER EDITION", 11, muted, true).apply {
                gravity = Gravity.CENTER
            }
        )

        scroll.addView(content)
        pageContainer.addView(scroll)

        val audioScroll = buildAudioScreen()
        audioScroll.visibility = View.GONE
        pageContainer.addView(audioScroll)

        // FIXED BOTTOM NAVIGATION
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(6, 14, 6, 14)
            background = rounded(panel, 22).apply {
                setStroke(1, borderColor)
            }
        }

        val tabs = listOf(
            "HOME" to "⌂",
            "CONTROL" to "◈",
            "AUDIO" to "♫",
            "DIAG" to "⌁",
            "SETTINGS" to "⚙"
        )

        tabs.forEachIndexed { index, tab ->
            val tabButton = TextView(this).apply {
                text = "${tab.second}\n${tab.first}"
                textSize = 11f
                gravity = Gravity.CENTER
                setPadding(2, 12, 2, 12)
                setLineSpacing(4f, 1f)
                setTextColor(if (index == 0) glow else muted)
                setOnClickListener {
                    when (index) {
                        0 -> {
                            scroll.visibility = View.VISIBLE
                            audioScroll.visibility = View.GONE
                        }
                        2 -> {
                            scroll.visibility = View.GONE
                            audioScroll.visibility = View.VISIBLE
                        }
                        else -> {
                            Toast.makeText(
                                this@MainActivity,
                                "${tab.first} section is coming soon",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }

            navigation.addView(
                tabButton,
                LinearLayout.LayoutParams(0, 64, 1f)
            )
        }

        root.addView(
            pageContainer,
            LinearLayout.LayoutParams(-1, 0, 1f)
        )

        root.addView(
            navigation,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(12, 6, 12, 14)
            }
        )

        setContentView(root)
    }

    override fun onStart() {
        super.onStart()

        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(
                    bluetoothReceiver,
                    filter,
                    Context.RECEIVER_EXPORTED
                )
            } else {
                registerReceiver(bluetoothReceiver, filter)
            }

            receiverRegistered = true

        } catch (e: Exception) {
            statusText.text = "Could not monitor Bluetooth"
        }

        checkBluetoothPermission()
    }

    override fun onStop() {
        super.onStop()
        if (::batteryScanner.isInitialized) batteryScanner.stop()

        if (receiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
            receiverRegistered = false
        }

        val adapter = BluetoothAdapter.getDefaultAdapter()

        if (adapter != null && a2dpProxy != null) {
            try {
                adapter.closeProfileProxy(
                    BluetoothProfile.A2DP,
                    a2dpProxy
                )
            } catch (_: Exception) {
            }
        }

        a2dpProxy = null
        checkingProfile = false
    }

    private fun checkBluetoothPermission() {

        if (Build.VERSION.SDK_INT >= 31) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )

            val missing = permissions.filter {
                checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }

            if (missing.isNotEmpty()) {
                requestPermissions(
                    missing.toTypedArray(),
                    1001
                )
                return
            }
        }

        detectAirPods()
        if (::batteryScanner.isInitialized) batteryScanner.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )

        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() &&
                grantResults.all {
                    it == PackageManager.PERMISSION_GRANTED
                }
            ) {
                detectAirPods()
                if (::batteryScanner.isInitialized) batteryScanner.start()
            } else {
                statusText.text = "Bluetooth permissions required"
            }
        }
    }

    private fun detectAirPods() {

        try {
            val adapter = BluetoothAdapter.getDefaultAdapter()

            if (adapter == null) {
                statusText.text = "Bluetooth not supported"
                return
            }

            if (!adapter.isEnabled) {
                statusText.text = "Bluetooth is turned off"
                return
            }

            if (checkingProfile) return

            checkingProfile = true
            statusText.text = "Checking AirPods connection..."

            val listener = object : BluetoothProfile.ServiceListener {

                override fun onServiceConnected(
                    profile: Int,
                    proxy: BluetoothProfile
                ) {
                    checkingProfile = false

                    if (profile != BluetoothProfile.A2DP) return

                    a2dpProxy = proxy

                    try {
                        val connected = proxy.connectedDevices

                        val airPods = connected.firstOrNull {
                            (it.name ?: "").contains(
                                "AirPods",
                                ignoreCase = true
                            )
                        }

                        if (airPods != null) {
                            statusText.text =
                                "Connected: ${airPods.name}"
                        } else {

                            val paired = adapter.bondedDevices.any {
                                (it.name ?: "").contains(
                                    "AirPods",
                                    ignoreCase = true
                                )
                            }

                            statusText.text = if (paired) {
                                "Paired, not connected"
                            } else {
                                "No AirPods connection detected"
                            }
                        }

                    } catch (e: SecurityException) {
                        statusText.text =
                            "Bluetooth permission required"
                    }
                }

                override fun onServiceDisconnected(profile: Int) {
                    checkingProfile = false
                    a2dpProxy = null

                    // Profile service disconnect does not mean
                    // the AirPods themselves disconnected.
                }
            }

            val started = adapter.getProfileProxy(
                this,
                listener,
                BluetoothProfile.A2DP
            )

            if (!started) {
                checkingProfile = false
                statusText.text = "Unable to check Bluetooth"
            }

        } catch (e: SecurityException) {
            checkingProfile = false
            statusText.text = "Bluetooth permission required"
        }
    }

    private fun getBluetoothDevice(
        intent: Intent
    ): BluetoothDevice? {

        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE,
                BluetoothDevice::class.java
            )
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE
            )
        }
    }

    private fun batteryItem(
        name: String,
        value: String
    ): LinearLayout {

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(6, 14, 6, 14)
            background = rounded(
                if (isDark) Color.rgb(41, 24, 60)
                else Color.rgb(241, 231, 255), 16
            ).apply {
                setStroke(1, borderColor)
            }
        }

        item.addView(text(name, 10, glow, true))

        val valueText = text(value, 22, primaryText, true)
        batteryValueViews[name] = valueText
        item.addView(valueText)

        return item
    }

    private fun updateBatteryDisplay(data: AirPodsBatteryData) {
        data.left?.let {
            batteryValueViews["LEFT"]?.text = "$it%"
        }

        data.right?.let {
            batteryValueViews["RIGHT"]?.text = "$it%"
        }

        data.case?.let {
            batteryValueViews["CASE"]?.text = "$it%"
        }
    }


    private fun buildAudioScreen(): ScrollView {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 30)
            setBackgroundColor(bg)
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
        }

        // AUDIO HEADER
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        header.addView(text("PodsOS  /  AUDIO SYSTEM", 11, glow, true))
        header.addView(space(10))
        header.addView(text("AUDIO CONTROL", 29, primaryText, true))
        header.addView(text("Advanced sound configuration", 14, muted))
        page.addView(header)
        page.addView(space(24))

        // CONNECTED DEVICE
        val device = card()
        device.addView(text("CONNECTED DEVICE", 11, glow, true))
        device.addView(space(14))

        val deviceRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        deviceRow.addView(text("◉", 34, glow, true))
        val deviceInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14, 0, 0, 0)
        }
        deviceInfo.addView(text("AirPods 4 ANC", 20, primaryText, true))
        deviceInfo.addView(text("Bluetooth audio device", 12, muted))
        deviceRow.addView(deviceInfo, LinearLayout.LayoutParams(0, -2, 1f))
        deviceRow.addView(text("CONNECTED", 10, glow, true))
        device.addView(deviceRow)

        page.addView(device, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = 22
        })

        // SOUND PRESETS
        val presetHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        presetHeader.addView(
            text("SOUND PRESETS", 13, primaryText, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        presetHeader.addView(text("SELECT A PROFILE", 9, muted, true))
        page.addView(presetHeader)
        page.addView(space(12))

        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        var selectedPreset = "BALANCED"
        val presetNames = listOf("BALANCED", "DEEP BASS", "VOCALS", "GAMING")

        presetNames.forEach { name ->
            val tile = TextView(this).apply {
                text = name
                textSize = 11f
                gravity = Gravity.CENTER
                typeface = Typeface.DEFAULT_BOLD
                setPadding(16, 19, 16, 19)
                setTextColor(if (name == selectedPreset) Color.WHITE else muted)
                background = rounded(
                    if (name == selectedPreset) accent else panel, 18
                ).apply {
                    setStroke(1, borderColor)
                }
                setOnClickListener {
                    selectedPreset = name
                    for (i in 0 until presetRow.childCount) {
                        val item = presetRow.getChildAt(i) as TextView
                        val active = item.text == selectedPreset
                        item.setTextColor(if (active) Color.WHITE else muted)
                        item.background = rounded(
                            if (active) accent else panel, 18
                        ).apply {
                            setStroke(1, borderColor)
                        }
                    }
                }
            }

            presetRow.addView(tile, LinearLayout.LayoutParams(132, -2).apply {
                rightMargin = 10
            })
        }

        val presetScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(presetRow)
        }
        page.addView(presetScroll)
        page.addView(space(24))

        // CUSTOM EQUALIZER
        val equalizer = card()
        val eqHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        eqHeader.addView(
            text("CUSTOM EQUALIZER", 13, primaryText, true),
            LinearLayout.LayoutParams(0, -2, 1f)
        )
        eqHeader.addView(text("CUSTOM", 10, glow, true))
        equalizer.addView(eqHeader)
        equalizer.addView(space(6))
        equalizer.addView(text("10-band frequency control", 12, muted))
        equalizer.addView(space(18))

        val frequencies = listOf(
            "31 Hz", "62 Hz", "125 Hz", "250 Hz", "500 Hz",
            "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
        )

        frequencies.forEach { frequency ->
            val band = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            band.addView(
                text(frequency, 11, muted, true),
                LinearLayout.LayoutParams(58, -2)
            )

            val slider = SeekBar(this).apply {
                max = 24
                progress = 12
                progressTintList =
                    android.content.res.ColorStateList.valueOf(accent)
                thumbTintList =
                    android.content.res.ColorStateList.valueOf(glow)
            }

            band.addView(slider, LinearLayout.LayoutParams(0, 42, 1f))
            band.addView(
                text("0 dB", 10, muted),
                LinearLayout.LayoutParams(38, -2)
            )
            equalizer.addView(band)
        }

        equalizer.addView(space(12))
        equalizer.addView(text("−12 dB                         0 dB                         +12 dB", 9, muted))
        equalizer.addView(space(10))

        val reset = text("RESET EQUALIZER", 11, glow, true).apply {
            gravity = Gravity.END
            setPadding(0, 10, 0, 4)
            setOnClickListener {
                for (i in 0 until equalizer.childCount) {
                    val row = equalizer.getChildAt(i)
                    if (row is LinearLayout && row.childCount >= 2) {
                        val slider = row.getChildAt(1)
                        if (slider is SeekBar) slider.progress = 12
                    }
                }
            }
        }
        equalizer.addView(reset)

        page.addView(equalizer, LinearLayout.LayoutParams(-1, -2).apply {
            bottomMargin = 22
        })

        // AUDIO ENHANCEMENTS
        val enhancements = card()
        enhancements.addView(text("AUDIO ENHANCEMENTS", 13, primaryText, true))
        enhancements.addView(space(16))

        fun enhancementSwitch(title: String, subtitle: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val labels = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
            }
            labels.addView(text(title, 14, primaryText, true))
            labels.addView(text(subtitle, 11, muted))

            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Switch(this).apply {
                isChecked = false
                setOnCheckedChangeListener { _, _ ->
                    Toast.makeText(
                        this@MainActivity,
                        "Audio processing is not connected yet",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
            enhancements.addView(row)
            enhancements.addView(space(16))
        }

        enhancementSwitch("Spatial audio", "Immersive sound experience")
        enhancementSwitch("Volume monitor", "Audio level monitoring")

        enhancements.addView(text("LEFT / RIGHT BALANCE", 11, muted, true))
        enhancements.addView(space(6))

        val balance = SeekBar(this).apply {
            max = 100
            progress = 50
            progressTintList =
                android.content.res.ColorStateList.valueOf(accent)
            thumbTintList =
                android.content.res.ColorStateList.valueOf(glow)
        }
        enhancements.addView(balance)
        enhancements.addView(space(4))
        enhancements.addView(text("LEFT                         CENTER                         RIGHT", 9, muted))

        page.addView(enhancements)

        page.addView(space(26))
        page.addView(
            text("PodsOS  •  AUDIO CONTROL CENTRE", 10, muted, true).apply {
                gravity = Gravity.CENTER
            }
        )

        scroll.addView(page)
        return scroll
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            background = rounded(panel, 26).apply {
                setStroke(1, borderColor)
            }
            elevation = 3f
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

            if (bold) {
                setTypeface(null, Typeface.BOLD)
            }
        }
    }

    private fun space(height: Int): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, height)
        }
    }

    private fun rounded(
        color: Int,
        radius: Int
    ): GradientDrawable {

        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }
}

