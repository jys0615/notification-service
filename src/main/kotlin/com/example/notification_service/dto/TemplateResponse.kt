package com.example.notification_service.dto

import com.example.notification_service.domain.NotificationChannel
import com.example.notification_service.domain.NotificationTemplate
import com.example.notification_service.domain.NotificationType
import java.time.LocalDateTime

data class TemplateResponse(
    val id: Long,
    val notificationType: NotificationType,
    val channel: NotificationChannel,
    val titleTemplate: String,
    val bodyTemplate: String,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(template: NotificationTemplate) = TemplateResponse(
            id = template.id,
            notificationType = template.notificationType,
            channel = template.channel,
            titleTemplate = template.titleTemplate,
            bodyTemplate = template.bodyTemplate,
            createdAt = template.createdAt,
            updatedAt = template.updatedAt
        )
    }
}
