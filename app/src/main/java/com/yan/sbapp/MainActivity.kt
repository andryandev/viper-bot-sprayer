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
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.os.Handler
import android.os.Looper

/**
 * MainActivity adalah pusat kontrol utama untuk aplikasi Viper Bot Sprayer.
 * Kelas ini menangani antarmuka pengguna (UI), auto-discovery perangkat robot via UDP,
 * serta komunikasi dua arah secara real-time via WebSockets.
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

    /**
     * Menghubungkan variabel-variabel Kotlin dengan elemen UI (View) yang ada di XML.
     */
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

    /**
     * Menginisiasi pendengar (listener) untuk semua interaksi tombol pengguna.
     * Mengimplementasikan logika tekan-tahan (hold-to-move) untuk pergerakan.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupListeners() {
        // Sprayer Button Toggle
        btnStartSprayer.setOnClickListener {
            isSpraying = !isSpraying
            val json = JSONObject()
            json.put("cmd", "spray")
            json.put("state", isSpraying)
            sendMessage(json.toString())
            
            updateSprayerButtonUI()
        }

        // Movement Buttons (Hold to move, release to stop)
        val movementListener = android.view.View.OnTouchListener { v, event ->
            val action = event.action
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                val isDown = action == MotionEvent.ACTION_DOWN
                
                when (v.id) {
                    R.id.btnMaju -> sendCommand("drive", if (isDown) 1 else 0)
                    R.id.btnMundur -> sendCommand("drive", if (isDown) -1 else 0)
                    R.id.btnKiri -> sendCommand("steer", if (isDown) -1 else 0)
                    R.id.btnKanan -> sendCommand("steer", if (isDown) 1 else 0)
                }
            }
            false
        }

        btnMaju.setOnTouchListener(movementListener)
        btnMundur.setOnTouchListener(movementListener)
        btnKiri.setOnTouchListener(movementListener)
        btnKanan.setOnTouchListener(movementListener)
    }

    /**
     * Memperbarui antarmuka pengguna pada tombol Sprayer (Warna dan Ikon)
     * berdasarkan status pompa saat ini.
     */
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

    /**
     * Mengirim perintah pergerakan (Maju, Mundur, Kiri, Kanan, Stop) ke robot.
     * @param cmd Kategori perintah (misal: "drive", "steer")
     * @param value Nilai instruksi (1, -1, atau 0)
     */
    private fun sendCommand(cmd: String, value: Int) {
        val json = JSONObject()
        json.put("cmd", cmd)
        json.put("val", value)
        sendMessage(json.toString())
    }

    /**
     * Melakukan pemindaian jaringan lokal (Auto-Discovery) menggunakan UDP Broadcast.
     * Aplikasi akan mencari ESP32 yang merespons dengan IP address-nya pada port 8888.
     * Setelah IP ditemukan, fungsi ini akan otomatis memanggil `connectWebSocket()`.
     */
    private fun discoverRobot() {
        runOnUiThread { tvSpeed.text = "..." }
        Thread {
            try {
                val socket = java.net.DatagramSocket()
                socket.broadcast = true
                socket.soTimeout = 1000 // 1 detik per kali baca
                
                val sendData = "FIND_SPRAYER_BOT".toByteArray()
                var robotIp: String? = null
                
                // Cari ke semua broadcast address yang ada (Wifi / Hotspot)
                val broadcastAddresses = mutableListOf<java.net.InetAddress>()
                broadcastAddresses.add(java.net.InetAddress.getByName("255.255.255.255"))
                
                val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    if (networkInterface.isLoopback || !networkInterface.isUp) continue
                    for (interfaceAddress in networkInterface.interfaceAddresses) {
                        interfaceAddress.broadcast?.let { broadcastAddresses.add(it) }
                    }
                }
                
                // Coba kirim dan tunggu selama 4 detik (4 kali percobaan)
                for (i in 1..4) {
                    if (robotIp != null) break
                    
                    // Kirim ke semua alamat broadcast
                    for (address in broadcastAddresses) {
                        try {
                            val sendPacket = java.net.DatagramPacket(sendData, sendData.size, address, 8888)
                            socket.send(sendPacket)
                        } catch (e: Exception) {}
                    }
                    
                    val receiveData = ByteArray(1024)
                    val receivePacket = java.net.DatagramPacket(receiveData, receiveData.size)
                    
                    try {
                        socket.receive(receivePacket)
                        val response = String(receivePacket.data, 0, receivePacket.length)
                        if (response.startsWith("I_AM_SPRAYER_BOT:")) {
                            robotIp = response.split(":")[1]
                            Log.d("UDP", "Menemukan robot di IP: $robotIp")
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // Timeout 1 detik, coba kirim lagi
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

    /**
     * Membuka koneksi WebSocket ke alamat IP ESP32 yang telah ditemukan.
     * Fungsi ini akan terus menerima aliran data (telemetri) dari robot
     * dan mengontrol UI Android secara real-time (Jarak, Baterai, Status, dll).
     * @param ip Alamat IP lokal dari robot ESP32
     */
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

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val speed = if (json.has("speed")) json.getString("speed") else "0.0"
                    val sprayState = if (json.has("isSpraying")) json.getBoolean("isSpraying") else false
                    val duration = if (json.has("duration")) json.getString("duration") else "00:00:00"

                    runOnUiThread {
                        tvSpeed.text = "$speed m/s"
                        tvPumpStatus.text = if (sprayState) "ON" else "OFF"
                        tvDuration.text = duration
                        
                        // Sync toggle button if changed from backend
                        if (sprayState != isSpraying) {
                            isSpraying = sprayState
                            updateSprayerButtonUI()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "JSON parse error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error: ${t.message}")
                runOnUiThread {
                    tvSpeed.text = "Error"
                }
            }
        })
    }

    private fun sendMessage(msg: String) {
        webSocket?.send(msg)
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket?.close(1000, "App closed")
    }
}
