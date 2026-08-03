package io.streamforge.controlplane.persistence.repository;

import io.streamforge.controlplane.persistence.entity.InputDefinitionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InputDefinitionRepository extends JpaRepository<InputDefinitionEntity, UUID> {}
