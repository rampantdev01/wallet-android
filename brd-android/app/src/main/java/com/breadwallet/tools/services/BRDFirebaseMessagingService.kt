/**
 * BreadWallet
 *
 * Created by Pablo Budelli <pablo.budelli@breadwallet.com> on 7/11/19.
 * Copyright (c) 2019 breadwallet LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.breadwallet.tools.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import android.util.Log
import com.breadwallet.R
import com.breadwallet.app.BreadApp
import com.breadwallet.tools.manager.BRSharedPrefs
import com.breadwallet.tools.security.BrdUserState
import com.breadwallet.tools.security.BrdUserManager
import com.breadwallet.tools.threads.executor.BRExecutor
import com.breadwallet.tools.util.EventUtils
import com.breadwallet.ui.MainActivity
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.platform.network.NotificationsSettingsClientImpl
import com.platform.util.getStringOrNull
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.kodein.di.android.closestKodein
import org.kodein.di.direct
import org.kodein.di.erased.instance

/**
 * This class is responsible for updating the Firebase registration token each time a new one is available,
 * and receiving and displaying push notifications.
 */
class BRDFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private val TAG = BRDFirebaseMessagingService::class.java.simpleName

        private const val NOTIFICATION_TITLE = "title"
        private const val NOTIFICATION_BODY = "body"
        private const val NOTIFICATION_CHANNEL_ID = "brd_notifications_channel"

        // Mixpanel notification's keys
        private const val MP_NOTIFICATION_MESSAGE = "mp_message"
        private const val MP_NOTIFICATION_BRD_EXTRAS = "brd"
        private const val MP_NOTIFICATION_URL = "url"
        private const val MP_CAMPAIGN_ID = "mp_campaign_id"

        /**
         * The [FirebaseMessagingService] starts on its own.  However, we don't want to send messages
         * from the BRD server to the device until the wallet is ready.  Thus, we mustn't register
         * with the server until the wallet is created.
         *
         * @param context The context in which we are operating.
         */
        fun initialize(context: Context) {
            val firebaseToken = BRSharedPrefs.getFCMRegistrationToken()
            if (!firebaseToken.isNullOrBlank() && BRSharedPrefs.getShowNotification()) {
                BRExecutor.getInstance().forLightWeightBackgroundTasks().execute {
                    NotificationsSettingsClientImpl.registerToken(
                        context,
                        firebaseToken
                    )
                }
            }
        }
    }

    /**
     * Get the next unique notification id which is required each notification sent into the system.
     *
     * @return A unique notification id.
     */
    private val nextNotificationId: Int
        @Synchronized get() {
            // Get the previously used notification id.
            // Increment the current id and update shared preferences.
            var previousId = BRSharedPrefs.getNotificationId()
            val nextId = ++previousId
            BRSharedPrefs.putNotificationId(nextId)
            return nextId
        }

    /**
     * Called when a new Firebase Cloud Management (FCM) registration token is generated by the Firebase framework.
     *
     * @param token The new token that was generated by Firebase.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)

        // Save token in shared preferences.
        Log.d(TAG, "onNewToken: token value: $token")
        BRSharedPrefs.putFCMRegistrationToken(token)
        BreadApp.applicationScope.launch {
            val kodein by closestKodein(applicationContext)
            val userManager = kodein.direct.instance<BrdUserManager>()
            userManager.stateChanges()
                .filter { it !is BrdUserState.Uninitialized }
                .first()
            updateFcmRegistrationToken(applicationContext, token)
        }
    }

    /**
     * Called when a new message (push notification) is sent from the BRD server to the device via Firebase.
     *
     * @param remoteMessage An object containing the payload of the Firebase message.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "message received")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)

        if (BRSharedPrefs.getShowNotification()
            && notificationManager != null
            && notificationManager is NotificationManager
        ) {
            // Setting up notification channels for Android O and above.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                setupChannels(notificationManager)
            }

            EventUtils.pushEvent(EventUtils.EVENT_PUSH_NOTIFICATION_RECEIVED)
            val notification = buildNotification(remoteMessage)
            notificationManager.notify(notification.first, notification.second)
        }
    }

    /**
     * Starting in Android 0(API 26), all notifications must be assigned to a notification channel. In this case, we
     * want to explicitly create a channel for PWB notifications
     *
     * @param notificationManager The []NotificationManager} that will be used to set up the notification channel.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private fun setupChannels(notificationManager: NotificationManager) {
        val notificationChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.PushNotifications_title),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationChannel.description = getString(R.string.PushNotifications_body)
        notificationManager.createNotificationChannel(notificationChannel)
    }

    /**
     * Updates the BRD server with the Firebase Cloud Management (FCM) registration token so that the BRD server
     * will be able to use Firebase to send messages to this device.
     *
     * @param context The context in which we are operating.
     */
    private fun updateFcmRegistrationToken(context: Context, token: String) {
        if (BRSharedPrefs.getShowNotification()) {
            Log.d(TAG, "updating FCM token: $token")
            BRExecutor.getInstance().forLightWeightBackgroundTasks()
                .execute { NotificationsSettingsClientImpl.registerToken(context, token) }
        }
    }

    private fun buildNotification(remoteMessage: RemoteMessage): Pair<Int, Notification> {
        return when {
            remoteMessage.notification != null -> buildFirebaseNotification(remoteMessage)
            remoteMessage.data.containsKey(MP_NOTIFICATION_MESSAGE) -> buildMixpanelNotification(
                remoteMessage
            )
            else -> buildSecureCheckoutNotification(remoteMessage)
        }
    }

    private fun buildFirebaseNotification(remoteMessage: RemoteMessage): Pair<Int, Notification> {
        val notificationBuilder =
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flower_black)
                .setContentTitle(remoteMessage.notification?.title.orEmpty())
                .setContentText(remoteMessage.notification?.body.orEmpty())
                .setContentIntent(getHomePendingIntent())
                .setAutoCancel(true)

        return Pair(nextNotificationId, notificationBuilder.build())
    }

    private fun buildMixpanelNotification(remoteMessage: RemoteMessage): Pair<Int, Notification> {
        val notificationBody = remoteMessage.data[MP_NOTIFICATION_MESSAGE]
        val notificationBuilder =
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flower_black)
                .setContentText(notificationBody)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(notificationBody)
                )
                .setContentIntent(getHomePendingIntent())
                .setAutoCancel(true)
                .setContentIntent(getMixpanelPendingIntent(remoteMessage))
        return Pair(nextNotificationId, notificationBuilder.build())
    }

    private fun buildSecureCheckoutNotification(remoteMessage: RemoteMessage): Pair<Int, Notification> {
        val notificationBuilder =
            NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_flower_black)
                .setContentTitle(remoteMessage.data[NOTIFICATION_TITLE])
                .setContentText(remoteMessage.data[NOTIFICATION_BODY])
                .setContentIntent(getHomePendingIntent())
                .setAutoCancel(true)
        return Pair(nextNotificationId, notificationBuilder.build())
    }

    private fun getMixpanelPendingIntent(remoteMessage: RemoteMessage): PendingIntent {
        val url = getMixpanelDeepLink(remoteMessage.data)
        val campaignId = remoteMessage.data[MP_CAMPAIGN_ID]

        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        return PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                if (!url.isNullOrEmpty()) {
                    putExtra(MainActivity.EXTRA_DATA, url)
                }
                putExtra(MainActivity.EXTRA_PUSH_NOTIFICATION_CAMPAIGN_ID, campaignId)
            }, pendingIntentFlags
        )
    }

    private fun getHomePendingIntent(): PendingIntent {
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

        return PendingIntent.getActivity(
            applicationContext, 0,
            Intent(applicationContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }, pendingIntentFlags
        )
    }

    private fun getMixpanelDeepLink(messageData: Map<String, String>): String? =
        if (messageData.containsKey(MP_NOTIFICATION_BRD_EXTRAS)) {
            val json = JSONObject(messageData[MP_NOTIFICATION_BRD_EXTRAS]!!)
            json.getStringOrNull(MP_NOTIFICATION_URL)
        } else {
            null
        }
}
