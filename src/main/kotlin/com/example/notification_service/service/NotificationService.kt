package com.example.notification_service.service

import com.example.notification_service.domain.Notification
import com.example.notification_service.domain.NotificationStatus
import com.example.notification_service.dto.NotificationRequest
import com.example.notification_service.dto.NotificationResponse
import com.example.notification_service.exception.NotificationNotFoundException
import com.example.notification_service.repository.NotificationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationService(
    private val notificationRepository: NotificationRepository
) {

    @Transactional
    fun register(request: NotificationRequest): NotificationResponse {
        val idempotencyKey = Notification.buildIdempotencyKey(
            recipientId = request.recipientId,
            notificationType = request.notificationType,
            referenceId = request.referenceId,
            channel = request.channel
        )

        notificationRepository.findByIdempotencyKey(idempotencyKey)
            ?.let { return NotificationResponse.from(it) }

        val notification = Notification(
            recipientId = request.recipientId,
            notificationType = request.notificationType,
            channel = request.channel,
            referenceId = request.referenceId,
            referenceType = request.referenceType,
            idempotencyKey = idempotencyKey,
            scheduledAt = request.scheduledAt
        )

        return NotificationResponse.from(notificationRepository.save(notification))
    }

    fun getById(id: Long): NotificationResponse {
        val notification = notificationRepository.findById(id)
            .orElseThrow { NotificationNotFoundException(id) }
        return NotificationResponse.from(notification)
    }

    fun listByRecipient(recipientId: Long, read: Boolean?): List<NotificationResponse> {
        val notifications = if (read != null) {
            notificationRepository.findByRecipientIdAndIsRead(recipientId, read)
        } else {
            notificationRepository.findByRecipientId(recipientId)
        }
        return notifications.map { NotificationResponse.from(it) }
    }

    /**
     * 읽음 처리.
     * @Version 낙관적 락으로 동시 요청을 제어합니다.
     * 여러 기기에서 동시에 읽음 처리가 들어와도 최초 1건만 UPDATE되고,
     * 나머지는 ObjectOptimisticLockingFailureException → GlobalExceptionHandler에서 200 반환.
     */
    @Transactional
    fun markRead(id: Long): NotificationResponse {
        val notification = notificationRepository.findById(id)
            .orElseThrow { NotificationNotFoundException(id) }

        if (notification.isRead) return NotificationResponse.from(notification)

        notification.markRead()
        return NotificationResponse.from(notificationRepository.save(notification))
    }

    /**
     * DEAD_LETTER 알림 수동 재시도.
     * retryCount를 0으로 초기화합니다.
     * 운영자가 명시적으로 재시도를 요청한 것이므로 새 시도로 간주합니다.
     */
    @Transactional
    fun manualRetry(id: Long): NotificationResponse {
        val notification = notificationRepository.findById(id)
            .orElseThrow { NotificationNotFoundException(id) }

        if (notification.status != NotificationStatus.DEAD_LETTER) {
            throw IllegalStateException("DEAD_LETTER 상태의 알림만 수동 재시도할 수 있습니다. 현재 상태: ${notification.status}")
        }

        notification.resetForManualRetry()
        return NotificationResponse.from(notificationRepository.save(notification))
    }
}
