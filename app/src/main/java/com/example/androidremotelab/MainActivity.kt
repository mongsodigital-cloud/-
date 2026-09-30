package com.example.androidremotelab

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
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
    private lateinit var prefs: SharedPreferences
    private lateinit var status: TextView
    private lateinit var galleryStatus: TextView
    private val galleryRequestCode = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        apiBase = findViewById(R.id.apiBase)
        deviceToken = findViewById(R.id.deviceToken)
        prefs = getPreferences(MODE_PRIVATE)
        status = findViewById(R.id.status)
        galleryStatus = findViewById(R.id.galleryStatus)
        apiBase.setText(prefs.getString("apiBase", "https://android-remote-lab.netlify.app"))
        deviceToken.setText(prefs.getString("deviceToken", ""))
        deviceToken.visibility = if (prefs.getBoolean("paired", false)) android.view.View.GONE else android.view.View.VISIBLE
        findViewById<Button>(R.id.saveConfig).setOnClickListener { saveConfig() }
        findViewById<Button>(R.id.checkNow).setOnClickListener { saveConfig(); pollOnce() }
        findViewById<Button>(R.id.testOpen).setOnClickListener { openWhatsApp() }
        findViewById<Button>(R.id.requestGallery).setOnClickListener { requestGalleryAccess() }
        updateGalleryStatus()
    }

    private fun saveConfig() {
        val base = apiBase.text.toString().trim().trimEnd('/')
        val token = deviceToken.text.toString().trim()
        if (base.isBlank() || token.isBlank()) { status.text = "Masukkan token sekali untuk pairing perangkat."; return }
        prefs.edit()
            .putString("apiBase", base)
            .putString("deviceToken", token)
            .putBoolean("paired", true)
            .apply()
        deviceToken.visibility = android.view.View.GONE
        status.text = "Perangkat berhasil dipasangkan. Token tersimpan di perangkat."
    }

    private fun requestGalleryAccess() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO), galleryRequestCode)
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), galleryRequestCode)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == galleryRequestCode) updateGalleryStatus()
    }

    private fun updateGalleryStatus() {
        if (!hasGalleryAccess()) { galleryStatus.text = "Galeri: akses belum diberikan."; return }
        thread {
            val summary = GalleryHelper.summarize(contentResolver)
            runOnUiThread { galleryStatus.text = "Galeri read-only: ${summary.images} foto, ${summary.videos} video." }
        }
    }

    private fun hasGalleryAccess(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val images = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
            val videos = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED
            return images || videos
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun pollOnce() {
        val base = apiBase.text.toString().trim().trimEnd('/')
        val token = prefs.getString("deviceToken", "")?.trim().orEmpty()
        if (base.isBlank() || token.isBlank()) { status.text = "Perangkat belum dipasangkan."; return }
        status.text = "Memeriksa command..."
        thread {
            try {
                val c = URL("${base}/api/command").openConnection() as HttpURLConnection
                c.requestMethod = "GET"
                c.setRequestProperty("x-device-token", token)
                c.connectTimeout = 10000
                c.readTimeout = 10000
                val code = c.responseCode
                val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                c.disconnect()
                runOnUiThread {
                    status.text = "HTTP " + code + "\n" + body
                    if (code in 200..299 && body.contains("OPEN_WHATSAPP")) { openWhatsApp(); reportResult(base, token, "OPEN_WHATSAPP", "ok") }
                }
            } catch (e: Exception) { runOnUiThread { status.text = "Error: ${e.message ?: "unknown"}" } }
        }
    }

    private fun openWhatsApp() {
        val launch = packageManager.getLaunchIntentForPackage("com.whatsapp")
        if (launch != null) { startActivity(launch); status.text = "WhatsApp dibuka." }
        else { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.whatsapp.com/"))); status.text = "WhatsApp tidak ditemukan; membuka situs." }
    }

    private fun reportResult(base: String, token: String, command: String, result: String) {
        thread {
            try {
                val c = URL("${base}/api/result").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true
                c.connectTimeout = 10000; c.readTimeout = 10000
                c.setRequestProperty("Content-Type", "application/json")
                c.setRequestProperty("x-device-token", token)
                c.outputStream.use { it.write("""{"command":"$command","result":"$result"}""".toByteArray()) }
                c.responseCode; c.inputStream.close(); c.disconnect()
            } catch (_: Exception) {}
        }
    }
}