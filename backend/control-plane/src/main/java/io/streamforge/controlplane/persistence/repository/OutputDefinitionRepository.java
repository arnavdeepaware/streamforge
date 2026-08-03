package io.streamforge.controlplane.persistence.repository;

import io.streamforge.controlplane.persistence.entity.OutputDefinitionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutputDefinitionRepository extends JpaRepository<OutputDefinitionEntity, UUID> {}
