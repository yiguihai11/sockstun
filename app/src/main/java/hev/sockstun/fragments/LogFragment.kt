package hev.sockstun.fragments

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import hev.sockstun.R
import hev.sockstun.TProxyService
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class LogFragment : Fragment() {
    private lateinit var logTextView: TextView
    private lateinit var clearButton: ExtendedFloatingActionButton
    private lateinit var saveButton: ExtendedFloatingActionButton
    private lateinit var copyButton: ExtendedFloatingActionButton
    
    private val logBuffer = StringBuilder()
    private val maxLogSize = 2 * 1024 * 1024 // 2MB
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    private lateinit var logFile: File
    
    // 广播接收器，用于接收日志事件
    private val logReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TProxyService.ACTION_LOG_EVENT) {
                val tag = intent.getStringExtra(TProxyService.EXTRA_LOG_TAG) ?: "Unknown"
                val message = intent.getStringExtra(TProxyService.EXTRA_LOG_MESSAGE) ?: ""
                appendLog(tag, message)
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_log, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        logTextView = view.findViewById(R.id.log_text)
        clearButton = view.findViewById(R.id.fab_clear)
        saveButton = view.findViewById(R.id.fab_save)
        copyButton = view.findViewById(R.id.fab_copy)
        
        // 设置日志文本视图的属性
        logTextView.isVerticalScrollBarEnabled = true
        logTextView.isHorizontalScrollBarEnabled = true
        logTextView.setPadding(16, 16, 16, 16)
        
        // 设置按钮点击事件
        clearButton.setOnClickListener {
            clearLogs()
        }
        
        saveButton.setOnClickListener {
            saveLogs()
        }
        
        copyButton.setOnClickListener {
            copyLogs()
        }
        
        // 初始化日志文件
        logFile = File(requireContext().filesDir, "app.log")
        
        // 加载现有日志
        loadLog()
        
        // 注册广播接收器
        val intentFilter = IntentFilter(TProxyService.ACTION_LOG_EVENT)
        requireContext().registerReceiver(
            logReceiver,
            intentFilter,
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        
        // 注销广播接收器
        try {
            requireContext().unregisterReceiver(logReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun loadLog() {
        try {
            if (logFile.exists()) {
                val logContent = logFile.readText()
                logBuffer.append(logContent)
                
                // 延迟更新显示，确保视图已完全初始化
                view?.post {
                    updateDisplay()
                }
            } else {
                view?.post {
                    appendLog("System", getString(R.string.log_file_not_exist))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            view?.post {
                appendLog("Error", getString(R.string.log_load_failed) + ": ${e.message}")
            }
        }
    }
    
    fun appendLog(tag: String, message: String) {
        if (!isAdded) return
        
        try {
            val timestamp = dateFormat.format(Date())
            val logEntry = "$timestamp $tag: $message\n"
            
            // 检查日志大小
            if (logBuffer.length + logEntry.length > maxLogSize) {
                // 移除最早的日志直到大小合适
                while (logBuffer.length + logEntry.length > maxLogSize) {
                    val firstNewline = logBuffer.indexOf('\n')
                    if (firstNewline == -1) break
                    logBuffer.delete(0, firstNewline + 1)
                }
            }
            
            logBuffer.append(logEntry)
            
            // 在UI线程中更新显示
            activity?.runOnUiThread {
                updateDisplay()
            }
            
            // 保存到文件
            saveToFile()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun updateDisplay() {
        try {
            if (!isAdded || logTextView == null) return
            
            logTextView.text = logBuffer.toString()
            
            // 自动滚动到底部
            val layout = logTextView.layout
            if (layout != null) {
                val scrollY = layout.getLineTop(layout.lineCount) - logTextView.height
                if (scrollY > 0) {
                    logTextView.scrollTo(0, scrollY)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun clearLogs() {
        logBuffer.clear()
        logTextView.text = ""
        // 清除日志文件
        try {
            logFile.delete()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        updateDisplay()
        Snackbar.make(requireView(), getString(R.string.log_cleared), Snackbar.LENGTH_SHORT).show()
    }
    
    private fun saveLogs() {
        try {
            logFile.writeText(logBuffer.toString())
            Snackbar.make(requireView(), getString(R.string.log_saved), Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Snackbar.make(requireView(), getString(R.string.log_save_failed), Snackbar.LENGTH_SHORT).show()
        }
    }
    
    private fun copyLogs() {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Logs", logBuffer.toString())
        clipboard.setPrimaryClip(clip)
        Snackbar.make(requireView(), getString(R.string.log_copied), Snackbar.LENGTH_SHORT).show()
    }
    
    private fun saveToFile() {
        try {
            // 确保文件存在
            if (!logFile.exists()) {
                logFile.createNewFile()
            }
            
            // 写入日志内容
            logFile.writeText(logBuffer.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
} 