package io.streamforge.controlplane.service;

import io.streamforge.controlplane.api.CreatePipelineDefinitionRequest;
import io.streamforge.controlplane.api.PipelineDefinitionCreated;
import io.streamforge.controlplane.persistence.entity.InputDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.OutputDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.PipelineDefinitionEntity;
import io.streamforge.controlplane.persistence.entity.PipelineRevisionEntity;
import io.streamforge.controlplane.persistence.entity.TransformDefinitionEntity;
import io.streamforge.controlplane.persistence.repository.InputDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.OutputDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineRevisionRepository;
import io.streamforge.controlplane.persistence.repository.TransformDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates durable pipeline definitions only; execution is intentionally outside the control plane.
 */
@Service
public class PipelineDefinitionService {
  private final ConfigurationValidator validator;
  private final PipelineDefinitionRepository pipelines;
  private final InputDefinitionRepository inputs;
  private final TransformDefinitionRepository transforms;
  private final OutputDefinitionRepository outputs;
  private final PipelineRevisionRepository revisions;

  public PipelineDefinitionService(
      ConfigurationValidator validator,
      PipelineDefinitionRepository pipelines,
      InputDefinitionRepository inputs,
      TransformDefinitionRepository transforms,
      OutputDefinitionRepository outputs,
      PipelineRevisionRepository revisions) {
    this.validator = validator;
    this.pipelines = pipelines;
    this.inputs = inputs;
    this.transforms = transforms;
    this.outputs = outputs;
    this.revisions = revisions;
  }

  /** Persists one validated pipeline and its initial immutable revision atomically. */
  @Transactional
  public PipelineDefinitionCreated create(CreatePipelineDefinitionRequest request) {
    if (request == null) {
      throw new IllegalArgumentException("pipeline definition request must not be null");
    }
    String input = validator.validate(request.inputConfiguration(), "input");
    String transform = validator.validate(request.transformConfiguration(), "transform");
    String output = validator.validate(request.outputConfiguration(), "output");

    PipelineDefinitionEntity pipeline =
        pipelines.save(new PipelineDefinitionEntity(request.name()));
    InputDefinitionEntity inputDefinition =
        inputs.save(new InputDefinitionEntity(pipeline.id(), input));
    TransformDefinitionEntity transformDefinition =
        transforms.save(new TransformDefinitionEntity(pipeline.id(), transform));
    OutputDefinitionEntity outputDefinition =
        outputs.save(new OutputDefinitionEntity(pipeline.id(), output));
    PipelineRevisionEntity revision =
        revisions.save(
            new PipelineRevisionEntity(
                pipeline.id(),
                1,
                inputDefinition.id(),
                transformDefinition.id(),
                outputDefinition.id()));
    return new PipelineDefinitionCreated(pipeline.id(), revision.id(), revision.revisionNumber());
  }
}
