package com.ai.assistance.operit.core.application

import android.app.Application
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import android.system.Os
import com.ai.assistance.operit.util.AppLogger
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.ai.assistance.operit.core.tools.system.AndroidShellExecutor
import com.ai.assistance.operit.core.tools.system.Terminal
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.data.preferences.initAndroidPermissionPreferences
import com.ai.assistance.operit.data.preferences.initUserPreferencesManager
import com.ai.assistance.operit.data.preferences.preferencesManager
import com.ai.assistance.operit.util.LocaleUtils
import com.ai.assistance.operit.util.CodexPaths
import com.ai.assistance.operit.util.SerializationSetup
import com.ai.assistance.operit.core.tools.agent.ShowerController
import com.ai.assistance.operit.core.tools.system.shower.CodexShowerShellRunner
import com.ai.assistance.showerclient.ShowerEnvironment
import com.ai.assistance.showerclient.ShowerLogSink
import java.io.File
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Application class for Codex AI */
class CodexApplication : Application(), ImageLoaderFactory {

    companion object {
        @Volatile
        var appStartupTimeMs: Long = 0L
            private set

        lateinit var instance: CodexApplication
            private set

        lateinit var globalImageLoader: ImageLoader
            private set

        private const val TAG = "CodexApplication"
    }

    private fun configureOpenMpEnvironment() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Os.setenv("KMP_AFFINITY", "disabled", true)
                Os.setenv("OMP_WAIT_POLICY", "PASSIVE", true)
                Os.setenv("OMP_PROC_BIND", "true", true)
                Os.setenv("OMP_PLACES", "threads", true)
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "配置OpenMP环境失败", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        appStartupTimeMs = System.currentTimeMillis()
        instance = this

        // 初始化全局 JSON 序列化配置
        SerializationSetup.createGlobalJson()

        // 初始化 AndroidShellExecutor
        AndroidShellExecutor.init(this)

        // 初始化 UserPreferencesManager
        initUserPreferencesManager(this)
        initAndroidPermissionPreferences(this)

        // 初始化 Shower
        ShowerController.init(this)
        CodexShowerShellRunner.init(this)
        ShowerLogSink.init(this)

        // 初始化 AppLogger
        AppLogger.init(this)
        AppLogger.d(TAG, "Codex Application 创建完毕")

        // 设置语言
        applySavedLanguage()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .availableMemoryPercentage(0.25)
            .crossfade(true)
            .build()
    }

    private fun applySavedLanguage() {
        try {
            val languageCode = runBlocking {
                preferencesManager(UserPreferencesManager::languageCode).first()
            }
            val locale = LocaleUtils.getLocaleForLanguageCode(languageCode, this)
            val config = Configuration(resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
            } else {
                config.locale = locale
                Locale.setDefault(locale)
            }
            resources.updateConfiguration(config, resources.displayMetrics)
            AppLogger.d(TAG, "已应用语言: $languageCode")
        } catch (e: Exception) {
            AppLogger.e(TAG, "应用语言设置失败", e)
        }
    }

    override fun attachBaseContext(base: android.content.Context) {
        configureOpenMpEnvironment()
        try {
            val code = LocaleUtils.getCurrentLanguage(base)
            val locale = LocaleUtils.getLocaleForLanguageCode(code, base)
            val config = Configuration(base.resources.configuration)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val localeList = LocaleList(locale)
                LocaleList.setDefault(localeList)
                config.setLocales(localeList)
            } else {
                config.locale = locale
                Locale.setDefault(locale)
            }
            val context = base.createConfigurationContext(config)
            super.attachBaseContext(context)
        } catch (e: Exception) {
            super.attachBaseContext(base)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        try {
            Terminal.getInstance(applicationContext).destroy()
        } catch (e: Exception) {
            AppLogger.e(TAG, "清理终端管理器失败: ${e.message}", e)
        }
        try {
            ShowerController.shutdown()
        } catch (e: Exception) {
            AppLogger.e(TAG, "关闭 ShowerController 失败: ${e.message}", e)
        }
    }
}
