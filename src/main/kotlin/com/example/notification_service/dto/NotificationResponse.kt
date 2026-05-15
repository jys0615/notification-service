package com.example.notification_service.dto

import com.example.notification_service.domain.Notification
import com.example.notification_service.domain.NotificationChannel
import com.example.notification_service.domain.NotificationStatus
import com.example.notification_service.domain.NotificationType
import java.time.LocalDateTime

data class NotificationResponse(
    val id: Long,
    val recipientId: Long,
    val notificationType: NotificationType,
    val channel: NotificationChannel,
    val referenceId: String,
    val referenceType: String,
    val status: NotificationStatus,
    val retryCount: Int,
    val failureReason: String?,
    val isRead: Boolean,
    val readAt: LocalDateTime?,
    val scheduledAt: LocalDateTime?,
    val sentAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(notification: Notification) = NotificationResponse(
            id = notification.id,
            recipientId = notification.recipientId,
            notificationType = notification.notificationType,
            channel = notification.channel,
            referenceId = notification.referenceId,
            referenceType = notification.referenceType,
            status = notification.status,
            retryCount = notification.retryCount,
            failureReason = notification.failureReason,
            isRead = notification.isRead,
            readAt = notification.readAt,
            scheduledAt = notification.scheduledAt,
            sentAt = notification.sentAt,
            createdAt = notification.createdAt,
            updatedAt = notification.updatedAt
        )
    }
}
