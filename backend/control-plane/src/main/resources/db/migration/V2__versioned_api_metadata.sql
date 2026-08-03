ALTER TABLE pipeline_definitions
    ADD COLUMN description VARCHAR(500) NOT NULL DEFAULT '',
    ADD COLUMN archived_at TIMESTAMPTZ;

ALTER TABLE pipeline_revisions
    ADD COLUMN blueprint_configuration JSONB;

CREATE TABLE schema_definitions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL UNIQUE,
    description VARCHAR(500) NOT NULL DEFAULT '',
    archived_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE schema_revisions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    schema_definition_id UUID NOT NULL REFERENCES schema_definitions(id),
    revision_number BIGINT NOT NULL CHECK (revision_number > 0),
    document JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (schema_definition_id, revision_number)
);
