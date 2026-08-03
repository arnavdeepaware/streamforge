package io.streamforge.controlplane.persistence.repository;

import io.streamforge.controlplane.persistence.entity.TransformDefinitionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransformDefinitionRepository
    extends JpaRepository<TransformDefinitionEntity, UUID> {}
