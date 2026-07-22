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

class MainActivity : AppCompatActivity() {

    private lateinit var tvBattery: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvDuration: TextView
    private lateinit var tvPumpStatus: TextView

    private lateinit var btnStartSprayer: MaterialButton
    private lateinit var btnMaju: MaterialButton
    private lateinit var btnMundur: MaterialButton
    private lateinit var btnKiri: MaterialButton
    private lateinit var btnKanan: MaterialButton

    private var webSocket: WebSocket? = null
    private var isSpraying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        tvBattery = findViewById(R.id.tvBattery)
        tvDistance = findViewById(R.id.tvDistance)
        tvDuration = findViewById(R.id.tvDuration)
        tvPumpStatus = findViewById(R.id.tvPumpStatus)

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
            val json = JSONObject()
            json.put("cmd", "spray")
            json.put("state", isSpraying)
            sendMessage(json.toString())
            
            btnStartSprayer.text = if (isSpraying) "STOP SPRAYING" else "START SPRAYING"
            // Note: Background color change can be added here if desired
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

    private fun sendCommand(cmd: String, value: Int) {
        val json = JSONObject()
        json.put("cmd", cmd)
        json.put("val", value)
        sendMessage(json.toString())
    }

    private fun discoverRobot() {
        runOnUiThread { tvDistance.text = "Mencari..." }
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

    private fun connectWebSocket(ip: String) {
        val client = OkHttpClient.Builder()
            .readTimeout(3, TimeUnit.SECONDS)
            .build()
        
        val request = Request.Builder().url("ws://$ip:81").build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d("WebSocket", "Connected to ESP32")
                runOnUiThread { tvBattery.text = "100 %" /* Example fixed */ }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val distance = if (json.has("distance")) json.getInt("distance") else -1
                    val sprayState = if (json.has("isSpraying")) json.getBoolean("isSpraying") else false
                    val duration = if (json.has("duration")) json.getString("duration") else "00:00:00"
                    // Direction not necessarily needed in UI, but could be added

                    runOnUiThread {
                        if (distance != -1) tvDistance.text = "$distance cm"
                        tvPumpStatus.text = if (sprayState) "ON" else "OFF"
                        tvDuration.text = duration
                        
                        // Sync toggle button if changed from backend
                        if (sprayState != isSpraying) {
                            isSpraying = sprayState
                            btnStartSprayer.text = if (isSpraying) "STOP SPRAYING" else "START SPRAYING"
                        }
                    }
                } catch (e: Exception) {
                    Log.e("WebSocket", "JSON parse error", e)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e("WebSocket", "Error: ${t.message}")
                runOnUiThread {
                    tvDistance.text = "Error"
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
