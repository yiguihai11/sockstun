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
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import hev.sockstun.fragments.AboutFragment
import hev.sockstun.fragments.HomeFragment
import hev.sockstun.fragments.HevSocks5TunnelFragment
import hev.sockstun.fragments.LogFragment
import hev.sockstun.fragments.ShadowsocksFragment

class MainActivity : AppCompatActivity(), NavigationView.OnNavigationItemSelectedListener {
    companion object {
        private const val NOTIFICATION_PERMISSION_CODE = 1001
        private const val TAG = "MainActivity"
        
        // 应用过滤模式常量
        const val APP_FILTER_MODE_OFF = 0
        const val APP_FILTER_MODE_BYPASS = 1
        const val APP_FILTER_MODE_ONLY = 2
    }

    private lateinit var prefs: Preferences
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var toolbar: Toolbar
    
    // 当前活动的Fragment
    private var activeFragment: Fragment? = null
    private var homeFragment: HomeFragment? = null
    private var logFragment: LogFragment? = null
    
    // 用于处理VPN权限请求结果的ActivityResultLauncher
    private lateinit var vpnActivityResultLauncher: ActivityResultLauncher<Intent>
    
    // 日志广播接收器
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TProxyService.ACTION_LOG_EVENT) {
                val tag = intent.getStringExtra(TProxyService.EXTRA_LOG_TAG) ?: "Unknown"
                val message = intent.getStringExtra(TProxyService.EXTRA_LOG_MESSAGE) ?: "No message"
                appendLog(tag, message)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main)

        prefs = Preferences(this)
        
        // 初始化视图
        initViews()
        
        // 设置导航抽屉
        setupDrawer()
        
        // 默认显示Home页面
        if (savedInstanceState == null) {
            // 导航到Home页面
            navigateToFragment(R.id.nav_home)
            navigationView.setCheckedItem(R.id.nav_home)
        }
        
        // 注册VPN权限请求结果处理器
        vpnActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                startVpnService()
            } else {
                Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
        
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
        
        // 检查是否需要显示解决方案
        checkForSolutionRequest(intent)

        // 设置返回键处理
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    finish()
                }
            }
        })
        
        // 注册日志接收器
        val intentFilter = IntentFilter(TProxyService.ACTION_LOG_EVENT)
        registerReceiver(logReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
    }
    
    override fun onDestroy() {
        // 注销日志接收器
        unregisterReceiver(logReceiver)
        super.onDestroy()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        checkForSolutionRequest(intent)
    }
    
    private fun checkForSolutionRequest(intent: Intent?) {
        if (intent?.action == "hev.sockstun.SHOW_SOLUTION") {
            // 从SharedPreferences读取解决方案信息
            val solutionText = getSharedPreferences("solution_info", Context.MODE_PRIVATE)
                .getString("solution_text", getString(R.string.solution_not_found)) ?: getString(R.string.solution_not_found)
            
            // 显示对话框
            AlertDialog.Builder(this)
                .setTitle(R.string.solution_title)
                .setMessage(solutionText)
                .setPositiveButton(R.string.ok, null)
                .setNegativeButton(R.string.solution_copy) { _, _ ->
                    // 复制到剪贴板
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText(getString(R.string.solution_title), solutionText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, R.string.solution_copied, Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // 设置默认标题（进入应用时显示）
        supportActionBar?.title = getString(R.string.nav_home)
        
        drawerLayout = findViewById(R.id.drawer_layout)
        navigationView = findViewById(R.id.nav_view)
    }
    
    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()
        
        navigationView.setNavigationItemSelectedListener(this)
    }
    
    // 切换到指定Fragment
    private fun navigateToFragment(fragmentId: Int) {
        val fragment = when (fragmentId) {
            R.id.nav_home -> {
                toolbar.title = getString(R.string.nav_home)
                HomeFragment().also { homeFragment = it }
            }
            R.id.nav_hev_socks5_tunnel -> {
                toolbar.title = getString(R.string.nav_hev_socks5_tunnel)
                HevSocks5TunnelFragment()
            }
            R.id.nav_shadowsocks -> {
                toolbar.title = getString(R.string.nav_shadowsocks)
                ShadowsocksFragment()
            }
            R.id.nav_log -> {
                toolbar.title = getString(R.string.nav_log)
                LogFragment().also { logFragment = it }
            }
            R.id.nav_about -> {
                toolbar.title = getString(R.string.nav_about)
                AboutFragment()
            }
            else -> {
                toolbar.title = getString(R.string.nav_home)
                HomeFragment().also { homeFragment = it }
            }
        }
        
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
            
        drawerLayout.closeDrawer(GravityCompat.START)
    }
    
    // 侧边栏导航菜单点击处理
    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        navigateToFragment(item.itemId)
        return true
    }
    
    // 显示提示消息
    fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    // 切换VPN服务状态
    fun toggleVpnService() {
        if (TProxyService.isRunning) {
            stopVpnService()
        } else {
            // 检查是否有其他VPN活动
            if (isOtherVpnActive()) {
                showVpnConflictDialog()
            } else {
                startVpn()
            }
        }
    }

    // 显示VPN冲突确认对话框
    private fun showVpnConflictDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.vpn_conflict_title)
            .setMessage(R.string.vpn_conflict_message)
            .setPositiveButton(R.string.vpn_conflict_continue) { _, _ ->
                startVpn()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
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

    private fun startVpn() {
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
        updateHomeUI()
    }
    
    private fun stopVpnService() {
        val serviceIntent = Intent(this, TProxyService::class.java)
        serviceIntent.action = TProxyService.ACTION_DISCONNECT
        startService(serviceIntent)
        prefs.isEnabled = false
        updateHomeUI()
    }
    
    // 更新Home页面UI
    private fun updateHomeUI() {
        if (homeFragment == null) {
            // 如果homeFragment为null，尝试从当前Fragment获取
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is HomeFragment) {
                homeFragment = currentFragment
            }
        }
        homeFragment?.updateUI()
    }

    // 添加日志
    fun appendLog(tag: String, message: String) {
        Log.d("Logger", "$tag: $message")
        if (logFragment == null) {
            // 如果logFragment为null，尝试从当前Fragment获取
            val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)
            if (currentFragment is LogFragment) {
                logFragment = currentFragment
            }
        }
        logFragment?.appendLog(tag, message)
    }
}