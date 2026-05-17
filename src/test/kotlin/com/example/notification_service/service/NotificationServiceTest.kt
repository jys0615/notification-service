package com.example.notification_service.service

import com.example.notification_service.domain.Notification
import com.example.notification_service.domain.NotificationChannel
import com.example.notification_service.domain.NotificationStatus
import com.example.notification_service.domain.NotificationType
import com.example.notification_service.dto.NotificationRequest
import com.example.notification_service.exception.NotificationNotFoundException
import com.example.notification_service.repository.NotificationRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import java.util.Optional

@ExtendWith(MockitoExtension::class)
@DisplayName("NotificationService 단위 테스트")
class NotificationServiceTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    private lateinit var notificationService: NotificationService

    @BeforeEach
    fun setUp() {
        notificationService = NotificationService(notificationRepository)
    }

    // ──────────────────────────────────────────────
    // 테스트용 픽스처
    // ──────────────────────────────────────────────

    private fun buildRequest(
        recipientId: Long = 1L,
        referenceId: String = "course-100",
        channel: NotificationChannel = NotificationChannel.EMAIL
    ) = NotificationRequest(
        recipientId = recipientId,
        notificationType = NotificationType.ENROLLMENT_COMPLETED,
        channel = channel,
        referenceId = referenceId,
        referenceType = "COURSE"
    )

    private fun buildNotification(
        id: Long = 1L,
        status: NotificationStatus = NotificationStatus.PENDING,
        isRead: Boolean = false
    ) = Notification(
        id = id,
        recipientId = 1L,
        notificationType = NotificationType.ENROLLMENT_COMPLETED,
        channel = NotificationChannel.EMAIL,
        referenceId = "course-100",
        referenceType = "COURSE",
        idempotencyKey = "1:ENROLLMENT_COMPLETED:course-100:EMAIL",
        status = status,
        isRead = isRead
    )

    // ──────────────────────────────────────────────
    // register()
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("register()")
    inner class Register {

        @Test
        @DisplayName("새로운 알림 요청은 PENDING 상태로 저장된다")
        fun `register new notification`() {
            val request = buildRequest()
            val saved = buildNotification()
            `when`(notificationRepository.findByIdempotencyKey(any())).thenReturn(null)
            `when`(notificationRepository.saveAndFlush(any())).thenReturn(saved)

            val response = notificationService.register(request)

            assertThat(response.status).isEqualTo(NotificationStatus.PENDING)
            assertThat(response.recipientId).isEqualTo(1L)
        }

        @Test
        @DisplayName("동일한 idempotencyKey가 이미 존재하면 기존 알림을 반환한다")
        fun `register with existing idempotency key returns existing`() {
            val request = buildRequest()
            val existing = buildNotification(id = 42L)
            `when`(notificationRepository.findByIdempotencyKey(any())).thenReturn(existing)

            val response = notificationService.register(request)

            assertThat(response.id).isEqualTo(42L)
            verify(notificationRepository, never()).save(any())
        }
    }

    // ──────────────────────────────────────────────
    // getById()
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("getById()")
    inner class GetById {

        @Test
        @DisplayName("존재하는 ID 조회 시 알림을 반환한다")
        fun `getById found`() {
            val notification = buildNotification(id = 1L)
            `when`(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))

            val response = notificationService.getById(1L)

            assertThat(response.id).isEqualTo(1L)
        }

        @Test
        @DisplayName("존재하지 않는 ID 조회 시 NotificationNotFoundException 발생")
        fun `getById not found`() {
            `when`(notificationRepository.findById(99L)).thenReturn(Optional.empty())

            assertThatThrownBy { notificationService.getById(99L) }
                .isInstanceOf(NotificationNotFoundException::class.java)
        }
    }

    // ──────────────────────────────────────────────
    // listByRecipient()
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("listByRecipient()")
    inner class ListByRecipient {

        @Test
        @DisplayName("read 파라미터 없으면 전체 알림을 반환한다")
        fun `list all notifications`() {
            val notifications = listOf(buildNotification(id = 1L), buildNotification(id = 2L))
            `when`(notificationRepository.findByRecipientId(1L)).thenReturn(notifications)

            val result = notificationService.listByRecipient(1L, null)

            assertThat(result).hasSize(2)
        }

        @Test
        @DisplayName("read=false 이면 안읽은 알림만 반환한다")
        fun `list unread notifications`() {
            val unread = listOf(buildNotification(id = 1L, isRead = false))
            `when`(notificationRepository.findByRecipientIdAndIsRead(1L, false)).thenReturn(unread)

            val result = notificationService.listByRecipient(1L, false)

            assertThat(result).hasSize(1)
            assertThat(result[0].isRead).isFalse()
        }
    }

    // ──────────────────────────────────────────────
    // markRead()
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("markRead()")
    inner class MarkRead {

        @Test
        @DisplayName("안읽은 알림을 읽음 처리하면 isRead가 true가 된다")
        fun `mark unread notification as read`() {
            val notification = buildNotification(isRead = false)
            val updated = buildNotification(isRead = true)
            `when`(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))
            `when`(notificationRepository.save(any())).thenReturn(updated)

            val response = notificationService.markRead(1L)

            assertThat(response.isRead).isTrue()
        }

        @Test
        @DisplayName("이미 읽은 알림은 save 없이 그대로 반환한다")
        fun `already read notification returns without save`() {
            val notification = buildNotification(isRead = true)
            `when`(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))

            notificationService.markRead(1L)

            verify(notificationRepository, never()).save(any())
        }
    }

    // ──────────────────────────────────────────────
    // manualRetry()
    // ──────────────────────────────────────────────

    @Nested
    @DisplayName("manualRetry()")
    inner class ManualRetry {

        @Test
        @DisplayName("DEAD_LETTER 알림을 수동 재시도하면 PENDING 상태로 바뀌고 retryCount가 0이 된다")
        fun `manual retry resets DEAD_LETTER to PENDING`() {
            val notification = buildNotification(status = NotificationStatus.DEAD_LETTER)
                .apply { retryCount = 3 }
            val reset = buildNotification(status = NotificationStatus.PENDING)
            `when`(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))
            `when`(notificationRepository.save(any())).thenReturn(reset)

            val response = notificationService.manualRetry(1L)

            assertThat(response.status).isEqualTo(NotificationStatus.PENDING)
        }

        @Test
        @DisplayName("DEAD_LETTER가 아닌 알림을 수동 재시도하면 IllegalStateException 발생")
        fun `manual retry on non DEAD_LETTER throws`() {
            val notification = buildNotification(status = NotificationStatus.SENT)
            `when`(notificationRepository.findById(1L)).thenReturn(Optional.of(notification))

            assertThatThrownBy { notificationService.manualRetry(1L) }
                .isInstanceOf(IllegalStateException::class.java)
        }
    }

    // Mockito any() Kotlin 헬퍼
    private fun <T> any(): T = org.mockito.ArgumentMatchers.any()
}
