# notification-service

알림 발송 시스템 — 수강 신청 완료, 결제 확정, 강의 시작 D-1 등의 이벤트를 사용자에게 이메일/인앱 알림으로 전달합니다.

---

## 프로젝트 개요

- 알림 발송 요청은 즉시 처리하지 않고 DB에 `PENDING` 상태로 저장한 뒤, 비동기 폴러가 주기적으로 꺼내 처리합니다 (Outbox Pattern).
- 멱등성 키 기반 중복 발송 방지와 비관적 락 기반 다중 인스턴스 안전성을 제공합니다.
- 실제 이메일 서버나 메시지 브로커 없이 동작하며, 운영 환경으로 교체 가능한 구조입니다.

---

## 기술 스택

| 항목 | 선택 | 이유 |
|---|---|---|
| Language | Kotlin | 간결한 문법, null-safety, data class |
| Framework | Spring Boot 3.5 | 표준 백엔드 프레임워크, 풍부한 생태계 |
| ORM | Spring Data JPA (Hibernate) | 쿼리 메서드 자동 생성, 상태 관리 편의성 |
| DB | H2 (in-memory) | 별도 설치 없이 즉시 실행 가능 |
| 비동기 | `@Scheduled` + DB Outbox | 브로커 없이 Kafka/RabbitMQ 전환 가능한 구조 |
| 동시성 | 비관적 락 + 낙관적 락 | 다중 인스턴스 처리 및 읽음 처리 경합 제어 |
| Test | JUnit 5, Mockito, MockMvc | 단위·통합 테스트 |

---

## 실행 방법

### 로컬

```bash
./gradlew bootRun
```

### Docker

```bash
docker build -t notification-service .
docker run -p 8080:8080 notification-service
```

서버 포트: `http://localhost:8080`  
H2 콘솔: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:notificationdb`
- Username: `sa` / Password: (없음)

---

## 요구사항 해석 및 가정

| 요구사항 | 해석 및 가정 |
|---|---|
| 알림 처리 실패가 비즈니스 트랜잭션에 영향을 주면 안 됨 | API는 접수만 하고 발송은 비동기 처리. 발송 실패는 DB에 기록하고 재시도 |
| 예외를 단순히 무시해서는 안 됨 | 실패 시 `failureReason` 기록, 재시도 정책 적용, 최종 실패 시 `DEAD_LETTER` 보관 |
| 중복 발송 방지 | `(recipientId, notificationType, referenceId, channel)` 복합 unique key. DB 레벨에서 차단 |
| 다중 인스턴스 중복 처리 방지 | `SELECT FOR UPDATE` 비관적 락으로 동일 알림을 두 인스턴스가 동시에 처리하지 않도록 보장 |
| 서버 재시작 후 미처리 알림 복구 | 알림을 DB에 저장하므로 재시작 후 폴러가 `PENDING` 상태를 재처리 |

---

## 설계 결정과 이유

### 1. Outbox Pattern (DB 폴링)

API 스레드는 알림을 `PENDING`으로 저장하고 즉시 반환합니다.  
`@Scheduled` 폴러가 5초마다 DB를 조회해서 처리합니다.  
운영 전환 시 `NotificationPoller`를 Kafka Consumer로 교체하면 되며, `NotificationSender` 인터페이스 덕분에 나머지 코드는 변경이 불필요합니다.

### 2. 비관적 락 (SELECT FOR UPDATE)

다중 인스턴스 환경에서 동일 알림의 중복 처리를 방지합니다.  
폴러가 ID 목록만 조회(락 없음) → 처리 시 `findByIdForUpdate()`로 행 락 획득 → 상태 재검증 → 처리

### 3. 낙관적 락 (@Version)

읽음 처리는 여러 기기에서 동시에 요청이 올 수 있습니다.  
`@Version`으로 동시 UPDATE를 제어하며, 첫 번째 요청만 성공하고 나머지는 200으로 응답합니다. 어차피 결과는 동일하게 "읽음"이기 때문입니다.

### 4. Rich Domain Model

상태 전이 로직(`markProcessing()`, `markFailed()` 등)을 엔티티 내부에 캡슐화했습니다.  
외부에서 `status`를 직접 변경하지 못하게 하여 부수 작업 누락을 방지합니다.

### 5. 수동 재시도 시 retryCount 초기화

`DEAD_LETTER` 알림을 수동 재시도할 때 `retryCount`를 0으로 초기화합니다.  
운영자가 명시적으로 재시도를 요청한 것이므로 새로운 시도로 간주합니다.  
초기화 없이 진행하면 첫 실패에 즉시 `DEAD_LETTER`로 돌아가 의미가 없습니다.

---

## 알림 상태 전이

```
PENDING
  ↓ (폴러 획득)
PROCESSING
  ↓ 성공          ↓ 실패 (retryCount < maxRetries)
SENT           FAILED
                  ↓ (다음 폴링에서 재시도)
               PENDING → PROCESSING → ...
                  ↓ 실패 (retryCount >= maxRetries)
               DEAD_LETTER
                  ↓ (운영자 수동 재시도)
               PENDING (retryCount = 0으로 초기화)
```

---

## 비동기 처리 구조 및 재시도 정책

### 처리 흐름

```
[API 스레드]
  POST /api/notifications
    → DB에 PENDING 저장 후 202 Accepted 즉시 반환

[Scheduler 스레드 — 5초마다]
  NotificationPoller.poll()
    → findProcessableIds(): PENDING/FAILED + scheduledAt 조건 필터
    → 각 ID에 대해 NotificationProcessorService.process(id) 호출
        → findByIdForUpdate(): SELECT FOR UPDATE (비관적 락)
        → 상태 재검증 (PENDING/FAILED인지 확인)
        → markProcessing() → save()
        → NotificationSender.send() (Mock 또는 실제 구현체)
        → markSent() 또는 markFailed() → save()

[Scheduler 스레드 — 1분마다]
  StuckNotificationRecoverer.recover()
    → processingStartedAt < 10분 전인 PROCESSING 알림 조회
    → resetForRetry() → PENDING으로 복구
```

### 재시도 정책

| 항목 | 값 |
|---|---|
| 최대 재시도 횟수 | 3회 (`notification.retry.max-count`) |
| 재시도 간격 | 폴러 주기(5초)에 따라 자연스럽게 재시도 |
| 최종 실패 처리 | `DEAD_LETTER` 상태로 보관 |
| Stuck 복구 임계값 | 10분 (`notification.retry.stuck-threshold-minutes`) |
| 수동 재시도 | `POST /api/notifications/{id}/retry` (DEAD_LETTER만 가능) |

### 운영 환경 전환

현재 `@Scheduled` 폴링 → Kafka 전환 시:
1. `NotificationPoller`를 `@KafkaListener`로 교체
2. 알림 등록 시 Kafka 토픽에 발행
3. `NotificationProcessorService`, `NotificationSender` 구현체는 변경 불필요

---

## API 목록 및 예시

| 메서드 | 경로 | 설명 | 응답 |
|---|---|---|---|
| POST | `/api/notifications` | 알림 등록 | 202 |
| GET | `/api/notifications/{id}` | 단건 조회 | 200 |
| GET | `/api/notifications?recipientId=&read=` | 목록 조회 | 200 |
| PATCH | `/api/notifications/{id}/read` | 읽음 처리 | 200 |
| POST | `/api/notifications/{id}/retry` | 수동 재시도 | 200 |
| GET | `/api/templates` | 템플릿 전체 조회 | 200 |
| GET | `/api/templates/{id}` | 템플릿 단건 조회 | 200 |
| POST | `/api/templates` | 템플릿 생성/수정 | 201 |
| DELETE | `/api/templates/{id}` | 템플릿 삭제 | 204 |

### 알림 등록 요청

```json
POST /api/notifications
{
  "recipientId": 1,
  "notificationType": "ENROLLMENT_COMPLETED",
  "channel": "EMAIL",
  "referenceId": "course-100",
  "referenceType": "COURSE",
  "scheduledAt": null
}
```

응답 (202 Accepted):
```json
{
  "id": 1,
  "recipientId": 1,
  "notificationType": "ENROLLMENT_COMPLETED",
  "channel": "EMAIL",
  "status": "PENDING",
  "retryCount": 0,
  "isRead": false,
  "createdAt": "2026-05-16T12:00:00"
}
```

### 템플릿 등록 요청

```json
POST /api/templates
{
  "notificationType": "ENROLLMENT_COMPLETED",
  "channel": "EMAIL",
  "titleTemplate": "수강 신청 완료",
  "bodyTemplate": "{referenceId} 강의 수강 신청이 완료되었습니다."
}
```

사용 가능한 플레이스홀더: `{recipientId}`, `{referenceId}`, `{referenceType}`, `{notificationType}`

### 단건 상태 조회

```
GET /api/notifications/1
```

응답 (200 OK):
```json
{
  "id": 1,
  "status": "SENT",
  "retryCount": 0,
  "failureReason": null,
  "sentAt": "2026-05-16T12:00:05"
}
```

### 목록 조회 (안읽은 알림만)

```
GET /api/notifications?recipientId=1&read=false
```

### 읽음 처리

```
PATCH /api/notifications/1/read
```

응답 (200 OK):
```json
{
  "id": 1,
  "isRead": true,
  "readAt": "2026-05-16T12:01:00"
}
```

### 수동 재시도 (DEAD_LETTER → PENDING)

```
POST /api/notifications/1/retry
```

응답 (200 OK):
```json
{
  "id": 1,
  "status": "PENDING",
  "retryCount": 0
}
```

오류 응답 (409 Conflict — DEAD_LETTER 아닌 경우):
```json
{
  "status": 409,
  "error": "Conflict",
  "message": "DEAD_LETTER 상태의 알림만 수동 재시도할 수 있습니다"
}
```

---

## 데이터 모델 설명

### notifications

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | BIGINT (PK) | 자동 증가 |
| recipient_id | BIGINT | 수신자 ID |
| notification_type | VARCHAR | ENROLLMENT_COMPLETED 등 |
| channel | VARCHAR | EMAIL / IN_APP |
| reference_id | VARCHAR | 이벤트/강의 ID 등 참조 식별자 |
| reference_type | VARCHAR | COURSE / PAYMENT 등 |
| idempotency_key | VARCHAR (UNIQUE) | 중복 방지 키 |
| status | VARCHAR | PENDING / PROCESSING / SENT / FAILED / DEAD_LETTER |
| retry_count | INT | 재시도 횟수 |
| failure_reason | TEXT | 실패 사유 |
| is_read | BOOLEAN | 읽음 여부 (IN_APP) |
| read_at | TIMESTAMP | 읽은 시각 |
| scheduled_at | TIMESTAMP | 예약 발송 시각 (null = 즉시) |
| sent_at | TIMESTAMP | 발송 완료 시각 |
| processing_started_at | TIMESTAMP | 처리 시작 시각 (Stuck 감지용) |
| version | BIGINT | 낙관적 락 버전 |
| created_at | TIMESTAMP | 생성 시각 |
| updated_at | TIMESTAMP | 수정 시각 |

인덱스: `idx_recipient_id`, `idx_status`, `idx_scheduled_at`

### notification_templates

| 컬럼 | 설명 |
|---|---|
| notification_type + channel | 복합 unique key |
| title_template | 제목 템플릿 |
| body_template | 본문 템플릿 |

---

## 요구사항 해석 및 개선 의견

### 해석한 부분

- **"알림 처리 실패가 비즈니스 트랜잭션에 영향을 주면 안 된다"** — API 스레드와 발송 스레드를 완전히 분리했습니다. 발송이 실패해도 수강 신청 트랜잭션은 이미 커밋된 상태이므로 영향이 없습니다.
- **"예외를 단순히 무시해서는 안 된다"** — `FAILED`, `DEAD_LETTER` 상태와 `failureReason` 기록으로 모든 실패를 추적합니다.

### 개선하고 싶은 점

**1. 재시도 백오프 정책**  
현재는 폴러 주기(5초)에 단순 의존합니다. 실제 운영에서는 지수 백오프(`1분 → 5분 → 30분`)를 적용해서 외부 서버 장애 시 불필요한 재시도를 줄이는 것이 좋습니다. `next_retry_at` 컬럼을 추가하면 구현할 수 있습니다.

**2. 알림 발송 순서 보장**  
현재는 `createdAt ASC` 순으로 처리하지만, 다중 인스턴스에서 건별 락을 거는 구조상 처리 순서가 완전히 보장되지는 않습니다. 순서가 중요한 경우 파티셔닝(`recipientId % N`)으로 인스턴스별 처리 대상을 분리하는 방법을 고려할 수 있습니다.

**3. 템플릿 자동 적용**  
현재 템플릿은 관리 API만 있고 실제 발송 메시지에 자동 적용되지 않습니다. `NotificationSender`가 발송 시 템플릿을 조회해서 렌더링하는 흐름을 추가하면 완성됩니다.

**4. 알림 만료 처리**  
오래된 `SENT` 알림은 보관할 필요가 없습니다. `TTL` 또는 배치 삭제 정책을 추가하면 DB 크기를 제어할 수 있습니다.

---

## 미구현 / 제약사항

- 실제 이메일 발송 미구현 (로그 출력으로 대체)
- 실제 메시지 브로커 미사용 (DB Outbox로 대체)
- 인증/인가 미구현 (userId를 파라미터로 전달하는 방식)
- 템플릿이 실제 발송 메시지에 자동 적용되지 않음 (관리 API만 제공)
- 재시도 백오프 정책 미구현 (폴링 주기에 의존)

---

## 테스트 실행 방법

```bash
./gradlew test

# 리포트 확인
open build/reports/tests/test/index.html
```

| 테스트 클래스 | 유형 | 검증 항목 |
|---|---|---|
| `NotificationServiceTest` | 단위 | 등록 멱등성, 조회, 읽음 처리, 수동 재시도 |
| `NotificationProcessorServiceTest` | 단위 | 발송 성공/실패/DEAD_LETTER, 중복 처리 스킵 |
| `NotificationControllerTest` | 통합 (MockMvc) | API 응답 코드, 유효성 검증, 예외 처리 |

---

## AI 활용 범위

- 도메인 설계 및 패턴 선정 (Outbox Pattern, Rich Domain Model) 방향 논의
- 보일러플레이트 코드 초안 생성 후 직접 검토 및 수정
- 비관적 락 / 낙관적 락 적용 위치 결정은 직접 판단
- 컴파일 오류 및 테스트 실패는 직접 디버깅하여 수정
