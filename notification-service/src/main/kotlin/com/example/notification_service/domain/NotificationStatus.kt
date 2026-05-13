package com.example.notification_service.domain

enum class NotificationStatus {
    /** 요청 접수 완료, 아직 처리 전 */
    PENDING,

    /** 비동기 워커가 처리 중 */
    PROCESSING,

    /** 발송 성공 */
    SENT,

    /** 발송 실패 (재시도 가능) */
    FAILED,

    /** 최대 재시도 초과 — 영구 실패 */
    DEAD_LETTER
}
