package com.example.notification_service.processor

import com.example.notification_service.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 주기적으로 DB를 폴링해서 처리 대상 알림 ID를 꺼내 발송합니다.
 *
 * 운영 환경으로 전환할 때는 이 클래스를 Kafka Consumer / RabbitMQ Listener로 교체하면 됩니다.
 * ProcessorService의 인터페이스는 변경할 필요가 없습니다.
 */
@Component
class NotificationPoller(
    private val notificationRepository: NotificationRepository,
    private val processorService: NotificationProcessorService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 5000)
    fun poll() {
        val ids = notificationRepository.findProcessableIds(LocalDateTime.now())

        if (ids.isEmpty()) return

        log.info("[POLLER] 처리 대상 {} 건 발견", ids.size)
        ids.forEach { processorService.process(it) }
    }
}
