package com.example.notification_service.sender

import com.example.notification_service.domain.Notification
import com.example.notification_service.domain.NotificationChannel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class EmailNotificationSender : NotificationSender {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun supports(channel: NotificationChannel) = channel == NotificationChannel.EMAIL

    override fun send(notification: Notification) {
        // 운영 환경에서는 실제 이메일 클라이언트(SES, SendGrid 등)로 교체
        log.info(
            "[EMAIL MOCK] id={} | recipientId={} | type={} | referenceId={}",
            notification.id,
            notification.recipientId,
            notification.notificationType,
            notification.referenceId
        )
        simulateExternalCall()
    }

    /**
     * 외부 서버 호출 시뮬레이션.
     * 10% 확률로 실패해서 재시도 정책을 테스트할 수 있게 합니다.
     */
    private fun simulateExternalCall() {
        if (Math.random() < 0.1) {
            throw RuntimeException("EMAIL 서버 일시 장애 (시뮬레이션)")
        }
    }
}
