package com.example.notification_service.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(
    name = "notification_templates",
    uniqueConstraints = [
        UniqueConstraint(
            name = "uk_template_type_channel",
            columnNames = ["notification_type", "channel"]
        )
    ]
)
class NotificationTemplate(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false)
    val notificationType: NotificationType,

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    val channel: NotificationChannel,

    @Column(name = "title_template", nullable = false)
    var titleTemplate: String,

    @Column(name = "body_template", nullable = false, columnDefinition = "TEXT")
    var bodyTemplate: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()

) {
    /**
     * 플레이스홀더를 알림 데이터로 치환합니다.
     * 사용 가능한 플레이스홀더: {recipientId}, {referenceId}, {referenceType}, {notificationType}
     */
    fun renderTitle(notification: Notification): String = render(titleTemplate, notification)
    fun renderBody(notification: Notification): String = render(bodyTemplate, notification)

    private fun render(template: String, notification: Notification): String =
        template
            .replace("{recipientId}", notification.recipientId.toString())
            .replace("{referenceId}", notification.referenceId)
            .replace("{referenceType}", notification.referenceType)
            .replace("{notificationType}", notification.notificationType.name)

    fun update(titleTemplate: String, bodyTemplate: String) {
        this.titleTemplate = titleTemplate
        this.bodyTemplate = bodyTemplate
        this.updatedAt = LocalDateTime.now()
    }
}
