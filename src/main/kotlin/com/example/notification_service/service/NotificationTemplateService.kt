package com.example.notification_service.service

import com.example.notification_service.domain.NotificationTemplate
import com.example.notification_service.dto.TemplateRequest
import com.example.notification_service.dto.TemplateResponse
import com.example.notification_service.repository.NotificationTemplateRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class NotificationTemplateService(
    private val templateRepository: NotificationTemplateRepository
) {
    fun getAll(): List<TemplateResponse> =
        templateRepository.findAll().map { TemplateResponse.from(it) }

    fun getById(id: Long): TemplateResponse {
        val template = templateRepository.findById(id)
            .orElseThrow { NoSuchElementException("템플릿을 찾을 수 없습니다. id=$id") }
        return TemplateResponse.from(template)
    }

    @Transactional
    fun create(request: TemplateRequest): TemplateResponse {
        // 동일 타입+채널 템플릿이 있으면 업데이트, 없으면 생성 (Upsert)
        val existing = templateRepository.findByNotificationTypeAndChannel(
            request.notificationType, request.channel
        )
        if (existing != null) {
            existing.update(request.titleTemplate, request.bodyTemplate)
            return TemplateResponse.from(templateRepository.save(existing))
        }

        val template = NotificationTemplate(
            notificationType = request.notificationType,
            channel = request.channel,
            titleTemplate = request.titleTemplate,
            bodyTemplate = request.bodyTemplate
        )
        return TemplateResponse.from(templateRepository.save(template))
    }

    @Transactional
    fun delete(id: Long) {
        if (!templateRepository.existsById(id)) {
            throw NoSuchElementException("템플릿을 찾을 수 없습니다. id=$id")
        }
        templateRepository.deleteById(id)
    }
}
