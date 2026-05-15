package com.example.notification_service.controller

import com.example.notification_service.dto.NotificationRequest
import com.example.notification_service.dto.NotificationResponse
import com.example.notification_service.service.NotificationService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notifications")
class NotificationController(
    private val notificationService: NotificationService
) {

    /** 알림 발송 요청 등록 */
    @PostMapping
    fun register(
        @RequestBody @Valid request: NotificationRequest
    ): ResponseEntity<NotificationResponse> {
        val response = notificationService.register(request)
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response)
    }

    /** 단건 상태 조회 */
    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<NotificationResponse> {
        return ResponseEntity.ok(notificationService.getById(id))
    }

    /** 수신자 기준 목록 조회 */
    @GetMapping
    fun listByRecipient(
        @RequestParam recipientId: Long,
        @RequestParam(required = false) read: Boolean?
    ): ResponseEntity<List<NotificationResponse>> {
        return ResponseEntity.ok(notificationService.listByRecipient(recipientId, read))
    }
}
