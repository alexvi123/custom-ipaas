-- Workflows, their versions, runs, step runs and the job queue (Phase 3).

CREATE TABLE workflows (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            text        NOT NULL,
    active          boolean     NOT NULL DEFAULT false,
    current_version int         NOT NULL,
    webhook_token   text        NOT NULL UNIQUE,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE workflow_versions (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id uuid        NOT NULL REFERENCES workflows (id) ON DELETE CASCADE,
    version     int         NOT NULL,
    definition  jsonb       NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (workflow_id, version)
);

CREATE TABLE runs (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    workflow_id         uuid        NOT NULL REFERENCES workflows (id) ON DELETE CASCADE,
    workflow_version_id uuid        NOT NULL REFERENCES workflow_versions (id) ON DELETE CASCADE,
    status              text        NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    trigger_payload     jsonb       NOT NULL,
    error               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    started_at          timestamptz,
    finished_at         timestamptz
);

CREATE INDEX runs_workflow_created_idx ON runs (workflow_id, created_at DESC);

CREATE TABLE step_runs (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id        uuid NOT NULL REFERENCES runs (id) ON DELETE CASCADE,
    step_key      text NOT NULL,
    position      int  NOT NULL,
    connector_key text NOT NULL,
    action_key    text NOT NULL,
    status        text NOT NULL CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'SKIPPED')),
    input         jsonb,
    output        jsonb,
    error         text,
    started_at    timestamptz,
    finished_at   timestamptz,
    UNIQUE (run_id, step_key),
    UNIQUE (run_id, position)
);

CREATE TABLE jobs (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id       uuid        NOT NULL UNIQUE REFERENCES runs (id) ON DELETE CASCADE,
    run_at       timestamptz NOT NULL DEFAULT now(),
    locked_until timestamptz,
    attempts     int         NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX jobs_claimable_idx ON jobs (run_at) WHERE locked_until IS NULL;
