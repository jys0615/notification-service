package com.example.notification_service.sender

import com.example.notification_service.domain.Notification
import com.example.notification_service.domain.NotificationChannel

/**
 * 실제 발송 구현체를 교체 가능하게 하는 인터페이스.
 * 운영 환경에서는 실제 이메일 클라이언트 / 푸시 서버로 교체.
 */
interface NotificationSender {
    fun send(notification: Notification)
    fun supports(channel: NotificationChannel): Boolean
}
