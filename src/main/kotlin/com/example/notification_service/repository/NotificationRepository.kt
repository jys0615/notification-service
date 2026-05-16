package com.example.notification_service.repository

import com.example.notification_service.domain.Notification
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByRecipientId(recipientId: Long): List<Notification>

    fun findByRecipientIdAndIsRead(recipientId: Long, isRead: Boolean): List<Notification>

    fun findByIdempotencyKey(idempotencyKey: String): Notification?

    /**
     * 처리 대상 알림 ID 목록 조회 (락 없이 가볍게).
     * 실제 락은 processById() 안에서 건별로 겁니다.
     */
    @Query("""
        SELECT n.id FROM Notification n
        WHERE n.status IN ('PENDING', 'FAILED')
        AND (n.scheduledAt IS NULL OR n.scheduledAt <= :now)
        ORDER BY n.createdAt ASC
    """)
    fun findProcessableIds(@Param("now") now: LocalDateTime): List<Long>

    /**
     * 비관적 락(SELECT FOR UPDATE)으로 단건 조회.
     * 다중 인스턴스 환경에서 동일 알림이 중복 처리되지 않도록 합니다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT n FROM Notification n WHERE n.id = :id")
    fun findByIdForUpdate(@Param("id") id: Long): Notification?

    /**
     * PROCESSING 상태로 일정 시간 이상 머문 알림 조회 (Stuck 복구용).
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status = 'PROCESSING'
        AND n.processingStartedAt < :threshold
    """)
    fun findStuckProcessing(@Param("threshold") threshold: LocalDateTime): List<Notification>
}
