package com.example.notification_service.controller

import com.example.notification_service.domain.NotificationChannel
import com.example.notification_service.domain.NotificationStatus
import com.example.notification_service.domain.NotificationType
import com.example.notification_service.dto.NotificationRequest
import com.example.notification_service.dto.NotificationResponse
import com.example.notification_service.exception.GlobalExceptionHandler
import com.example.notification_service.exception.NotificationNotFoundException
import com.example.notification_service.service.NotificationService
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime

@WebMvcTest(NotificationController::class)
@Import(GlobalExceptionHandler::class)
@DisplayName("NotificationController 통합 테스트")
class NotificationControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockBean
    private lateinit var notificationService: NotificationService

    private fun buildResponse(
        id: Long = 1L,
        status: NotificationStatus = NotificationStatus.PENDING
    ) = NotificationResponse(
        id = id,
        recipientId = 1L,
        notificationType = NotificationType.ENROLLMENT_COMPLETED,
        channel = NotificationChannel.EMAIL,
        referenceId = "course-100",
        referenceType = "COURSE",
        status = status,
        retryCount = 0,
        failureReason = null,
        isRead = false,
        readAt = null,
        scheduledAt = null,
        sentAt = null,
        createdAt = LocalDateTime.now(),
        updatedAt = LocalDateTime.now()
    )

    @Test
    @DisplayName("POST /api/notifications - 유효한 요청이면 202 Accepted 반환")
    fun `register returns 202`() {
        val request = NotificationRequest(
            recipientId = 1L,
            notificationType = NotificationType.ENROLLMENT_COMPLETED,
            channel = NotificationChannel.EMAIL,
            referenceId = "course-100",
            referenceType = "COURSE"
        )
        `when`(notificationService.register(any())).thenReturn(buildResponse())

        mockMvc.perform(
            post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.recipientId").value(1))
    }

    @Test
    @DisplayName("POST /api/notifications - recipientId 누락 시 400 Bad Request 반환")
    fun `register with missing field returns 400`() {
        val invalidBody = """{"notificationType":"ENROLLMENT_COMPLETED","channel":"EMAIL","referenceId":"c1","referenceType":"COURSE"}"""

        mockMvc.perform(
            post("/api/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidBody)
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    @DisplayName("GET /api/notifications/{id} - 존재하는 ID는 200 OK 반환")
    fun `getById returns 200`() {
        `when`(notificationService.getById(1L)).thenReturn(buildResponse(id = 1L))

        mockMvc.perform(get("/api/notifications/1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(1))
    }

    @Test
    @DisplayName("GET /api/notifications/{id} - 존재하지 않는 ID는 404 반환")
    fun `getById not found returns 404`() {
        `when`(notificationService.getById(99L)).thenThrow(NotificationNotFoundException(99L))

        mockMvc.perform(get("/api/notifications/99"))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.status").value(404))
    }

    @Test
    @DisplayName("GET /api/notifications - 수신자 기준 목록 조회 200 OK")
    fun `listByRecipient returns 200`() {
        `when`(notificationService.listByRecipient(1L, null))
            .thenReturn(listOf(buildResponse(id = 1L), buildResponse(id = 2L)))

        mockMvc.perform(get("/api/notifications").param("recipientId", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
    }

    @Test
    @DisplayName("GET /api/notifications?read=false - 안읽은 알림만 조회")
    fun `listByRecipient with read filter returns filtered`() {
        `when`(notificationService.listByRecipient(1L, false))
            .thenReturn(listOf(buildResponse(id = 1L)))

        mockMvc.perform(
            get("/api/notifications")
                .param("recipientId", "1")
                .param("read", "false")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    @DisplayName("PATCH /api/notifications/{id}/read - 읽음 처리 200 OK")
    fun `markRead returns 200`() {
        val readResponse = buildResponse().copy(isRead = true)
        `when`(notificationService.markRead(1L)).thenReturn(readResponse)

        mockMvc.perform(patch("/api/notifications/1/read"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.isRead").value(true))
    }

    @Test
    @DisplayName("POST /api/notifications/{id}/retry - 수동 재시도 200 OK")
    fun `manualRetry returns 200`() {
        `when`(notificationService.manualRetry(1L)).thenReturn(buildResponse(status = NotificationStatus.PENDING))

        mockMvc.perform(post("/api/notifications/1/retry"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    @DisplayName("POST /api/notifications/{id}/retry - DEAD_LETTER가 아니면 409 Conflict 반환")
    fun `manualRetry on non-dead-letter returns 409`() {
        `when`(notificationService.manualRetry(1L))
            .thenThrow(IllegalStateException("DEAD_LETTER 상태의 알림만 수동 재시도할 수 있습니다"))

        mockMvc.perform(post("/api/notifications/1/retry"))
            .andExpect(status().isConflict)
    }

    private fun <T> any(): T = org.mockito.ArgumentMatchers.any()
}
