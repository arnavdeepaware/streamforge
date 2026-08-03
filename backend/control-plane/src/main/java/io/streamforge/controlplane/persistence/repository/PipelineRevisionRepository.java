package io.streamforge.controlplane.persistence.repository;

import io.streamforge.controlplane.persistence.entity.PipelineRevisionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRevisionRepository extends JpaRepository<PipelineRevisionEntity, UUID> {
  Optional<PipelineRevisionEntity> findFirstByPipelineDefinitionIdOrderByRevisionNumberDesc(
      UUID pipelineDefinitionId);
}
