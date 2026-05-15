package com.example.notification_service.service

import com.example.notification_service.domain.Notification
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

    /**
     * 알림 발송 요청 등록.
     * 동일 idempotencyKey가 이미 존재하면 기존 알림을 그대로 반환 (멱등 처리).
     */
    @Transactional
    fun register(request: NotificationRequest): NotificationResponse {
        val idempotencyKey = Notification.buildIdempotencyKey(
            recipientId = request.recipientId,
            notificationType = request.notificationType,
            referenceId = request.referenceId,
            channel = request.channel
        )

        // 이미 동일 요청이 존재하면 기존 것을 반환 (중복 요청 방어)
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

    /** 단건 상태 조회 */
    fun getById(id: Long): NotificationResponse {
        val notification = notificationRepository.findById(id)
            .orElseThrow { NotificationNotFoundException(id) }
        return NotificationResponse.from(notification)
    }

    /** 수신자 기준 목록 조회 (읽음/안읽음 필터 선택) */
    fun listByRecipient(recipientId: Long, read: Boolean?): List<NotificationResponse> {
        val notifications = if (read != null) {
            notificationRepository.findByRecipientIdAndIsRead(recipientId, read)
        } else {
            notificationRepository.findByRecipientId(recipientId)
        }
        return notifications.map { NotificationResponse.from(it) }
    }
}
