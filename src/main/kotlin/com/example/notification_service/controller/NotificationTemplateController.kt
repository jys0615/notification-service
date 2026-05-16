package com.example.notification_service.controller

import com.example.notification_service.dto.TemplateRequest
import com.example.notification_service.dto.TemplateResponse
import com.example.notification_service.service.NotificationTemplateService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/templates")
class NotificationTemplateController(
    private val templateService: NotificationTemplateService
) {
    @GetMapping
    fun getAll(): ResponseEntity<List<TemplateResponse>> =
        ResponseEntity.ok(templateService.getAll())

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<TemplateResponse> =
        ResponseEntity.ok(templateService.getById(id))

    @PostMapping
    fun create(
        @RequestBody @Valid request: TemplateRequest
    ): ResponseEntity<TemplateResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(templateService.create(request))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long): ResponseEntity<Void> {
        templateService.delete(id)
        return ResponseEntity.noContent().build()
    }
}
