# Job Management System (JMS)

[![CI — Build, Test & Verify](https://github.com/GodsGreatness/Job-Management-System/actions/workflows/ci.yml/badge.svg)](https://github.com/GodsGreatness/Job-Management-System/actions/workflows/ci.yml)
![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot 3](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen?logo=springboot)
![React 18 + TypeScript](https://img.shields.io/badge/React%2018-TypeScript-3178C6?logo=react)
![PostgreSQL + Redis + Docker](https://img.shields.io/badge/PostgreSQL%20%7C%20Redis-Docker-2496ED?logo=docker)

A **full-stack Job Portal & Applicant Tracking System (ATS)** backed by a **concurrent, priority-scheduled asynchronous Background Job Processing Engine** built with **Java 21 (Spring Boot 3)** and **React (TypeScript + Vite)**.

Designed to unite real-world full-stack product architecture (multi-role portals, JWT/RBAC security, hiring workflows, analytics) with backend systems engineering depth (bounded worker thread pools, optimistic concurrency locking, idempotency keys, exponential backoff retries, Dead-Letter Queue replay, and live queue telemetry).

---

## Architecture Highlights

```text
Job-Management-System/
├── backend/                     # Java 21 + Spring Boot 3 REST API & Worker Engine
│   ├── src/main/java/com/jms/
│   │   ├── config/              # SecurityConfig, OpenApiConfig, DataSeeder
│   │   ├── security/            # HS256 JWT auth filter & RBAC UserPrincipal
│   │   ├── domain/              # JPA Entities & Enums (PostgreSQL / H2 PG-Mode)
│   │   ├── repository/          # Spring Data JPA repositories & priority queries
│   │   ├── service/             # Auth, Job, Application, Interview, Notification services
│   │   ├── worker/              # ⭐ Concurrent Priority Worker Pool Engine
│   │   │   ├── BackgroundJobQueueService.java
│   │   │   ├── WorkerPoolEngine.java
│   │   │   ├── EmailWorker.java
│   │   │   ├── ResumeProcessingWorker.java
│   │   │   ├── ApplicationNotificationWorker.java
│   │   │   └── AnalyticsWorker.java
│   │   └── controller/          # REST API + Admin Queue Ops + Swagger/OpenAPI
│   └── src/test/java/com/jms/   # JUnit 5 Unit & MockMvc End-to-End Integration Tests
├── frontend/                    # React 18 + TypeScript + Vite SPA
│   └── src/pages/
│       ├── CandidatePortal.tsx  # Job search, 1-click apply, async ATS scores, notifications
│       ├── EmployerPortal.tsx   # Requisition publishing, ATS-ranked applicants
│       ├── RecruiterDashboard.tsx # Cross-org pipeline triage & interview scheduling
│       └── AdminDashboard.tsx   # Live Worker Pool Monitor, Chaos Simulator & DLQ Replay
├── docs/                        # Architecture diagrams & REST API / RBAC reference
├── scripts/                     # One-click Windows (.ps1) & Linux/macOS (.sh) dev runners
└── docker-compose.yml           # Full-stack deployment: PostgreSQL 16 + Redis 7 + Java + Nginx
```

See [docs/architecture.md](docs/architecture.md) for Mermaid architecture and worker state machine diagrams, and [docs/api-reference.md](docs/api-reference.md) for the complete REST API and RBAC matrix.

---

## Key Features

### 1. Multi-Role Web Application (4 Portals)
- **Candidate Portal**: Filter open roles by keyword/remote status, apply with resume text, view real-time **ATS Skill Match Scores** (matched vs. missing skills), track interview schedules, and manage notifications.
- **Employer Portal**: Create, pause, reopen, or close job requisitions; inspect incoming applicants ranked by ATS match score; advance candidates through hiring stages.
- **Recruiter Dashboard**: Cross-organization ATS pipeline filtering (`SUBMITTED → SCREENING → INTERVIEW → OFFER → HIRED / REJECTED`), interview scheduling, feedback recording, and conversion KPIs.
- **Admin Operations & Live Queue Monitor**: Live thread-pool utilization, queue depth, execution latency, hiring funnel analytics, **simulated failure injection** (watch exponential backoff into the Dead-Letter Queue in real time), and **1-click DLQ replay**.

### 2. Asynchronous Background Worker Pool Engine (`com.jms.worker`)
- **Priority Queue + FIFO Ordering**: Jobs execute in strict `priority DESC, createdAt ASC` order.
- **Bounded Thread Pool (`WorkerPoolEngine`)**: Dispatches tasks concurrently across a configurable `ExecutorService` with atomic optimistic-lock (`@Version`) claiming and graceful `@PreDestroy` shutdown.
- **Idempotency Keys**: Prevents duplicate execution for welcome emails, resume parsing, and interview invites.
- **Exponential Backoff Retries**: Transient failures reschedule with `delay = baseBackoffMs * 2^(attempt - 1)`.
- **Dead-Letter Queue (DLQ) & Replay**: Exhausted jobs transition to `DEAD_LETTER` with full error telemetry and can be replayed via REST API or Admin UI.
- **4 Specialized Domain Workers**:
  - `ResumeProcessingWorker` — Extracts required skills from job postings, scores candidate resumes (0–100%), persists matched/missing skill sets, and fans out high-match alerts (`≥ 75%`).
  - `EmailWorker` — Dispatches transactional email payloads with optional failure-simulation mode for resilience testing.
  - `ApplicationNotificationWorker` — Persists real-time in-app notifications for candidates, employers, and recruiters.
  - `AnalyticsWorker` — Precomputes hiring funnel snapshots in memory and syncs to Redis when running in `prod` profile.

---

## Quick Start

### Option A: Zero-Config Local Development (No Docker Required)
By default, the backend boots with embedded H2 in **PostgreSQL compatibility mode**, pre-seeds demo accounts and engineering roles, and starts the concurrent worker pool immediately.

**Windows (PowerShell):**
```powershell
.\scripts\run_dev.ps1
```

**Linux / macOS:**
```bash
chmod +x scripts/run_dev.sh
./scripts/run_dev.sh
```

Or run manually in two terminals:
```bash
# Terminal 1 — Java Backend (http://localhost:8080)
cd backend
./mvnw spring-boot:run

# Terminal 2 — React Frontend (http://localhost:5173)
cd frontend
npm install && npm run dev
```

- **Web App**: `http://localhost:5173`
- **Swagger / OpenAPI Docs**: `http://localhost:8080/swagger-ui.html`
- **Spring Actuator Health**: `http://localhost:8080/actuator/health`

### Option B: Full Production Stack with Docker Compose (PostgreSQL 16 + Redis 7)
```bash
docker compose up --build
```
- **Web App (Nginx + React)**: `http://localhost:3000`
- **Backend API (Java 21 + PostgreSQL + Redis)**: `http://localhost:8080`

---

## Pre-Seeded Demo Accounts

All demo accounts share password **`password123`** (and are available as 1-click buttons on the login screen):

| Portal | Role | Email | Password |
| :--- | :--- | :--- | :--- |
| Candidate Portal | `CANDIDATE` | `candidate@jms.dev` | `password123` |
| Employer Portal | `EMPLOYER` | `employer@jms.dev` | `password123` |
| Recruiter Dashboard | `RECRUITER` | `recruiter@jms.dev` | `password123` |
| Admin & Queue Monitor | `ADMIN` | `admin@jms.dev` | `password123` |

---

## Automated Testing & CI/CD

### Backend (JUnit 5 + Spring Boot Test + MockMvc)
```bash
cd backend
./mvnw test
```
Covers:
- `WorkerPoolEngineTest`: Strict priority ordering, idempotency key deduplication, exponential backoff retry progression, Dead-Letter Queue routing, and DLQ manual replay.
- `ResumeProcessingWorkerTest`: Skill extraction (`Java`, `Spring Boot`, `C++`, `PostgreSQL`, `Docker`), ATS match score calculation, and high-match notification fan-out.
- `ApiIntegrationTest`: End-to-end JWT authentication, RBAC access denial (`403`), duplicate application prevention (`409`), job posting, interview scheduling, status transitions, and admin queue/analytics endpoints.

### Frontend (TypeScript + Vitest + Vite)
```bash
cd frontend
npm test
npm run build
```

### Continuous Integration
GitHub Actions (`.github/workflows/ci.yml`) automatically runs on every push and pull request:
1. **Java 21 Backend**: Maven compile, JUnit 5 test suite, and executable Spring Boot JAR packaging.
2. **React + TypeScript Frontend**: Vitest unit tests, strict TypeScript type-check (`tsc -b`), and Vite production build.
3. **Infrastructure Validation**: `docker compose config` verification.

---

## License

MIT License — see [LICENSE](LICENSE).
