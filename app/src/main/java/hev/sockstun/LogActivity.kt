package hev.sockstun

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

class LogActivity : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContent {
			LogViewerScreen()
		}
	}
}

// Log colors for dark theme
private val COLOR_DEBUG_DARK = Color(0xFFAAAAAA) // Gray
private val COLOR_INFO_DARK = Color(0xFF00FF00)  // Green
private val COLOR_WARN_DARK = Color(0xFFFFFF00)  // Yellow
private val COLOR_ERROR_DARK = Color(0xFFFF0000) // Red
private val COLOR_DEFAULT_DARK = Color(0xFF00FF00) // Green

// Log colors for light theme
private val COLOR_DEBUG_LIGHT = Color(0xFF808080) // Gray
private val COLOR_INFO_LIGHT = Color(0xFF008000)  // Dark Green
private val COLOR_WARN_LIGHT = Color(0xFFB8860B)  // Dark Goldenrod
private val COLOR_ERROR_LIGHT = Color(0xFFCC0000) // Dark Red
private val COLOR_DEFAULT_LIGHT = Color(0xFF008000) // Dark Green

private const val MAX_LOG_SIZE = 100 * 1024 // 100KB max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen() {
	val context = LocalContext.current
	val scope = rememberCoroutineScope()
	val handler = remember { Handler(Looper.getMainLooper()) }

	var selectedTab by remember { mutableStateOf(0) }

	// Java log state
	var javaLogs by remember { mutableStateOf(AnnotatedString("")) }
	var javaOriginalLogs by remember { mutableStateOf("") }
	var javaSearchQuery by remember { mutableStateOf("") }
	var javaIsLoading by remember { mutableStateOf(false) }

	// Native log state
	var nativeLogs by remember { mutableStateOf(AnnotatedString("")) }
	var nativeOriginalLogs by remember { mutableStateOf("") }
	var nativeSearchQuery by remember { mutableStateOf("") }
	var nativeIsLoading by remember { mutableStateOf(false) }

	val listState = rememberLazyListState()

	// Load logs on startup
	LaunchedEffect(Unit) {
		refreshJavaLogs(
			context = context,
			handler = handler,
			onLoading = { javaIsLoading = it },
			onLogsLoaded = {
				javaOriginalLogs = it
				javaLogs = colorizeAndFilterLog(context, it, javaSearchQuery)
			}
		)
		refreshNativeLogs(
			context = context,
			handler = handler,
			onLoading = { nativeIsLoading = it },
			onLogsLoaded = {
				nativeOriginalLogs = it
				nativeLogs = colorizeAndFilterLog(context, it, nativeSearchQuery)
			}
		)
	}

	Scaffold(
		topBar = {
			TopAppBar(
				title = { Text(context.getString(R.string.log_viewer_title)) },
				colors = TopAppBarDefaults.topAppBarColors(
					containerColor = MaterialTheme.colorScheme.primaryContainer,
					titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
				)
			)
		}
	) { paddingValues ->
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(paddingValues)
		) {
			// Tab bar at top
			TabRow(selectedTabIndex = selectedTab) {
				Tab(selected = selectedTab == 0,
					onClick = { selectedTab = 0 },
					text = { Text(context.getString(R.string.tab_java_log)) }
				)
				Tab(selected = selectedTab == 1,
					onClick = { selectedTab = 1 },
					text = { Text(context.getString(R.string.tab_native_log)) }
				)
			}

			// Content based on selected tab
			when (selectedTab) {
				0 -> JavaLogTab(
					context = context,
					javaLogs = javaLogs,
					searchQuery = javaSearchQuery,
					onSearchQueryChange = {
						javaSearchQuery = it
						javaLogs = colorizeAndFilterLog(context, javaOriginalLogs, it)
					},
					isLoading = javaIsLoading,
					onRefresh = {
						scope.launch {
							refreshJavaLogs(
								context = context,
								handler = handler,
								onLoading = { javaIsLoading = it },
								onLogsLoaded = {
									javaOriginalLogs = it
									javaLogs = colorizeAndFilterLog(context, it, javaSearchQuery)
								}
							)
						}
					},
					onClear = {
						scope.launch {
							clearJavaLogs(
								context = context,
								handler = handler,
								onLogsCleared = {
									javaOriginalLogs = it
									javaLogs = buildAnnotatedString { append(it) }
								}
							)
						}
					},
					listState = listState
				)
				1 -> NativeLogTab(
					context = context,
					nativeLogs = nativeLogs,
					searchQuery = nativeSearchQuery,
					onSearchQueryChange = {
						nativeSearchQuery = it
						nativeLogs = colorizeAndFilterLog(context, nativeOriginalLogs, it)
					},
					isLoading = nativeIsLoading,
					onRefresh = {
						scope.launch {
							refreshNativeLogs(
								context = context,
								handler = handler,
								onLoading = { nativeIsLoading = it },
								onLogsLoaded = {
									nativeOriginalLogs = it
									nativeLogs = colorizeAndFilterLog(context, it, nativeSearchQuery)
								}
							)
						}
					},
					onClear = {
						scope.launch {
							clearNativeLogs(
								context = context,
								handler = handler,
								onLogsCleared = {
									nativeOriginalLogs = it
									nativeLogs = buildAnnotatedString { append(it) }
								}
							)
						}
					},
					listState = listState
				)
			}
		}
	}
}

@Composable
fun JavaLogTab(
	context: Context,
	javaLogs: AnnotatedString,
	searchQuery: String,
	onSearchQueryChange: (String) -> Unit,
	isLoading: Boolean,
	onRefresh: () -> Unit,
	onClear: () -> Unit,
	listState: androidx.compose.foundation.lazy.LazyListState
) {
	LogTabContent(
		context = context,
		logs = javaLogs,
		searchQuery = searchQuery,
		onSearchQueryChange = onSearchQueryChange,
		isLoading = isLoading,
		onRefresh = onRefresh,
		onClear = onClear,
		listState = listState
	)
}

@Composable
fun NativeLogTab(
	context: Context,
	nativeLogs: AnnotatedString,
	searchQuery: String,
	onSearchQueryChange: (String) -> Unit,
	isLoading: Boolean,
	onRefresh: () -> Unit,
	onClear: () -> Unit,
	listState: androidx.compose.foundation.lazy.LazyListState
) {
	LogTabContent(
		context = context,
		logs = nativeLogs,
		searchQuery = searchQuery,
		onSearchQueryChange = onSearchQueryChange,
		isLoading = isLoading,
		onRefresh = onRefresh,
		onClear = onClear,
		listState = listState
	)
}

@Composable
fun LogTabContent(
	context: Context,
	logs: AnnotatedString,
	searchQuery: String,
	onSearchQueryChange: (String) -> Unit,
	isLoading: Boolean,
	onRefresh: () -> Unit,
	onClear: () -> Unit,
	listState: androidx.compose.foundation.lazy.LazyListState
) {
	Column(
		modifier = Modifier.fillMaxSize()
	) {
		// Search and action bar
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.padding(8.dp),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			verticalAlignment = Alignment.CenterVertically
		) {
			OutlinedTextField(
				value = searchQuery,
				onValueChange = onSearchQueryChange,
				modifier = Modifier.weight(1f),
				placeholder = { Text(context.getString(R.string.search_logs)) },
				singleLine = true
			)

			Button(
				onClick = onRefresh,
				enabled = !isLoading
			) {
				Text(context.getString(R.string.button_refresh))
			}

			Button(
				onClick = onClear,
				enabled = !isLoading
			) {
				Text(context.getString(R.string.button_clear))
			}
		}

		// Log content
		if (isLoading) {
			Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center
			) {
				CircularProgressIndicator()
			}
		} else if (logs.text.isNotEmpty()) {
			val scrollState = rememberScrollState()
			SelectionContainer {
				Column(
					modifier = Modifier
						.fillMaxSize()
						.verticalScroll(scrollState)
						.padding(8.dp)
				) {
					Text(
						text = logs,
						fontSize = 12.sp,
						lineHeight = 18.sp,
						fontFamily = FontFamily.Monospace,
						modifier = Modifier.fillMaxWidth()
					)
				}
			}
		} else {
			Box(
				modifier = Modifier.fillMaxSize(),
				contentAlignment = Alignment.Center
			) {
				Text(context.getString(R.string.no_logs), style = MaterialTheme.typography.bodyLarge)
			}
		}
	}
}

// ==================== Core Functions ====================

private fun refreshJavaLogs(
	context: Context,
	handler: Handler,
	onLoading: (Boolean) -> Unit,
	onLogsLoaded: (String) -> Unit
) {
	onLoading(true)
	Thread {
		val logs = readLogsFromFile(context, "java.log")
		handler.post {
			onLoading(false)
			if (logs != null && logs.isNotEmpty()) {
				onLogsLoaded(logs)
			} else {
				onLogsLoaded(context.getString(R.string.log_no_java_logs))
			}
		}
	}.start()
}

private fun refreshNativeLogs(
	context: Context,
	handler: Handler,
	onLoading: (Boolean) -> Unit,
	onLogsLoaded: (String) -> Unit
) {
	onLoading(true)
	Thread {
		val config = readConfigFile(context)
		val logs = readLogsFromFile(context, "tunnel.log")
		handler.post {
			onLoading(false)
			val display = StringBuilder()

			if (config != null && config.isNotEmpty()) {
				display.append("========== tproxy.conf ==========\n")
				display.append(config)
				display.append("\n========== End of Config ==========\n\n")
			}

			if (logs != null && logs.isNotEmpty()) {
				display.append("========== tunnel.log ==========\n")
				display.append(logs)
				display.append("\n========== End of Logs ==========")
				onLogsLoaded(display.toString())
			} else if (config != null && config.isNotEmpty()) {
				display.append("========== tunnel.log ==========\n")
				display.append("No logs available. Make sure VPN is running.")
				onLogsLoaded(display.toString())
			} else {
				onLogsLoaded(context.getString(R.string.log_no_native_logs))
			}
		}
	}.start()
}

private fun clearJavaLogs(
	context: Context,
	handler: Handler,
	onLogsCleared: (String) -> Unit
) {
	Thread {
		try {
			val logFile = File(context.cacheDir, "java.log")
			if (logFile.exists()) {
				logFile.delete()
			}
			handler.post {
				onLogsCleared(context.getString(R.string.log_java_cleared))
			}
		} catch (e: Exception) {
			handler.post {
				onLogsCleared(context.getString(R.string.log_clear_failed, e.message))
			}
		}
	}.start()
}

private fun clearNativeLogs(
	context: Context,
	handler: Handler,
	onLogsCleared: (String) -> Unit
) {
	Thread {
		try {
			val logFile = File(context.cacheDir, "tunnel.log")
			if (logFile.exists()) {
				logFile.delete()
			}
			handler.post {
				onLogsCleared(context.getString(R.string.log_native_cleared))
			}
		} catch (e: Exception) {
			handler.post {
				onLogsCleared(context.getString(R.string.log_clear_failed, e.message))
			}
		}
	}.start()
}

private fun filterLogs(originalLogs: String, filter: String): String {
	if (originalLogs.isEmpty()) return originalLogs
	if (filter.isEmpty()) return originalLogs

	val lowerFilter = filter.lowercase()
	val lines = originalLogs.split("\n")
	val filtered = StringBuilder()

	for (line in lines) {
		if (line.lowercase().contains(lowerFilter)) {
			filtered.append(line).append("\n")
		}
	}

	return filtered.toString()
}

private fun readConfigFile(context: Context): String? {
	val configFile = File(context.cacheDir, "tproxy.conf")
	if (!configFile.exists()) return null

	return try {
		val sb = StringBuilder()
		BufferedReader(FileReader(configFile)).use { reader ->
			var line: String?
			while (reader.readLine().also { line = it } != null) {
				sb.append(line).append("\n")
			}
		}
		sb.toString()
	} catch (e: Exception) {
		"Error reading config: ${e.message}"
	}
}

private fun readLogsFromFile(context: Context, filename: String): String? {
	val logFile = File(context.cacheDir, filename)
	if (!logFile.exists()) return null

	return try {
		val fileLength = logFile.length()
		if (fileLength <= 0) return null

		val startPos = if (fileLength > MAX_LOG_SIZE) fileLength - MAX_LOG_SIZE else 0

		RandomAccessFile(logFile, "r").use { raf ->
			raf.seek(startPos)

			val sb = StringBuilder()
			BufferedReader(FileReader(raf.fd)).use { reader ->
				var line: String?
				while (reader.readLine().also { line = it } != null) {
					sb.append(line).append("\n")
				}
			}
			sb.toString()
		}
	} catch (e: Exception) {
		"Error reading logs: ${e.message}"
	}
}

private fun colorizeAndFilterLog(context: Context, originalLogs: String, filter: String): AnnotatedString {
	val filteredLogs = filterLogs(originalLogs, filter)
	return buildAnnotatedString {
		append(colorizeLog(context, filteredLogs))
	}
}

private fun colorizeLog(context: Context, log: String): AnnotatedString {
	val isLightTheme = (context.resources.configuration.uiMode and
		Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO

	val colorDebug = if (isLightTheme) COLOR_DEBUG_LIGHT else COLOR_DEBUG_DARK
	val colorInfo = if (isLightTheme) COLOR_INFO_LIGHT else COLOR_INFO_DARK
	val colorWarn = if (isLightTheme) COLOR_WARN_LIGHT else COLOR_WARN_DARK
	val colorError = if (isLightTheme) COLOR_ERROR_LIGHT else COLOR_ERROR_DARK
	val colorDefault = if (isLightTheme) COLOR_DEFAULT_LIGHT else COLOR_DEFAULT_DARK

	return buildAnnotatedString {
		var start = 0
		while (start < log.length) {
			val levelStart = log.indexOf("[", start)
			if (levelStart == -1) {
				append(log.substring(start))
				break
			}

			val levelEnd = log.indexOf("]", levelStart)
			if (levelEnd == -1 || levelEnd - levelStart != 2) {
				start = levelStart + 1
				continue
			}

			val level = log[levelStart + 1]
			val color = when (level) {
				'D' -> colorDebug
				'I' -> colorInfo
				'W' -> colorWarn
				'E' -> colorError
				else -> {
					start = levelStart + 1
					continue
				}
			}

			val lineStart = log.lastIndexOf("\n", levelStart - 1) + 1
			val lineEnd = log.indexOf("\n", levelStart).let { if (it == -1) log.length else it }

			// Append text before this line
			if (lineStart > start) {
				append(log.substring(start, lineStart))
			}

			// Append colored line
			withStyle(SpanStyle(color = color)) {
				append(log.substring(lineStart, lineEnd))
			}
			// Add newline after the colored line
			if (lineEnd < log.length && log[lineEnd] == '\n') {
				append("\n")
			}

			start = lineEnd + 1
		}
	}
}

// ==================== Static Logging Methods ====================

fun log(context: Context?, level: String, tag: String, message: String) {
	if (context == null) return

	try {
		val logFile = File(context.cacheDir, "java.log")
		val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
		val timestamp = sdf.format(Date())
		val logLine = "[$timestamp] $tag: $message\n"

		FileWriter(logFile, true).use { writer ->
			writer.write(logLine)
		}
	} catch (e: Exception) {
		// Silently fail if logging fails
	}
}

fun d(context: Context?, tag: String, message: String) {
	log(context, "D", tag, message)
}

fun i(context: Context?, tag: String, message: String) {
	log(context, "I", tag, message)
}

fun w(context: Context?, tag: String, message: String) {
	log(context, "W", tag, message)
}

fun e(context: Context?, tag: String, message: String) {
	log(context, "E", tag, message)
}
