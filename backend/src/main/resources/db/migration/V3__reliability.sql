-- Retries, crash resume and webhook dedupe (Phase 4).

ALTER TABLE runs ADD COLUMN dedupe_key text;
ALTER TABLE runs ADD CONSTRAINT runs_workflow_dedupe_key UNIQUE (workflow_id, dedupe_key);

ALTER TABLE step_runs ADD COLUMN attempt int NOT NULL DEFAULT 0;
ALTER TABLE step_runs ADD COLUMN next_retry_at timestamptz;
ALTER TABLE step_runs DROP CONSTRAINT step_runs_status_check;
ALTER TABLE step_runs ADD CONSTRAINT step_runs_status_check
    CHECK (status IN ('PENDING', 'RUNNING', 'WAITING_RETRY', 'SUCCEEDED', 'FAILED', 'SKIPPED'));
