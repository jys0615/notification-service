package com.example.notification_service.dto

import com.example.notification_service.domain.NotificationChannel
import com.example.notification_service.domain.NotificationType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

data class NotificationRequest(

    @field:Positive(message = "recipientId는 양수여야 합니다")
    val recipientId: Long,

    val notificationType: NotificationType,

    val channel: NotificationChannel,

    @field:NotBlank(message = "referenceId는 필수입니다")
    val referenceId: String,

    @field:NotBlank(message = "referenceType은 필수입니다")
    val referenceType: String,

    /** null이면 즉시 처리, 값이 있으면 해당 시각에 발송 */
    val scheduledAt: LocalDateTime? = null
)
