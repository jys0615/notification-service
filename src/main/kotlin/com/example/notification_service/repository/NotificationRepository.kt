package com.example.notification_service.repository

import com.example.notification_service.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NotificationRepository : JpaRepository<Notification, Long> {

    fun findByRecipientId(recipientId: Long): List<Notification>

    fun findByRecipientIdAndIsRead(recipientId: Long, isRead: Boolean): List<Notification>

    fun findByIdempotencyKey(idempotencyKey: String): Notification?
}
