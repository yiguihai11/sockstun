/*
 ============================================================================
 Name        : MainActivity.kt
 Author      : hev <r@hev.cc>
 Copyright   : Copyright (c) 2023 xyz
 Description : Main Activity
 ============================================================================
 */

package hev.sockstun

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import hev.sockstun.adapters.ViewPagerAdapter
import hev.sockstun.fragments.ServerFragment
import hev.sockstun.fragments.DnsFragment
import hev.sockstun.fragments.OptionsFragment

class MainActivity : FragmentActivity(), View.OnClickListener {
    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 1001
        
        // 应用过滤模式常量
        const val APP_FILTER_MODE_OFF = 0
        const val APP_FILTER_MODE_BYPASS = 1
        const val APP_FILTER_MODE_ONLY = 2
    }

    private lateinit var prefs: Preferences
    private lateinit var buttonSave: MaterialButton
    private lateinit var buttonControl: MaterialButton
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    
    private lateinit var serverFragment: ServerFragment
    private lateinit var dnsFragment: DnsFragment
    private lateinit var optionsFragment: OptionsFragment
    
    // 用于处理VPN权限请求结果的ActivityResultLauncher
    private lateinit var vpnActivityResultLauncher: ActivityResultLauncher<Intent>

    // 用于接收VPN服务状态广播的Receiver
    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateUI()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        prefs = Preferences(this)
        
        // 注册VPN权限请求结果处理器
        vpnActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                // VPN权限已获取，启动服务
                startVpnService()
            } else {
                // 用户拒绝VPN权限
                Toast.makeText(this, "VPN权限被拒绝", Toast.LENGTH_SHORT).show()
            }
        }
        
        initViews()
        setupViewPager()
        setupListeners()
        
        // 使用Context.RECEIVER_NOT_EXPORTED标志，因为这是应用内部使用的广播
        registerReceiver(serviceReceiver, IntentFilter(TProxyService.ACTION_STATUS_CHANGED), Context.RECEIVER_NOT_EXPORTED)

        // Android 13+需要请求通知权限
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_CODE
            )
        }

        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(serviceReceiver)
    }

    private fun initViews() {
        tabLayout = findViewById(R.id.tab_layout)
        viewPager = findViewById(R.id.view_pager)
        buttonSave = findViewById(R.id.save)
        buttonControl = findViewById(R.id.control)
    }
    
    private fun setupViewPager() {
        // 初始化Fragment
        serverFragment = ServerFragment()
        dnsFragment = DnsFragment()
        optionsFragment = OptionsFragment()
        
        // 设置ViewPager适配器
        val fragments = listOf(serverFragment, dnsFragment, optionsFragment)
        val adapter = ViewPagerAdapter(this, fragments)
        viewPager.adapter = adapter
        
        // 连接TabLayout和ViewPager
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.tab_server)
                1 -> getString(R.string.tab_dns)
                2 -> getString(R.string.tab_options)
                else -> ""
            }
        }.attach()
    }

    private fun setupListeners() {
        buttonSave.setOnClickListener(this)
        buttonControl.setOnClickListener(this)
    }

    // 显示VPN冲突确认对话框
    private fun showVpnConflictDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.vpn_conflict_title)
            .setMessage(R.string.vpn_conflict_message)
            .setPositiveButton(R.string.vpn_conflict_continue) { _, _ ->
                startVpn()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    // 检查是否有其他VPN活动
    private fun isOtherVpnActive(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                return true
            }
        }
        return false
    }

    private fun savePrefs() {
        // 保存所有Fragment的设置
        serverFragment.savePreferences()
        dnsFragment.savePreferences()
        optionsFragment.savePreferences()
    }

    private fun startVpn() {
        // 保存用户设置
        savePrefs()
        
        // 准备VPN请求
        val intent = VpnService.prepare(this)
        if (intent != null) {
            // 需要用户确认VPN权限
            vpnActivityResultLauncher.launch(intent)
        } else {
            // 已经有权限，启动服务
            startVpnService()
        }
    }
    
    private fun startVpnService() {
        val serviceIntent = Intent(this, TProxyService::class.java)
        serviceIntent.action = TProxyService.ACTION_CONNECT
        startForegroundService(serviceIntent)
        prefs.isEnabled = true
        updateUI()
    }

    private fun updateUI() {
        // 更新控制按钮文本
        buttonControl.text = getString(
            if (prefs.isEnabled) R.string.control_disable else R.string.control_enable
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限被授予
                Toast.makeText(this, "通知权限已授予", Toast.LENGTH_SHORT).show()
            } else {
                // 权限被拒绝
                Toast.makeText(this, "需要通知权限来显示流量统计", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    override fun onClick(view: View) {
        when (view) {
            buttonSave -> {
                savePrefs()
                Toast.makeText(applicationContext, "Saved", Toast.LENGTH_SHORT).show()
            }
            buttonControl -> {
                if (prefs.isEnabled) {
                    // 目前已经启用VPN，停止服务
                    val intent = Intent(this, TProxyService::class.java)
                    intent.action = TProxyService.ACTION_DISCONNECT
                    startService(intent)
                    prefs.isEnabled = false
                    updateUI()
                } else {
                    // 检查是否有其他VPN正在运行
                    if (isOtherVpnActive()) {
                        showVpnConflictDialog()
                    } else {
                        startVpn()
                    }
                }
            }
        }
    }
}