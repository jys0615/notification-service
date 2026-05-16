package com.example.notification_service.processor

import com.example.notification_service.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

/**
 * PROCESSING 상태가 일정 시간 이상 지속된 알림을 PENDING으로 되돌립니다.
 *
 * 서버가 갑자기 재시작되거나 처리 도중 장애가 발생하면
 * 알림이 PROCESSING 상태로 영구히 묶일 수 있습니다.
 * 이 Recoverer가 주기적으로 이를 감지해 재처리 대상으로 되돌립니다.
 */
@Component
class StuckNotificationRecoverer(
    private val notificationRepository: NotificationRepository,
    @Value("\${notification.retry.stuck-threshold-minutes}") private val stuckThresholdMinutes: Long
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 60_000) // 1분마다 실행
    @Transactional
    fun recover() {
        val threshold = LocalDateTime.now().minusMinutes(stuckThresholdMinutes)
        val stuck = notificationRepository.findStuckProcessing(threshold)

        if (stuck.isEmpty()) return

        log.warn("[RECOVERER] Stuck 알림 {} 건 감지, PENDING으로 복구합니다", stuck.size)

        stuck.forEach { notification ->
            log.warn(
                "[RECOVERER] 복구 | id={} | processingStartedAt={}",
                notification.id, notification.processingStartedAt
            )
            notification.resetForRetry()
            notificationRepository.save(notification)
        }
    }
}
