package com.example.notification_service.processor

import com.example.notification_service.domain.NotificationStatus
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
     * 알림 1건 처리.
     *
     * [다중 인스턴스 안전성]
     * findByIdForUpdate()로 SELECT FOR UPDATE를 걸어 행 락을 획득합니다.
     * 같은 ID를 다른 인스턴스가 처리하려 하면 락이 풀릴 때까지 대기하고,
     * 락 해제 후 상태를 재확인해서 이미 처리된 건은 건너뜁니다.
     */
    @Transactional
    fun process(id: Long) {
        val notification = notificationRepository.findByIdForUpdate(id) ?: return

        // 락 획득 후 상태 재검증 — 다른 인스턴스가 이미 처리했을 수 있음
        if (notification.status !in listOf(NotificationStatus.PENDING, NotificationStatus.FAILED)) {
            log.debug("[PROCESSOR] 이미 처리된 알림, 스킵 | id={} | status={}", id, notification.status)
            return
        }

        notification.markProcessing()
        notificationRepository.save(notification)

        try {
            val sender = senders.find { it.supports(notification.channel) }
                ?: throw IllegalArgumentException("지원하지 않는 채널: ${notification.channel}")

            sender.send(notification)
            notification.markSent()
            log.info("[PROCESSOR] 발송 성공 | id={} | channel={}", id, notification.channel)

        } catch (e: Exception) {
            notification.markFailed(e.message ?: "알 수 없는 오류", maxRetries)
            log.warn(
                "[PROCESSOR] 발송 실패 | id={} | retryCount={} | status={} | reason={}",
                id, notification.retryCount, notification.status, e.message
            )
        }

        notificationRepository.save(notification)
    }
}
