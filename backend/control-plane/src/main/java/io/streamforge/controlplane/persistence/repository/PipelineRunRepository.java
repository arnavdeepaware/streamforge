package io.streamforge.controlplane.persistence.repository;

import io.streamforge.controlplane.execution.PipelineRunState;
import io.streamforge.controlplane.persistence.entity.PipelineRunEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PipelineRunRepository extends JpaRepository<PipelineRunEntity, UUID> {
  Optional<PipelineRunEntity> findTopByPipelineDefinitionIdOrderByCreatedAtDesc(
      UUID pipelineDefinitionId);

  List<PipelineRunEntity> findByStateIn(Collection<PipelineRunState> states);
}
