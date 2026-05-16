package com.example.notification_service.processor

import com.example.notification_service.domain.Notification
import com.example.notification_service.repository.NotificationRepository
import com.example.notification_service.sender.NotificationSender
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class NotificationProcessorService(
    private val notificationRepository: NotificationRepository,
    private val senders: List<NotificationSender>,
    @Value("\${notification.retry.max-count}") private val maxRetries: Int
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 알림 1건 처리. 트랜잭션을 분리해서 각 건의 성공/실패가 독립적으로 커밋됩니다.
     */
    @Transactional
    fun process(notification: Notification) {
        notification.markProcessing()
        notificationRepository.save(notification)

        try {
            val sender = senders.find { it.supports(notification.channel) }
                ?: throw IllegalArgumentException("지원하지 않는 채널: ${notification.channel}")

            sender.send(notification)
            notification.markSent()
            log.info("[PROCESSOR] 발송 성공 | id={} | channel={}", notification.id, notification.channel)

        } catch (e: Exception) {
            notification.markFailed(e.message ?: "알 수 없는 오류", maxRetries)
            log.warn(
                "[PROCESSOR] 발송 실패 | id={} | retryCount={} | status={} | reason={}",
                notification.id, notification.retryCount, notification.status, e.message
            )
        }

        notificationRepository.save(notification)
    }
}
