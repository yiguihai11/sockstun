package hev.sockstun

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 原生程序执行器
 * 负责执行APK中打包的原生程序(.so)
 */
class NativeProgramExecutor(context: Context) {
    
    companion object {
        private const val TAG = "NativeProgramExecutor"
        private const val LIB_SSLOCAL = "libsslocal.so"
    }
    
    // 原生库目录
    private val nativeLibDir: String = context.applicationInfo.nativeLibraryDir
    
    /**
     * 在nativeLibraryDir中执行原生程序
     * 
     * @param libName 程序文件名（必须是.so结尾）
     * @param args 程序参数
     * @return 执行结果，包含输出文本、退出码和是否成功
     */
    fun executeNativeProgram(libName: String, vararg args: String): ExecutionResult {
        try {
            // 确保在原生库目录中存在该文件
            val execFile = File(nativeLibDir, libName)
            if (!execFile.exists()) {
                Log.e(TAG, "在原生库目录中找不到 $libName")
                return ExecutionResult("", -1, false, "文件不存在")
            }
            
            // 检查执行权限
            if (!execFile.canExecute()) {
                // 尝试添加执行权限
                if (!execFile.setExecutable(true)) {
                    Log.e(TAG, "$libName 没有执行权限且无法授予")
                    return ExecutionResult("", -1, false, "没有执行权限")
                }
                Log.d(TAG, "已为 $libName 授予执行权限")
            }
            
            Log.d(TAG, "尝试执行原生程序: ${execFile.absolutePath} ${args.joinToString(" ")}")
            
            // 构建命令并执行
            val command = mutableListOf(execFile.absolutePath)
            command.addAll(args)
            
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            
            // 获取输出和退出码
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            
            val success = (exitCode == 0)
            Log.d(TAG, "执行结果 - 成功: $success, 退出码: $exitCode, 输出长度: ${output.length}")
            
            return ExecutionResult(
                output = output.trim(),
                exitCode = exitCode,
                success = success,
                errorMessage = if (success) "" else "程序执行失败，退出码: $exitCode"
            )
        } catch (e: Exception) {
            Log.e(TAG, "执行原生程序时出错", e)
            return ExecutionResult("", -1, false, e.message ?: "未知错误")
        }
    }
    
    /**
     * 获取libsslocal的版本号
     * @return 版本号字符串，获取失败则返回null
     */
    fun getSsLocalVersion(): String? {
        val result = executeNativeProgram(LIB_SSLOCAL, "-V")
        return if (result.success) {
            result.output.also {
                Log.i(TAG, "libsslocal版本信息: $it")
            }
        } else {
            Log.e(TAG, "获取版本失败: ${result.errorMessage}")
            null
        }
    }
    
    /**
     * 执行结果类
     */
    data class ExecutionResult(
        val output: String,       // 程序输出
        val exitCode: Int,        // 退出码
        val success: Boolean,     // 是否成功执行
        val errorMessage: String  // 错误信息
    )
} 