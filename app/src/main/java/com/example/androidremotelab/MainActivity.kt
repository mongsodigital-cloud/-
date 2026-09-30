package com.example.androidremotelab

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var apiBase: EditText
    private lateinit var deviceToken: EditText
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val pollMs = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiBase = findViewById(R.id.apiBase)
        deviceToken = findViewById(R.id.deviceToken)
        status = findViewById(R.id.status)

        apiBase.setText(getPreferences(MODE_PRIVATE).getString("apiBase", "https://android-remote-lab.netlify.app"))
        deviceToken.setText(getPreferences(MODE_PRIVATE).getString("deviceToken", ""))

        findViewById<Button>(R.id.saveConfig).setOnClickListener {
            saveConfig()
        }

        findViewById<Button>(R.id.checkNow).setOnClickListener {
            saveConfig()
            pollOnce()
        }

        findViewById<Button>(R.id.testOpen).setOnClickListener {
            openWhatsApp()
        }
    }

    private fun saveConfig() {
        getPreferences(MODE_PRIVATE).edit()
            .putString("apiBase", apiBase.text.toString().trim().trimEnd('/'))
            .putString("deviceToken", deviceToken.text.toString().trim())
            .apply()
        status.text = "Konfigurasi tersimpan."
    }

    private fun pollOnce() {
        val base = apiBase.text.toString().trim().trimEnd('/')
        val token = deviceToken.text.toString().trim()

        if (base.isBlank() || token.isBlank()) {
            status.text = "API URL dan device token wajib diisi."
            return
        }

        status.text = "Memeriksa command..."

        thread {
            try {
                val c = URL("${base}/api/command").openConnection() as HttpURLConnection
                c.requestMethod = "GET"
                c.setRequestProperty("x-device-token", token)
                c.connectTimeout = 10000
                c.readTimeout = 10000

                val code = c.responseCode
                val body = (if (code in 200..299) c.inputStream else c.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                c.disconnect()

                handler.post {
                    status.text = "HTTP $code
$body"
                    if (code in 200..299 && body.contains(""OPEN_WHATSAPP"")) {
                        openWhatsApp()
                        reportResult(base, token, "OPEN_WHATSAPP", "ok")
                    }
                }
            } catch (e: Exception) {
                handler.post { status.text = "Error: ${e.message ?: "unknown"}" }
            }
        }
    }

    private fun openWhatsApp() {
        val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (launch != null) {
            startActivity(launch)
            status.text = "WhatsApp dibuka."
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.whatsapp.com/")))
            status.text = "WhatsApp tidak ditemukan; membuka situs."
        }
    }

    private fun reportResult(base: String, token: String, command: String, result: String) {
        thread {
            try {
                val c = URL("${base}/api/result").openConnection() as HttpURLConnection
                c.requestMethod = "POST"
                c.doOutput = true
                c.connectTimeout = 10000
                c.readTimeout = 10000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-device-token", token)
                c.outputStream.use {
                    it.write("""{"command":"$command","result":"$result"}""".toByteArray())
                }
                c.responseCode
                c.inputStream.close()
                c.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun startPolling() {
        val token = deviceToken.text.toString().trim()
        if (token.isBlank()) {
            status.text = "Masukkan device token lalu Simpan konfigurasi."
            return
        }
        pollOnce()
        handler.postDelayed({ startPolling() }, pollMs)
    }
}
