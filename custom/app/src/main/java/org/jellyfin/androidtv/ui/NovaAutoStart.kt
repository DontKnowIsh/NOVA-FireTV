package org.jellyfin.androidtv.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * "Open NOVA when the Fire TV turns on": NOVA comes up by itself when the Fire TV
 * starts or wakes from sleep, instead of Amazon's home screen. Settings →
 * "Open NOVA on start-up" switches it off. On Fire OS 8 the Fire TV also needs
 * NOVA allowed to "display over other apps" (one adb command), or Android won't
 * let it open itself.
 */
object NovaAutoStart {
	private const val PREFS = "nova_settings"
	private const val KEY = "auto_start"

	fun isEnabled(context: Context): Boolean =
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, true)

	fun setEnabled(context: Context, enabled: Boolean) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
		if (enabled) start(context) else context.stopService(Intent(context, NovaWakeService::class.java))
	}

	/** Keeps the little "wake" watcher running (called when NOVA starts and at boot). */
	fun start(context: Context) {
		if (!isEnabled(context)) return
		runCatching { ContextCompat.startForegroundService(context, Intent(context, NovaWakeService::class.java)) }
			.onFailure { Timber.w(it, "NOVA: couldn't start the wake watcher") }
	}

	/** Brings NOVA to the front (a moment after waking, once Amazon's home has loaded). */
	internal fun openNova(context: Context) {
		val pm = context.packageManager
		val launch = pm.getLeanbackLaunchIntentForPackage(context.packageName)
			?: pm.getLaunchIntentForPackage(context.packageName)
			?: return
		launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
		runCatching { context.startActivity(launch) }
			.onFailure { Timber.w(it, "NOVA: couldn't open on start-up") }
	}
}

/** The Fire TV has just started: open NOVA, and keep watching for wake-ups. */
class NovaBootReceiver : BroadcastReceiver() {
	override fun onReceive(context: Context, intent: Intent) {
		val action = intent.action ?: return
		if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.QUICKBOOT_POWERON") return
		if (!NovaAutoStart.isEnabled(context)) return
		NovaAutoStart.start(context)
		val app = context.applicationContext
		Handler(Looper.getMainLooper()).postDelayed({ NovaAutoStart.openNova(app) }, 4_000L)
	}
}

/**
 * A tiny background watcher: when the Fire TV wakes from sleep (screen on),
 * NOVA is brought to the front.
 */
class NovaWakeService : Service() {
	private val handler = Handler(Looper.getMainLooper())
	private val receiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context, intent: Intent) {
			if (intent.action != Intent.ACTION_SCREEN_ON) return
			if (!NovaAutoStart.isEnabled(context)) return
			// Let Amazon's home screen settle first, then come to the front.
			handler.removeCallbacksAndMessages(null)
			handler.postDelayed({ NovaAutoStart.openNova(applicationContext) }, 1_500L)
		}
	}

	override fun onCreate() {
		super.onCreate()
		val manager = getSystemService(NotificationManager::class.java)
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			manager?.createNotificationChannel(
				NotificationChannel(CHANNEL, "NOVA start-up", NotificationManager.IMPORTANCE_MIN)
			)
		}
		val notification = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL) else Notification.Builder(this))
			.setContentTitle("NOVA")
			.setContentText("Opens NOVA when the Fire TV wakes up")
			.setSmallIcon(applicationInfo.icon)
			.setOngoing(true)
			.build()
		val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
		runCatching { ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type) }
			.onFailure { Timber.w(it, "NOVA: wake watcher couldn't run in the foreground") }
		ContextCompat.registerReceiver(this, receiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

	override fun onDestroy() {
		handler.removeCallbacksAndMessages(null)
		runCatching { unregisterReceiver(receiver) }
		super.onDestroy()
	}

	override fun onBind(intent: Intent?): IBinder? = null

	private companion object {
		const val CHANNEL = "nova_auto_start"
		const val NOTIFICATION_ID = 7417
	}
}
