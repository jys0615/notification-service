package com.example.notification_service.processor

import com.example.notification_service.domain.Notification
import com.example.notification_service.domain.NotificationChannel
import com.example.notification_service.domain.NotificationStatus
import com.example.notification_service.domain.NotificationType
import com.example.notification_service.repository.NotificationRepository
import com.example.notification_service.sender.NotificationSender
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.junit.jupiter.MockitoSettings
import org.mockito.quality.Strictness

@ExtendWith(MockitoExtension::class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationProcessorService 단위 테스트")
class NotificationProcessorServiceTest {

    @Mock
    private lateinit var notificationRepository: NotificationRepository

    @Mock
    private lateinit var mockSender: NotificationSender

    private lateinit var processorService: NotificationProcessorService

    private val maxRetries = 3

    @BeforeEach
    fun setUp() {
        `when`(mockSender.supports(NotificationChannel.EMAIL)).thenReturn(true)
        processorService = NotificationProcessorService(
            notificationRepository = notificationRepository,
            senders = listOf(mockSender),
            maxRetries = maxRetries
        )
    }

    private fun buildNotification(
        id: Long = 1L,
        status: NotificationStatus = NotificationStatus.PENDING,
        retryCount: Int = 0
    ) = Notification(
        id = id,
        recipientId = 1L,
        notificationType = NotificationType.ENROLLMENT_COMPLETED,
        channel = NotificationChannel.EMAIL,
        referenceId = "course-100",
        referenceType = "COURSE",
        idempotencyKey = "key",
        status = status,
        retryCount = retryCount
    )

    @Test
    @DisplayName("알림이 없으면 아무것도 하지 않는다")
    fun `process with non-existent id does nothing`() {
        `when`(notificationRepository.findByIdForUpdate(99L)).thenReturn(null)

        processorService.process(99L)

        verify(notificationRepository, never()).save(any())
    }

    @Test
    @DisplayName("이미 PROCESSING 상태인 알림은 스킵한다")
    fun `process already processing notification is skipped`() {
        val notification = buildNotification(status = NotificationStatus.PROCESSING)
        `when`(notificationRepository.findByIdForUpdate(1L)).thenReturn(notification)

        processorService.process(1L)

        verify(notificationRepository, never()).save(any())
    }

    @Test
    @DisplayName("발송 성공 시 상태가 SENT로 바뀐다")
    fun `process success sets status to SENT`() {
        val notification = buildNotification(status = NotificationStatus.PENDING)
        `when`(notificationRepository.findByIdForUpdate(1L)).thenReturn(notification)
        `when`(notificationRepository.save(any())).thenReturn(notification)

        processorService.process(1L)

        assertThat(notification.status).isEqualTo(NotificationStatus.SENT)
        assertThat(notification.sentAt).isNotNull()
    }

    @Test
    @DisplayName("발송 실패 시 상태가 FAILED로 바뀌고 실패 사유가 기록된다")
    fun `process failure sets status to FAILED`() {
        val notification = buildNotification(status = NotificationStatus.PENDING, retryCount = 0)
        `when`(notificationRepository.findByIdForUpdate(1L)).thenReturn(notification)
        `when`(notificationRepository.save(any())).thenReturn(notification)
        doThrow(RuntimeException("네트워크 오류")).`when`(mockSender).send(any())

        processorService.process(1L)

        assertThat(notification.status).isEqualTo(NotificationStatus.FAILED)
        assertThat(notification.failureReason).contains("네트워크 오류")
        assertThat(notification.retryCount).isEqualTo(1)
    }

    @Test
    @DisplayName("최대 재시도 횟수 초과 시 상태가 DEAD_LETTER로 바뀐다")
    fun `process failure at max retries sets status to DEAD_LETTER`() {
        // retryCount가 이미 maxRetries - 1인 상태에서 한 번 더 실패
        val notification = buildNotification(status = NotificationStatus.FAILED, retryCount = maxRetries - 1)
        `when`(notificationRepository.findByIdForUpdate(1L)).thenReturn(notification)
        `when`(notificationRepository.save(any())).thenReturn(notification)
        doThrow(RuntimeException("외부 서버 장애")).`when`(mockSender).send(any())

        processorService.process(1L)

        assertThat(notification.status).isEqualTo(NotificationStatus.DEAD_LETTER)
        assertThat(notification.retryCount).isEqualTo(maxRetries)
    }

    private fun <T> any(): T = org.mockito.ArgumentMatchers.any()
}
