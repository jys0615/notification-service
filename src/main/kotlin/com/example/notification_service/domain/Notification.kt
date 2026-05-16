package com.example.notification_service.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "notifications",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_idempotency_key",
            columnNames = ["idempotency_key"]
        )
    ],
    indexes = [
        Index(name = "idx_recipient_id", columnList = "recipient_id"),
        Index(name = "idx_status", columnList = "status"),
        Index(name = "idx_scheduled_at", columnList = "scheduled_at")
    ]
)
class Notification(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "recipient_id", nullable = false)
    val recipientId: Long,

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    val notificationType: NotificationType,

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    val channel: NotificationChannel,

    /** 이벤트 ID, 강의 ID 등 참조 식별자 */
    @Column(name = "reference_id", nullable = false)
    val referenceId: String,

    /** referenceId의 대상 타입 (e.g. "COURSE", "PAYMENT") */
    @Column(name = "reference_type", nullable = false)
    val referenceType: String,

    /**
     * 중복 발송 방지 키.
     * recipientId + notificationType + referenceId + channel 조합으로 생성.
     * DB unique constraint로 중복 INSERT 자체를 차단.
     */
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    val idempotencyKey: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: NotificationStatus = NotificationStatus.PENDING,

    @Column(name = "retry_count", nullable = false)
    var retryCount: Int = 0,

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    var failureReason: String? = null,

    /** IN_APP 알림 읽음 여부 */
    @Column(name = "is_read", nullable = false)
    var isRead: Boolean = false,

    @Column(name = "read_at")
    var readAt: LocalDateTime? = null,

    /** 예약 발송 시각 (null이면 즉시 처리 대상) */
    @Column(name = "scheduled_at")
    val scheduledAt: LocalDateTime? = null,

    @Column(name = "sent_at")
    var sentAt: LocalDateTime? = null,

    @Column(name = "processing_started_at")
    var processingStartedAt: LocalDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    /**
     * 낙관적 락 버전.
     * 동시에 읽음 처리 요청이 여러 건 들어와도 한 건만 실제 UPDATE가 되고
     * 나머지는 ObjectOptimisticLockingFailureException을 받습니다.
     */
    @Version
    var version: Long = 0

) {
    companion object {
        fun buildIdempotencyKey(
            recipientId: Long,
            notificationType: NotificationType,
            referenceId: String,
            channel: NotificationChannel
        ): String = "$recipientId:$notificationType:$referenceId:$channel"
    }

    fun markProcessing() {
        status = NotificationStatus.PROCESSING
        processingStartedAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    fun markSent() {
        status = NotificationStatus.SENT
        sentAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    fun markFailed(reason: String, maxRetries: Int) {
        retryCount++
        failureReason = reason
        status = if (retryCount >= maxRetries) NotificationStatus.DEAD_LETTER else NotificationStatus.FAILED
        updatedAt = LocalDateTime.now()
    }

    fun resetForRetry() {
        status = NotificationStatus.PENDING
        processingStartedAt = null
        updatedAt = LocalDateTime.now()
    }

    fun markRead() {
        isRead = true
        readAt = LocalDateTime.now()
        updatedAt = LocalDateTime.now()
    }

    /**
     * 수동 재시도 (DEAD_LETTER → PENDING).
     * retryCount를 0으로 초기화해서 재시도 횟수를 완전히 리셋합니다.
     * 운영자가 명시적으로 재시도를 요청한 것이므로 새로운 시도로 간주합니다.
     */
    fun resetForManualRetry() {
        status = NotificationStatus.PENDING
        retryCount = 0
        failureReason = null
        processingStartedAt = null
        updatedAt = LocalDateTime.now()
    }
}
