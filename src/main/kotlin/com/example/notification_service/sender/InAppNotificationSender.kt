package com.example.notification_service.sender

import com.example.notification_service.domain.Notification
import com.example.notification_service.domain.NotificationChannel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class InAppNotificationSender : NotificationSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(channel: NotificationChannel) = channel == NotificationChannel.IN_APP

    override fun send(notification: Notification) {
        // 운영 환경에서는 WebSocket 브로드캐스트 / FCM 푸시로 교체
        log.info(
            "[IN_APP MOCK] id={} | recipientId={} | type={} | referenceId={}",
            notification.id,
            notification.recipientId,
            notification.notificationType,
            notification.referenceId
        )
    }
}
