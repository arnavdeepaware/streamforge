package io.streamforge.controlplane.persistence.repository;

import io.streamforge.controlplane.persistence.entity.SchemaRevisionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemaRevisionRepository extends JpaRepository<SchemaRevisionEntity, UUID> {
  Optional<SchemaRevisionEntity> findFirstBySchemaDefinitionIdOrderByRevisionNumberDesc(
      UUID schemaDefinitionId);
}
