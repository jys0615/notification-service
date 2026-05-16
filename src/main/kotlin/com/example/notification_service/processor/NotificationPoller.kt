package com.example.notification_service.processor

import com.example.notification_service.repository.NotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

/**
 * 주기적으로 DB를 폴링해서 처리 대상 알림을 꺼내 발송합니다.
 *
 * 현재는 @Scheduled 폴링 방식(Outbox Pattern)으로 구현합니다.
 * 운영 환경으로 전환할 때는 이 클래스를 Kafka Consumer / RabbitMQ Listener로 교체하면 됩니다.
 */
@Component
class NotificationPoller(
    private val notificationRepository: NotificationRepository,
    private val processorService: NotificationProcessorService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * fixedDelay: 이전 실행이 끝난 후 5초 뒤에 다시 실행.
     * fixedRate와 달리 처리 시간이 길어져도 중복 실행이 발생하지 않습니다.
     */
    @Scheduled(fixedDelay = 5000)
    fun poll() {
        val targets = notificationRepository.findProcessable(LocalDateTime.now())

        if (targets.isEmpty()) return

        log.info("[POLLER] 처리 대상 {} 건 발견", targets.size)
        targets.forEach { processorService.process(it) }
    }
}
