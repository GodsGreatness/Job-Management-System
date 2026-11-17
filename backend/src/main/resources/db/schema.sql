-- PostgreSQL 16 Reference Schema for Job Management System (JMS)
-- Automatically synchronized by JPA/Hibernate; provided here for DBA review and migrations.

CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(180) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    company_name  VARCHAR(150),
    headline      VARCHAR(250),
    skills        TEXT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS job_postings (
    id              BIGSERIAL PRIMARY KEY,
    title           VARCHAR(180) NOT NULL,
    company         VARCHAR(150) NOT NULL,
    department      VARCHAR(120),
    location        VARCHAR(150) NOT NULL,
    remote          BOOLEAN      NOT NULL DEFAULT FALSE,
    employment_type VARCHAR(30)  NOT NULL DEFAULT 'FULL_TIME',
    salary_min      INTEGER,
    salary_max      INTEGER,
    description     TEXT         NOT NULL,
    required_skills TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    employer_id     BIGINT       NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS applications (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT      NOT NULL REFERENCES job_postings(id),
    candidate_id    BIGINT      NOT NULL REFERENCES users(id),
    status          VARCHAR(30) NOT NULL DEFAULT 'SUBMITTED',
    resume_text     TEXT        NOT NULL,
    cover_letter    TEXT,
    ats_match_score INTEGER,
    matched_skills  TEXT,
    missing_skills  TEXT,
    recruiter_notes TEXT,
    applied_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_application_job_candidate UNIQUE (job_id, candidate_id)
);

CREATE TABLE IF NOT EXISTS interviews (
    id               BIGSERIAL PRIMARY KEY,
    application_id   BIGINT       NOT NULL REFERENCES applications(id),
    interviewer_id   BIGINT       NOT NULL REFERENCES users(id),
    scheduled_at     TIMESTAMPTZ  NOT NULL,
    duration_minutes INTEGER      NOT NULL DEFAULT 45,
    meeting_link     VARCHAR(300),
    stage            VARCHAR(100) NOT NULL,
    status           VARCHAR(25)  NOT NULL DEFAULT 'SCHEDULED',
    feedback         TEXT,
    rating           INTEGER,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS notifications (
    id           BIGSERIAL PRIMARY KEY,
    recipient_id BIGINT       NOT NULL REFERENCES users(id),
    title        VARCHAR(180) NOT NULL,
    message      TEXT         NOT NULL,
    category     VARCHAR(50)  NOT NULL,
    read_flag    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS background_jobs (
    id                    BIGSERIAL PRIMARY KEY,
    type                  VARCHAR(40) NOT NULL,
    status                VARCHAR(25) NOT NULL DEFAULT 'QUEUED',
    priority              INTEGER     NOT NULL DEFAULT 5,
    payload               TEXT        NOT NULL,
    idempotency_key       VARCHAR(160) UNIQUE,
    attempts              INTEGER     NOT NULL DEFAULT 0,
    max_attempts          INTEGER     NOT NULL DEFAULT 3,
    next_run_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    locked_by             VARCHAR(100),
    started_at            TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    execution_duration_ms BIGINT,
    last_error            TEXT,
    result_summary        TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    version               BIGINT      NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_bg_status_priority_next
    ON background_jobs (status, priority DESC, next_run_at ASC);
