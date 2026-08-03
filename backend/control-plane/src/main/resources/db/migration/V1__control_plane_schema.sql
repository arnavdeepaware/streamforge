CREATE TABLE pipeline_definitions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE input_definitions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    pipeline_definition_id UUID NOT NULL REFERENCES pipeline_definitions(id),
    configuration JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE transform_definitions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    pipeline_definition_id UUID NOT NULL REFERENCES pipeline_definitions(id),
    configuration JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE output_definitions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    pipeline_definition_id UUID NOT NULL REFERENCES pipeline_definitions(id),
    configuration JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE pipeline_revisions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL,
    pipeline_definition_id UUID NOT NULL REFERENCES pipeline_definitions(id),
    revision_number BIGINT NOT NULL CHECK (revision_number > 0),
    input_definition_id UUID NOT NULL REFERENCES input_definitions(id),
    transform_definition_id UUID NOT NULL REFERENCES transform_definitions(id),
    output_definition_id UUID NOT NULL REFERENCES output_definitions(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (pipeline_definition_id, revision_number)
);
