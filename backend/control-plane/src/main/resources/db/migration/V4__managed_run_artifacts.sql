ALTER TABLE pipeline_runs
    ADD COLUMN output_artifact_path VARCHAR(1024),
    ADD COLUMN dead_letter_artifact_path VARCHAR(1024);
