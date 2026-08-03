CREATE TABLE pipeline_runs (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    pipeline_definition_id UUID NOT NULL REFERENCES pipeline_definitions(id),
    pipeline_revision_id UUID NOT NULL REFERENCES pipeline_revisions(id),
    state VARCHAR(16) NOT NULL,
    failure_summary VARCHAR(512),
    final_report JSONB,
    dead_letter_configuration JSONB,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX pipeline_runs_definition_created_idx
    ON pipeline_runs (pipeline_definition_id, created_at DESC);
