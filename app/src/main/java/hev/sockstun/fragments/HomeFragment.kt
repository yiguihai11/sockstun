package hev.sockstun.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import hev.sockstun.MainActivity
import hev.sockstun.Preferences
import hev.sockstun.R
import hev.sockstun.TProxyService

class HomeFragment : Fragment() {

    private var buttonControl: MaterialButton? = null
    private var statusText: TextView? = null
    private lateinit var prefs: Preferences
    private val handler = Handler(Looper.getMainLooper())
    private var isUpdating = false
    
    // 用于接收VPN服务状态广播的Receiver
    private val serviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TProxyService.ACTION_STATUS_CHANGED) {
                val isRunning = intent.getBooleanExtra(TProxyService.EXTRA_IS_RUNNING, false)
                prefs.isEnabled = isRunning
                // 状态变化时重置更新状态
                isUpdating = false
                updateUI()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        prefs = Preferences(requireContext())
        
        // 初始化视图
        buttonControl = view.findViewById(R.id.control)
        statusText = view.findViewById(R.id.status_text)
        
        // 设置点击监听器
        buttonControl?.setOnClickListener {
            if (!isUpdating) {
                isUpdating = true
                val mainActivity = activity as? MainActivity
                mainActivity?.toggleVpnService()
                
                // 5秒后强制重置状态（以防广播没有收到）
                handler.postDelayed({
                    isUpdating = false
                    updateUI()
                }, 5000)
            }
        }
        
        // 注册广播接收器以监听服务状态变化
        val intentFilter = IntentFilter(TProxyService.ACTION_STATUS_CHANGED)
        requireContext().registerReceiver(
            serviceReceiver,
            intentFilter,
            Context.RECEIVER_NOT_EXPORTED
        )
        
        // 更新UI状态
        updateUI()
    }
    
    override fun onResume() {
        super.onResume()
        // 重置更新状态
        isUpdating = false
        // 更新UI状态
        updateUI()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // 在 onDestroyView 中注销广播接收器
        try {
            requireContext().unregisterReceiver(serviceReceiver)
        } catch (e: IllegalArgumentException) {
            // 接收器可能未注册
        }
        
        // 清除视图引用
        buttonControl = null
        statusText = null
    }
    
    // 更新UI状态
    fun updateUI() {
        // 确保在主线程中更新UI
        view?.post {
            try {
                // 更新VPN状态
                val isRunning = TProxyService.isRunning
                statusText?.text = getString(
                    R.string.vpn_status_prefix) + " " + 
                    getString(if (isRunning) R.string.vpn_status_enabled else R.string.vpn_status_disabled)
                
                // 更新按钮状态和文本
                buttonControl?.apply {
                    text = getString(if (isRunning) R.string.stop else R.string.control_enable)
                    isEnabled = !isUpdating
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
} 