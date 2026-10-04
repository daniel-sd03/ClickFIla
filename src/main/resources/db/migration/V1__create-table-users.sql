CREATE TABLE users
(
    id                 TEXT PRIMARY KEY UNIQUE NOT NULL,
    login              TEXT UNIQUE             NOT NULL,
    password           TEXT                    NOT NULL,
    name               TEXT                    NOT NULL,
    phone              TEXT,
    role               TEXT                    NOT NULL,
    google_id          TEXT UNIQUE,
    is_active          BOOLEAN                 NOT NULL,
    tutorial_completed BOOLEAN                          DEFAULT FALSE NOT NULL,
    email_verified     BOOLEAN                 NOT NULL DEFAULT FALSE,
    deleted_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ                      DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ,
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255)
);

CREATE TABLE lgpd_consents
(
    id           TEXT PRIMARY KEY UNIQUE NOT NULL,
    user_id      TEXT                    NOT NULL,
    term_version VARCHAR(20)             NOT NULL,
    ip_address   VARCHAR(45),
    user_agent   TEXT,
    created_at   TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_lgpd_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE businesses
(
    id         TEXT PRIMARY KEY UNIQUE NOT NULL,
    user_id    TEXT UNIQUE             NOT NULL,
    name       TEXT                    NOT NULL,
    cpf_cnpj   VARCHAR(14),
    is_active  BOOLEAN                 NOT NULL,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    CONSTRAINT fk_business_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE team_members
(
    id          TEXT PRIMARY KEY UNIQUE NOT NULL,
    business_id TEXT                    NOT NULL,
    name        VARCHAR(100)            NOT NULL,
    user_id     TEXT,
    role        VARCHAR(20)             NOT NULL,
    pin_code    VARCHAR(4),
    is_active   BOOLEAN                 NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ,
    created_by  VARCHAR(255),
    updated_by  VARCHAR(255),
    CONSTRAINT fk_team_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE TABLE queue_sessions
(
    id                TEXT PRIMARY KEY   NOT NULL,
    business_id       TEXT               NOT NULL,
    ticket_code       VARCHAR(50) UNIQUE NOT NULL,
    is_active         BOOLEAN            NOT NULL,
    tolerance_minutes INTEGER            NOT NULL,
    prefix            VARCHAR(10),
    created_at        TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMPTZ,
    created_by        VARCHAR(255)       NOT NULL,
    updated_by        VARCHAR(255),
    CONSTRAINT fk_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
);

CREATE TABLE queue_entries
(
    id                  TEXT PRIMARY KEY NOT NULL,
    queue_sessions_id   TEXT             NOT NULL,
    user_id             TEXT             NOT NULL,
    served_by_member_id TEXT,
    service_name        VARCHAR(100)     NOT NULL,
    status              VARCHAR(30)      NOT NULL,
    missed_calls        INTEGER          NOT NULL,
    version             BIGINT           NOT NULL,
    joined_at           TIMESTAMPTZ      NOT NULL,
    called_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),
    CONSTRAINT fk_queue_sessions FOREIGN KEY (queue_sessions_id) REFERENCES queue_sessions (id) ON DELETE CASCADE,
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_served_by_member FOREIGN KEY (served_by_member_id) REFERENCES team_members (id) ON DELETE SET NULL
);

CREATE TABLE password_reset_tokens
(
    id          TEXT PRIMARY KEY UNIQUE NOT NULL,
    email       TEXT                    NOT NULL,
    code        VARCHAR(10)             NOT NULL,
    expiry_date TIMESTAMPTZ             NOT NULL,
    created_at  TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE team_invites
(
    id          VARCHAR(255) PRIMARY KEY,
    business_id VARCHAR(255) NOT NULL,
    email       VARCHAR(255) NOT NULL,
    role        VARCHAR(50)  NOT NULL,
    status      VARCHAR(50)  NOT NULL,
    created_by  VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    CONSTRAINT fk_team_invites_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
);

CREATE TABLE user_push_subscriptions
(
    id       VARCHAR(255) PRIMARY KEY,
    user_id  VARCHAR(255)  NOT NULL,
    endpoint VARCHAR(1000) NOT NULL,
    p256dh   VARCHAR(255)  NOT NULL,
    auth     VARCHAR(255)  NOT NULL,

    CONSTRAINT fk_user_push_subscription FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE refresh_tokens
(
    id          VARCHAR(255) PRIMARY KEY,
    token       VARCHAR(255)             NOT NULL UNIQUE,
    user_id     VARCHAR(255)             NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE plans
(
    id              TEXT PRIMARY KEY UNIQUE   NOT NULL,
    code            VARCHAR(30) UNIQUE        NOT NULL,
    name            VARCHAR(100)              NOT NULL,
    description     TEXT,
    price           DECIMAL(10, 2)            NOT NULL,
    currency        VARCHAR(3)  DEFAULT 'BRL' NOT NULL,
    billing_cycle   VARCHAR(20)               NOT NULL,
    gateway_plan_id VARCHAR(100),
    is_active       BOOLEAN     DEFAULT TRUE  NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMPTZ,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
);

CREATE TABLE subscriptions
(
    id                      TEXT PRIMARY KEY UNIQUE   NOT NULL,
    business_id             TEXT UNIQUE               NOT NULL,
    plan_id                 TEXT,
    payment_provider        VARCHAR(30)               NOT NULL,
    gateway_subscription_id VARCHAR(100) UNIQUE,
    gateway_customer_id     VARCHAR(100),
    status                  VARCHAR(30)               NOT NULL,
    billing_anchor_day      INTEGER,
    current_period_start    DATE                      NOT NULL,
    current_period_end      DATE                      NOT NULL,
    cancel_at_period_end    BOOLEAN     DEFAULT FALSE NOT NULL,
    canceled_at             TIMESTAMPTZ,
    created_at              TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMPTZ,
    created_by              VARCHAR(255),
    updated_by              VARCHAR(255),
    CONSTRAINT fk_subscription_plan FOREIGN KEY (plan_id) REFERENCES plans (id),
    CONSTRAINT fk_subscription_business FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE CASCADE
);

CREATE TABLE payments
(
    id                 TEXT PRIMARY KEY UNIQUE   NOT NULL,
    subscription_id    TEXT                      NOT NULL,
    payment_provider   VARCHAR(30)               NOT NULL,
    gateway_invoice_id VARCHAR(100) UNIQUE,
    amount             DECIMAL(10, 2)            NOT NULL,
    currency           VARCHAR(3)  DEFAULT 'BRL' NOT NULL,
    status             VARCHAR(30)               NOT NULL,
    payment_method     VARCHAR(30),
    installments       INTEGER     DEFAULT 1     NOT NULL,
    due_date           DATE                      NOT NULL,
    paid_at            TIMESTAMPTZ,
    invoice_url        TEXT,
    pix_copy_paste     TEXT,
    pix_qr_code_base64 TEXT,
    receipt_url        TEXT,
    created_at         TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ,
    created_by         VARCHAR(255),
    updated_by         VARCHAR(255),
    CONSTRAINT fk_payment_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE
);

CREATE TABLE subscription_history
(
    id              TEXT PRIMARY KEY UNIQUE NOT NULL,
    subscription_id TEXT                    NOT NULL,
    previous_status VARCHAR(30),
    new_status      VARCHAR(30)             NOT NULL,
    reason          TEXT                    NOT NULL,
    created_at      TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_history_subscription FOREIGN KEY (subscription_id) REFERENCES subscriptions (id) ON DELETE CASCADE
);

CREATE INDEX idx_subscription_history_sub_id
    ON subscription_history (subscription_id);

CREATE INDEX idx_team_invites_email
    ON team_invites (email);

CREATE INDEX idx_password_reset_email_code
    ON password_reset_tokens (email, code);

CREATE UNIQUE INDEX idx_unique_active_user_queue
    ON queue_entries (user_id) WHERE status IN ('WAITING', 'CALLED', 'IN_SERVICE');

CREATE INDEX idx_queue_entries_session_status
    ON queue_entries (queue_sessions_id, status, joined_at);

CREATE INDEX idx_queue_entries_user_joined_at
    ON queue_entries (user_id, joined_at DESC);

CREATE UNIQUE INDEX idx_queue_sessions_ticket_code
    ON queue_sessions (ticket_code);

CREATE INDEX idx_subscriptions_status_period_end
    ON subscriptions (status, current_period_end);

CREATE INDEX idx_payments_subscription_status
    ON payments (subscription_id, status);