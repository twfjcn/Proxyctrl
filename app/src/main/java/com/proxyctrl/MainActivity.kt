package com.proxyctrl

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button

    private val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tv_status)
        btnStart = findViewById(R.id.btn_start)
        btnStop = findViewById(R.id.btn_stop)

        checkPermissions()

        btnStart.setOnClickListener {
            startVpn()
        }

        btnStop.setOnClickListener {
            stopVpn()
        }

        updateUI()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun checkPermissions() {
        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                1001
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val denied = grantResults.filterIndexed { index, result ->
                result != PackageManager.PERMISSION_GRANTED
            }
            if (denied.isNotEmpty()) {
                Toast.makeText(this, "需要权限才能运行", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startVpn() {
        try {
            val intent = VpnForwardService.prepare(this)
            if (intent != null) {
                startIntentSenderForResult(
                    intent.intentSender,
                    1000,
                    null,
                    0,
                    0,
                    0,
                    null
                )
            } else {
                VpnForwardService.start(this)
                Toast.makeText(this, "VPN 服务已启动", Toast.LENGTH_SHORT).show()
                updateUI()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1000 && resultCode == RESULT_OK) {
            VpnForwardService.start(this)
            Toast.makeText(this, "VPN 服务已启动", Toast.LENGTH_SHORT).show()
            updateUI()
        } else if (requestCode == 1000) {
            Toast.makeText(this, "需要 VPN 权限", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopVpn() {
        VpnForwardService.stop(this)
        Toast.makeText(this, "VPN 服务已停止", Toast.LENGTH_SHORT).show()
        updateUI()
    }

    private fun updateUI() {
        val running = VpnForwardService.isRunning
        btnStart.isEnabled = !running
        btnStop.isEnabled = running
        tvStatus.text = if (running) {
            "状态：VPN 转发正在运行"
        } else {
            "状态：VPN 转发已停止"
        }
    }
}
