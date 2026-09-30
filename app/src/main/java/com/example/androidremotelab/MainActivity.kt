package com.example.androidremotelab

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {
    private lateinit var apiBase: EditText
    private lateinit var deviceToken: EditText
    private lateinit var status: TextView
    private lateinit var galleryStatus: TextView
    private val galleryRequestCode = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        apiBase = findViewById(R.id.apiBase)
        deviceToken = findViewById(R.id.deviceToken)
        status = findViewById(R.id.status)
        galleryStatus = findViewById(R.id.galleryStatus)

        apiBase.setText(getPreferences(MODE_PRIVATE).getString(
            "apiBase", "https://android-remote-lab.netlify.app"
        ))
        deviceToken.setText(getPreferences(MODE_PRIVATE).getString("deviceToken", ""))

        findViewById<Button>(R.id.saveConfig).setOnClickListener { saveConfig() }
        findViewById<Button>(R.id.checkNow).setOnClickListener {
            saveConfig()
            pollOnce()
        }
        findViewById<Button>(R.id.testOpen).setOnClickListener { openWhatsApp() }
        findViewById<Button>(R.id.requestGallery).setOnClickListener { requestGalleryAccess() }

        updateGalleryStatus()
    }

    private fun saveConfig() {
        getPreferences(MODE_PRIVATE).edit()
            .putString("apiBase", apiBase.text.toString().trim().trimEnd('/'))
            .putString("deviceToken", deviceToken.text.toString().trim())
            .apply()
        status.text = "Konfigurasi tersimpan."
    }

    private fun requestGalleryAccess() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO
                ),
                galleryRequestCode
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                galleryRequestCode
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == galleryRequestCode) updateGalleryStatus()
    }

    private fun updateGalleryStatus() {
        if (!hasGalleryAccess()) {
            galleryStatus.text = "Galeri: akses belum diberikan."
            return
        }

        thread {
            val summary = GalleryHelper.summarize(contentResolver)
            runOnUiThread {
                galleryStatus.text = "Galeri read-only: ${summary.images} foto, ${summary.videos} video."
            }
        }
    }

    private fun hasGalleryAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
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

                runOnUiThread {
                    status.text = "HTTP $code\n$body"
                    if (code in 200..299 && body.contains("OPEN_WHATSAPP")) {
                        openWhatsApp()
                        reportResult(base, token, "OPEN_WHATSAPP", "ok")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Error: ${e.message ?: "unknown"}" }
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
            } catch (_: Exception) { }
        }
    }
}