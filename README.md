# Explore With Me — микросервисная архитектура

Афиша событий: пользователи публикуют события и подают заявки на участие, администраторы модерируют события, формируют подборки, пользователи ставят лайки/дизлайки.
Проект построен на Spring Cloud: единая точка входа (Gateway), service discovery (Eureka), централизованная конфигурация (Config Server), межсервисные вызовы через OpenFeign.

## Архитектура

```
                     клиент (Postman, :8080)
                              │
                       gateway-server  ──────────┐
        ┌────────────┬────────┴───────┬──────────┐│  lb:// через Eureka
        ▼            ▼                ▼          ▼│
  user-service  request-service  rating-service  event-service ──► stats-server (статистика просмотров)
     user_db       request_db       rating_db      main (events, categories, compilations)

  discovery-server (Eureka, 8761)    config-server (конфигурации сервисов из infra/config-repo)
```

| Модуль | Назначение | БД |
|---|---|---|
| `infra/discovery-server` | Eureka, порт 8761 | — |
| `infra/config-server` | Config Server (native, файлы из `infra/config-repo`) | — |
| `infra/gateway-server` | Spring Cloud Gateway, порт **8080**, маршрутизация по микросервисам | — |
| `core/event-service` | События (создание, модерация, поиск), категории, подборки | `main` (порт 6642) |
| `core/user-service` | Администрирование пользователей | `user_db` (6643) |
| `core/request-service` | Заявки на участие в событиях | `request_db` (6644) |
| `core/rating-service` | Лайки/дизлайки событий, рейтинг событий и авторов (дополнительная функциональность) | `rating_db` (6645) |
| `core/interaction-api` | Общая библиотека: Feign-клиенты и fallback'и, внутренние DTO, исключения, `ErrorHandler` | — |
| `stats/*` | Сервис статистики (`stats-server`), клиент (`stats-client`), DTO (`stats-dto`) | `stats` (5432) |

`event-service` остаётся сервисом управления событиями (в него входят также категории и подборки, которые тесно связаны с событиями).
Из него вынесены пользователи, заявки и рейтинги.

### База данных на сервис
У каждого сервиса своя БД и свои таблицы. Внешние ключи между сервисами заменены обычными идентификаторами
(`events.initiator_id`, `requests.event_id/requester_id`, `event_ratings.event_id/user_id/author_id`) — целостность
проверяется при записи вызовом соответствующего сервиса. Схемы: `core/<service>/src/main/resources/schema.sql`.
В `event_ratings` дополнительно хранится `author_id` (инициатор события), чтобы рейтинг авторов считался без обращения к событиям.

## Внешний API
- основной сервис: [`ewm-main-service-spec.json`](ewm-main-service-spec.json) (также дополнительные эндпоинты рейтинга: `PUT /users/{userId}/events/{eventId}/like|dislike`, `DELETE /users/{userId}/events/{eventId}/rating`, `GET /users/{userId}/rating`, `GET /users/top-authors`, `GET /events/top`);
- сервис статистики: [`ewm-stats-service-spec.json`](ewm-stats-service-spec.json).

Клиенты обращаются только в Gateway (`http://localhost:8080`). Маршруты (`infra/config-repo/infra/gateway-server.yml`):

| Путь | Сервис |
|---|---|
| `/admin/users/**` | user-service |
| `/users/*/requests/**`, `/users/*/events/*/requests/**` | request-service |
| `/users/*/events/*/like`, `/dislike`, `/rating`, `/users/*/rating`, `/users/top-authors` | rating-service |
| `/admin/**`, `/users/**`, `/categories/**`, `/events/**`, `/compilations/**` (всё остальное) | event-service |

## Внутренний API (между сервисами)
Доступен только внутри сети сервисов, через Gateway не публикуется (префикс `/internal`). Вызывается через Feign-клиенты из `core/interaction-api` (`ewm.client`).

| Сервис | Запрос | Назначение |
|---|---|---|
| user-service | `GET /internal/users/{userId}` | пользователь по id (404, если нет) |
| user-service | `GET /internal/users/batch?ids=1&ids=2` | краткая информация о нескольких пользователях за один запрос |
| event-service | `GET /internal/events/{eventId}` | данные события для проверок (инициатор, статус, лимит, модерация, дата) |
| event-service | `GET /internal/events/published-count?initiatorIds=…` | число опубликованных событий по авторам |
| request-service | `GET /internal/requests/confirmed-count?eventIds=…` | число подтверждённых заявок по событиям (одним запросом) |
| request-service | `GET /internal/requests/confirmed?eventId=&userId=` | есть ли подтверждённая заявка пользователя на событие |
| rating-service | `GET /internal/ratings?eventIds=…` | лайки/дизлайки событий (одним запросом) |
| rating-service | `GET /internal/ratings/top-events?from=&size=` | события с наивысшим рейтингом |

Кто кого вызывает: `event-service` → user/request/rating-service (имена инициаторов, число заявок, рейтинг); `request-service` → event-service, user-service;
`rating-service` → event-service, request-service, user-service. Все «списочные» операции используют пакетные эндпоинты, поэтому число межсервисных запросов
не зависит от размера выборки (нет N+1).

## Отказоустойчивость
- Feign + Eureka (`lb://`), для каждого клиента задан `fallbackFactory` (`core/interaction-api/.../ewm/client`).
- Circuit breaker и time limiter — Resilience4j (`ResilienceConfig`); ответы 400/404/409 не считаются отказом.
- Ответы 5xx повторяются (`Retryer`, 3 попытки) — `FeignClientConfig`.
- Если зависимый сервис недоступен, при чтении используются значения по умолчанию: заявки/лайки/дизлайки/рейтинг — `0`, имя инициатора — `null`,
  просмотры из stats — `0`. Операции, где без проверки нельзя корректно записать данные (создание заявки/события/оценки), отвечают `503 Service Unavailable`.

## Конфигурация
Каждый сервис хранит локально только `spring.application.name` и параметры подключения к Config Server (`src/main/resources/application.yml`).
Остальное — в `infra/config-repo` (упаковывается в config-server; можно переопределить `CONFIG_REPO_LOCATION=file:/path`):

```
infra/config-repo/
├── application.yml            # общее: Eureka, Feign/Resilience
├── application-docker.yml     # общее для профиля docker
├── core/{main,user,request,rating}-service[-docker].yml
├── stats/stats-server[-docker].yml
└── infra/gateway-server.yml
```

## Запуск
1. Собрать: `mvn clean package`
2. Docker: `docker compose up --build` — поднимутся все сервисы и БД; наружу опубликован только Gateway (`8080`) и Eureka (`8761`), сервисы запускаются на случайных портах.
3. Локально из IDE: запустить БД (`docker compose up -d stats-db ewm-db user-db request-db rating-db`), затем по порядку `discovery-server`, `config-server`,
   затем остальные сервисы и `gateway-server`.
