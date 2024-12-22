package com.root.library.shell

import android.os.Handler
import android.os.Looper
import com.root.common.shell.KeepShell
import com.root.common.shell.KeepShellPublic
import com.root.common.shell.RootFile.fileExists
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 帧率检测工具
 */
class FpsUtils(private val keepShell: KeepShell = KeepShellPublic.secondaryKeepShell) {
    private var fpsFilePath: String? = null
    private var fpsCommand = "service call SurfaceFlinger 1013"
    private var lastTime = AtomicLong(-1)
    private var lastFrames = AtomicLong(-1)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isMonitoring = AtomicBoolean(false)
    private var monitoringCallback: ((Float) -> Unit)? = null
    private var cachedFps: String = "0.0"
    private var lastUpdateTime = AtomicLong(0)
    private val initialized = AtomicBoolean(false)
    private var monitorRunnable: Runnable? = null

    init {
        // 快速初始化
        mainHandler.post { 
            initializeAsync()
        }
    }

    private fun initializeAsync() {
        if (initialized.compareAndSet(false, true)) {
            try {
                // 初始化文件路径
                if (fpsFilePath == null) {
                    fpsFilePath = when {
                        fileExists("/sys/class/drm/sde-crtc-0/measured_fps") -> 
                            "/sys/class/drm/sde-crtc-0/measured_fps"
                        fileExists("/sys/class/graphics/fb0/measured_fps") -> 
                            "/sys/class/graphics/fb0/measured_fps"
                        else -> findFpsFile()
                    }
                }

                // 验证系统 FPS 命令
                val testResult = keepShell.doCmdSync(fpsCommand).trim()
                if (testResult == "error" || testResult.contains("Parcel")) {
                    fpsCommand = ""
                }
            } catch (ex: Exception) {
                // 初始化失败时的处理
            }
        }
    }

    fun setMonitoringCallback(callback: (Float) -> Unit) {
        monitoringCallback = callback
    }

    val currentFps: String
        get() {
            return try {
                // 1. 先尝试使用 dumpsys gfxinfo 获取 FPS
                getFpsFromGfxInfo()
                    // 2. 尝试读取文件中的 FPS
                    ?: readFpsFromFile()
                    // 3. 如果无法获取到真实 FPS，尝试使用 Choreographer
                    ?: choreographerFps.get()
            } catch (ex: Exception) {
                choreographerFps.get()
            }
        }

    // 使用 dumpsys gfxinfo 获取 FPS
    private fun getFpsFromGfxInfo(): String? {
        return try {
            val result = keepShell.doCmdSync("dumpsys gfxinfo").trim()
            if (result.contains("Permission denied") || result.isEmpty()) {
                return null
            }

            val frameTimes = mutableListOf<Long>()
            var frameCount = 0
            var totalTime = 0L

            // 从 dumpsys gfxinfo 中提取每一帧的渲染时间
            result.lines().forEach { line ->
                if (line.startsWith("Frame #")) {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        try {
                            // 获取每一帧的渲染时间
                            val frameTime = parts[3].toLong()
                            frameTimes.add(frameTime)
                            totalTime += frameTime
                            frameCount++
                        } catch (e: NumberFormatException) {
                            // 如果无法解析为数字，忽略该行
                        }
                    }
                }
            }

            // 如果成功提取了渲染时间，则计算 FPS
            if (frameCount > 0) {
                val averageFrameTime = totalTime.toFloat() / frameCount
                val fps = 1000000000.0f / averageFrameTime // 每秒帧数
                String.format("%.1f", fps)
            } else {
                null
            }
        } catch (ex: Exception) {
            null
        }
    }

    private fun readFpsFromFile(): String? {
        // 尝试固定路径
        listOf(
            "/sys/class/drm/sde-crtc-0/measured_fps",
            "/sys/class/graphics/fb0/measured_fps"
        ).forEach { path ->
            if (fileExists(path)) {
                val fps = keepShell.doCmdSync("cat $path").trim()
                if (fps.isNotEmpty() && fps != "0" && fps != "N/A") {
                    return fps
                }
            }
        }

        // 尝试动态查找文件
        findFpsFile()?.let { path ->
            if (fileExists(path)) {
                val fps = keepShell.doCmdSync("cat $path").trim()
                if (fps.isNotEmpty() && fps != "0" && fps != "N/A") {
                    return fps
                }
            }
        }

        return null
    }

    private fun updateFps(): String {
        return getSystemFps()?.takeIf { it.toFloatOrNull() ?: 0f > 0f }
            ?: getForegroundAppFps()?.takeIf { it.toFloatOrNull() ?: 0f > 0f }
            ?: cachedFps
    }

    val fps: Float
        get() = currentFps.toFloatOrNull() ?: 0f  

    private val frameCallback = AtomicReference<Choreographer.FrameCallback>()
    private var frameCount = AtomicLong(0)
    private var lastFrameTime = AtomicLong(0)
    private val choreographerFps = AtomicReference<String>("0.0")

    private fun initChoreographerFps() {
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                calculateChoreographerFps(frameTimeNanos)
                if (isMonitoring.get()) {
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }
        }
        frameCallback.set(callback)
    }

    private fun calculateChoreographerFps(frameTimeNanos: Long) {
        val lastTime = lastFrameTime.get()
        if (lastTime > 0) {
            val timeDiff = frameTimeNanos - lastTime
            if (timeDiff > 0) {
                frameCount.incrementAndGet()
                // 每一秒更新一次 FPS
                if (timeDiff >= 1_000_000_000L) {
                    val fps = (frameCount.get() * 1_000_000_000.0f) / timeDiff
                    choreographerFps.set(String.format("%.1f", fps))
                    frameCount.set(0)
                    lastFrameTime.set(frameTimeNanos)
                }
            }
        } else {
            lastFrameTime.set(frameTimeNanos)
        }
    }

    fun startMonitoring() {
        if (isMonitoring.compareAndSet(false, true)) {
            initChoreographerFps()
            frameCallback.get()?.let { callback ->
                Choreographer.getInstance().postFrameCallback(callback)
            }
            
            monitorRunnable = object : Runnable {
                override fun run() {
                    if (!isMonitoring.get()) return
                    
                    try {
                        currentFps.toFloatOrNull()?.let { fps ->
                            monitoringCallback?.invoke(fps)
                        }
                    } catch (ex: Exception) {
                        // 错误处理
                    }
                    
                    if (isMonitoring.get()) {
                        mainHandler.postDelayed(this, REFRESH_INTERVAL)
                    }
                }
            }.also { runnable ->
                mainHandler.post(runnable)
            }
        }
    }

    fun stopMonitoring() {
        isMonitoring.set(false)
        monitoringCallback = null
        monitorRunnable?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
        }
        monitorRunnable = null
        frameCallback.get()?.let { callback ->
            Choreographer.getInstance().removeFrameCallback(callback)
        }
    }

    fun destroy() {
        stopMonitoring()
        mainHandler.removeCallbacksAndMessages(null)
        keepShell.tryExit()
    }

    private fun getSystemFps(): String? {
        if (fpsCommand.isEmpty()) return null

        return try {
            val result = keepShell.doCmdSync(fpsCommand).trim()
            if (result == "error" || result.contains("Parcel")) {
                fpsCommand = ""
                return null
            }

            val index = result.indexOf("(") + 1
            val frames = Integer.parseInt(result.substring(index, index + 8), 16)
            val time = System.currentTimeMillis()
            
            calculateSystemFps(frames, time)?.takeIf { it.toFloatOrNull() ?: 0f > 0f }
        } catch (ex: Exception) {
            fpsCommand = ""
            null
        }
    }

    private fun calculateSystemFps(frames: Int, time: Long): String? {
        val lastTimeValue = lastTime.get()
        val lastFramesValue = lastFrames.get()
        
        val fpsValue = if (lastTimeValue > 0 && lastFramesValue > 0) {
            val timeDiff = time - lastTimeValue
            if (timeDiff > 0) {
                val frameDiff = frames - lastFramesValue
                (frameDiff.toFloat() / timeDiff.toFloat()) * 1000
            } else {
                0f
            }
        } else {
            -1f
        }

        if (fpsValue >= 0f) {
            lastTime.set(time)
            lastFrames.set(frames)
            return String.format("%.1f", fpsValue)
        }
        return null
    }
}
