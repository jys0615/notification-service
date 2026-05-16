package com.example.notification_service.repository

import com.example.notification_service.domain.NotificationChannel
import com.example.notification_service.domain.NotificationTemplate
import com.example.notification_service.domain.NotificationType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NotificationTemplateRepository : JpaRepository<NotificationTemplate, Long> {
    fun findByNotificationTypeAndChannel(
        notificationType: NotificationType,
        channel: NotificationChannel
    ): NotificationTemplate?
}
