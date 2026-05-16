package com.example.notification_service.dto

import com.example.notification_service.domain.NotificationChannel
import com.example.notification_service.domain.NotificationType
import jakarta.validation.constraints.NotBlank

data class TemplateRequest(
    val notificationType: NotificationType,
    val channel: NotificationChannel,

    @field:NotBlank(message = "titleTemplate은 필수입니다")
    val titleTemplate: String,

    @field:NotBlank(message = "bodyTemplate은 필수입니다")
    val bodyTemplate: String
)
