package com.hazagou.spectra.service

import android.app.Notification
import android.app.PendingIntent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Notificação simplificada para o painel do launcher. */
data class SpectraNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val time: Long,
    val clearable: Boolean,
    val contentIntent: PendingIntent?,
)

/**
 * Lê as notificações (após o usuário liberar "Acesso a notificações") para mostrar
 * o contador no sino da barra de status e o painel de notificações.
 */
class SpectraNotificationService : NotificationListenerService() {

    companion object {
        private val _items = MutableStateFlow<List<SpectraNotification>>(emptyList())
        val items: StateFlow<List<SpectraNotification>> = _items
        @Volatile private var instance: SpectraNotificationService? = null
        val connected: Boolean get() = instance != null

        fun dismiss(key: String) {
            runCatching { instance?.cancelNotification(key) }
        }

        fun dismissAll() {
            runCatching { instance?.cancelAllNotifications() }
        }
    }

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        instance = null
        _items.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = refresh()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = refresh()

    private fun refresh() {
        val list = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
        _items.value = list
            .filter { it.packageName != packageName }
            .filter { (it.notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0 }
            .mapNotNull { sbn ->
                val ex = sbn.notification.extras
                val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) return@mapNotNull null
                SpectraNotification(sbn.key, sbn.packageName, title, text, sbn.postTime, sbn.isClearable, sbn.notification.contentIntent)
            }
            .sortedByDescending { it.time }
    }
}
