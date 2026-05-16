package com.example.notification_service.repository

import com.example.notification_service.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByRecipientId(recipientId: Long): List                                  <Notification>

    fun findByRecipientIdAndIsRead(recipientId: Long, isRead: Boolean): List<Notification>

    fun findByIdempotencyKey(idempotencyKey: String): Notification?

    /**
     * 처리 대상 알림 조회.
     * PENDING / FAILED 상태이면서 예약 시각이 지났거나 즉시 발송 대상인 것만 가져옵니다.
     */
    @Query("""
        SELECT n FROM Notification n
        WHERE n.status IN ('PENDING', 'FAILED')
        AND (n.scheduledAt IS NULL OR n.scheduledAt <= :now)
        ORDER BY n.createdAt ASC
    """)
    fun findProcessable(@Param("now") now: LocalDateTime): List<Notification>
} 
