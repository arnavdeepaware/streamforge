package io.streamforge.controlplane.persistence.repository;

import io.streamforge.controlplane.persistence.entity.PipelineDefinitionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineDefinitionRepository
    extends JpaRepository<PipelineDefinitionEntity, UUID> {}
