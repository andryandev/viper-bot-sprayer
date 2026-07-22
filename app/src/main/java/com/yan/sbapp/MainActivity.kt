package com.yan.sbapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.Handler
import android.os.Looper

/**
 * MainActivity adalah pusat kontrol utama untuk aplikasi Viper Bot Sprayer.
 * Kelas ini menangani antarmuka pengguna (UI), auto-discovery perangkat robot via UDP,
 * serta komunikasi dua arah secara real-time via WebSockets (Raw Byte Protocol).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvSpeed: TextView
    private lateinit var tvPumpStatus: TextView
    private lateinit var tvDuration: TextView

    private lateinit var btnStartSprayer: MaterialButton
    private lateinit var btnMaju: MaterialButton
    private lateinit var btnMundur: MaterialButton
    private lateinit var btnKiri: MaterialButton
    private lateinit var btnKanan: MaterialButton

    private var webSocket: WebSocket? = null
    private var isSpraying = false
    private var driveState = 0
    private var steerState = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        
        var isReady = false
        Handler(Looper.getMainLooper()).postDelayed({
            isReady = true
        }, 2500) // 2.5 seconds delay
        
        splashScreen.setKeepOnScreenCondition { !isReady }
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val mainView = findViewById<android.view.View>(R.id.main)
        if (mainView != null) {
            ViewCompat.setOnApplyWindowInsetsListener(mainView) { v, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
                insets
            }
        }

        initViews()
        setupListeners()
        discoverRobot()
    }

    private fun initViews() {
        tvSpeed = findViewById(R.id.tvSpeed)
        tvPumpStatus = findViewById(R.id.tvPumpStatus)
        tvDuration = findViewById(R.id.tvDuration)

        btnStartSprayer = findViewById(R.id.btnStartSprayer)
        btnMaju = findViewById(R.id.btnMaju)
        btnMundur = findViewById(R.id.btnMundur)
        btnKiri = findViewById(R.id.btnKiri)
        btnKanan = findViewById(R.id.btnKanan)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        // Sprayer Button Toggle
        btnStartSprayer.setOnClickListener {
            isSpraying = !isSpraying
            sendRawCommand()
            updateSprayerButtonUI()
        }

        // Movement Buttons (Hold to move, release to stop)
        val movementListener = android.view.View.OnTouchListener { v, event ->
            val action = event.action
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                val isDown = action == MotionEvent.ACTION_DOWN
                
                when (v.id) {
                    R.id.btnMaju -> driveState = if (isDown) 1 else 0
                    R.id.btnMundur -> driveState = if (isDown) -1 else 0
                    R.id.btnKiri -> steerState = if (isDown) -1 else 0
                    R.id.btnKanan -> steerState = if (isDown) 1 else 0
                }
                sendRawCommand()
            }
            false
        }

        btnMaju.setOnTouchListener(movementListener)
        btnMundur.setOnTouchListener(movementListener)
        btnKiri.setOnTouchListener(movementListener)
        btnKanan.setOnTouchListener(movementListener)
    }

    private fun updateSprayerButtonUI() {
        btnStartSprayer.text = if (isSpraying) "STOP SPRAYING" else "START SPRAYING"
        if (isSpraying) {
            btnStartSprayer.setIconResource(R.drawable.ic_stop)
            btnStartSprayer.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#EF4444"))
        } else {
            btnStartSprayer.setIconResource(R.drawable.ic_power)
            btnStartSprayer.backgroundTintList = android.content.res.ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(this, R.color.brand_blue))
        }
    }

    private fun sendRawCommand() {
        val cmd = byteArrayOf(driveState.toByte(), steerState.toByte(), if (isSpraying) 1.toByte() else 0.toByte())
        webSocket?.send(cmd.toByteString())
    }

    private fun discoverRobot() {
        runOnUiThread { tvSpeed.text = "..." }
        Thread {
            try {
                val socket = DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 1000 
                
                val sendData = "FIND_SPRAYER_BOT".toByteArray()
                var robotIp: String? = null
                
                val broadcastAddresses = mutableListOf<InetAddress>()
                broadcastAddresses.add(InetAddress.getByName("255.255.255.255"))
                
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    for (interfaceAddress in networkInterface.interfaceAddresses) {
                        interfaceAddress.broadcast?.let { broadcastAddresses.add(it) }
                    }
                }
                
                for (i in 1..4) {
                    if (robotIp != null) break
                    
                    for (address in broadcastAddresses) {
                        try {
                            val sendPacket = DatagramPacket(sendData, sendData.size, address, 8888)
                            socket.send(sendPacket)
                        } catch (e: Exception) {}
                    }
                    
                    val receiveData = ByteArray(1024)
                    val receivePacket = DatagramPacket(receiveData, receiveData.size)
                    
                    try {
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        if (response.startsWith("I_AM_SPRAYER_BOT:")) {
                            robotIp = response.split(":")[1]
                            Log.d("UDP", "Menemukan robot di IP: $robotIp")
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // Timeout
                    }
                }
                
                socket.close()
                
                if (robotIp != null) {
                    runOnUiThread { connectWebSocket(robotIp) }
                } else {
                    Log.d("UDP", "Robot tidak ditemukan, mencoba IP default AP")
                    runOnUiThread { connectWebSocket("192.168.4.1") }
                }
                
            } catch (e: Exception) {
                Log.e("UDP", "Error pencarian UDP", e)
                runOnUiThread { connectWebSocket("192.168.4.1") }
            }
        }.start()
    }

    private fun connectWebSocket(ip: String) {
        val client = OkHttpClient.Builder()
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder().url("ws://$ip:81").build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected")
                runOnUiThread { tvSpeed.text = "0.0 m/s" }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                try {
                    val buffer = ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN)
                    if (buffer.capacity() >= 16) {
                        val header = buffer.get().toUByte().toInt()
                        if (header == 0xAA) {
                            val distance = buffer.short
                            val sprayState = buffer.get().toInt() == 1
                            val speed = buffer.float
                            val volume = buffer.float
                            val durationSecs = buffer.int
                            
                            val h = durationSecs / 3600
                            val m = (durationSecs % 3600) / 60
                            val s = durationSecs % 60
                            val durationStr = String.format("%02d:%02d:%02d", h, m, s)
                            
                            runOnUiThread {
                                tvSpeed.text = String.format("%.1f m/s", speed).replace(',', '.')
                                tvPumpStatus.text = if (sprayState) "ON" else "OFF"
                                tvDuration.text = durationStr
                                
                                if (sprayState != isSpraying) {
                                    isSpraying = sprayState
                                    updateSprayerButtonUI()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "Binary parse error", e)
                }
            }

            // Fallback for debugging if ESP32 sends text
            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d("WebSocket", "Received text instead of binary. Please flash ESP32 with the new FreeRTOS code.")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error: ${t.message}")
                runOnUiThread {
                    tvSpeed.text = "Error"
                }
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "App closed")
    }
}
