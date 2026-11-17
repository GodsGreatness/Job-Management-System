# REST API Reference & RBAC Matrix

Interactive OpenAPI / Swagger UI is served live at `/swagger-ui.html` when the backend is running.

## Authentication
All protected endpoints expect a Bearer JWT header:
```http
Authorization: Bearer <token>
```

---

## Endpoints Summary

### Auth (`/api/auth`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/register` | Public | Register a new account (`CANDIDATE`, `EMPLOYER`, `RECRUITER`) and enqueue welcome email |
| `POST` | `/api/auth/login` | Public | Authenticate credentials and issue HS256 JWT token |
| `GET` | `/api/auth/me` | Authenticated | Return current authenticated user summary |

### Jobs (`/api/jobs`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/jobs` | Public | Search & filter jobs (`status`, `remote`, `employmentType`, `keyword`) |
| `GET` | `/api/jobs/{id}` | Public | Retrieve single job posting details |
| `GET` | `/api/jobs/mine` | `EMPLOYER`, `RECRUITER`, `ADMIN` | List jobs posted by the authenticated user |
| `POST` | `/api/jobs` | `EMPLOYER`, `RECRUITER`, `ADMIN` | Create a new job posting |
| `PATCH` | `/api/jobs/{id}` | Owner / `RECRUITER` / `ADMIN` | Update job details or status (`OPEN`, `PAUSED`, `CLOSED`) |
| `DELETE` | `/api/jobs/{id}` | Owner / `RECRUITER` / `ADMIN` | Close a job posting |

### Applications (`/api/applications`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/applications` | `CANDIDATE` | Submit application & trigger async ATS scoring + notification/email workers |
| `GET` | `/api/applications` | Authenticated | Role-scoped listing (own applications for Candidate, org applicants for Employer, global for Recruiter/Admin) |
| `PATCH` | `/api/applications/{id}/status` | Role-scoped | Candidate may `WITHDRAW`; Employer/Recruiter/Admin advance through ATS stages |

### Interviews (`/api/interviews`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/interviews` | `EMPLOYER`, `RECRUITER`, `ADMIN` | Schedule an interview & auto-advance application to `INTERVIEW` |
| `GET` | `/api/interviews` | Authenticated | List role-scoped interviews |
| `PATCH` | `/api/interviews/{id}` | `EMPLOYER`, `RECRUITER`, `ADMIN` | Update interview status, feedback, and rating (`1–5`) |

### Notifications & Analytics
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/notifications` | Authenticated | List user's in-app notifications |
| `POST` | `/api/notifications/read-all` | Authenticated | Mark all user notifications read |
| `GET` | `/api/analytics/overview` | `EMPLOYER`, `RECRUITER`, `ADMIN` | Hiring funnel KPIs, average ATS score, and user/job totals |
| `POST` | `/api/analytics/refresh` | `RECRUITER`, `ADMIN` | Enqueue an async `REFRESH_ANALYTICS` job |

### Admin Background Queue Operations (`/api/admin/queue`)
| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/queue/metrics` | `ADMIN`, `RECRUITER` | Live thread-pool utilization, queue depth, DLQ count, and average execution latency |
| `GET` | `/api/admin/queue/jobs` | `ADMIN`, `RECRUITER` | Inspect background job ledger (optional `?status=DEAD_LETTER&limit=50`) |
| `POST` | `/api/admin/queue/jobs/trigger` | `ADMIN` | Manually dispatch a background job (with optional `simulateFailure: true` to test retry/DLQ) |
| `POST` | `/api/admin/queue/jobs/{id}/replay` | `ADMIN` | Reset and replay a `DEAD_LETTER` job back into the priority queue |
| `POST` | `/api/admin/queue/flush` | `ADMIN` | Trigger an immediate worker pool dispatch cycle |
| `GET` | `/api/admin/users` | `ADMIN` | List all registered users |
