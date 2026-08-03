package io.streamforge.controlplane;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.streamforge.controlplane.api.CreatePipelineDefinitionRequest;
import io.streamforge.controlplane.api.PipelineDefinitionCreated;
import io.streamforge.controlplane.persistence.entity.PipelineDefinitionEntity;
import io.streamforge.controlplane.persistence.repository.InputDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.OutputDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineDefinitionRepository;
import io.streamforge.controlplane.persistence.repository.PipelineRevisionRepository;
import io.streamforge.controlplane.persistence.repository.TransformDefinitionRepository;
import io.streamforge.controlplane.service.ConfigurationValidationException;
import io.streamforge.controlplane.service.PipelineDefinitionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ControlPlanePersistenceIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16.8-alpine");

  @DynamicPropertySource
  static void databaseProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }

  @Autowired private PipelineDefinitionService service;
  @Autowired private PipelineDefinitionRepository pipelines;
  @Autowired private InputDefinitionRepository inputs;
  @Autowired private TransformDefinitionRepository transforms;
  @Autowired private OutputDefinitionRepository outputs;
  @Autowired private PipelineRevisionRepository revisions;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void migratesAnEmptyDatabaseAndPersistsValidatedDefinitionComponents() {
    PipelineDefinitionCreated created =
        service.create(
            new CreatePipelineDefinitionRequest(
                "nasdaq-feed",
                "{\"type\":\"STP_BINARY\"}",
                "{\"schemaVersion\":\"1.0\",\"operations\":[]}",
                "{\"type\":\"JSONL\"}"));

    PipelineDefinitionEntity pipeline =
        pipelines.findById(created.pipelineDefinitionId()).orElseThrow();
    assertThat(pipeline.name()).isEqualTo("nasdaq-feed");
    assertThat(pipeline.createdAt()).isNotNull();
    assertThat(pipeline.updatedAt()).isNotNull();
    assertThat(inputs.findAll()).hasSize(1);
    assertThat(transforms.findAll()).hasSize(1);
    assertThat(outputs.findAll()).hasSize(1);
    assertThat(revisions.findById(created.revisionId()).orElseThrow().revisionNumber())
        .isEqualTo(1);
  }

  @Test
  void rejectsCredentialMaterialBeforePersistence() {
    assertThatThrownBy(
            () ->
                service.create(
                    new CreatePipelineDefinitionRequest(
                        "unsafe-feed", "{\"password\":\"not-allowed\"}", "{}", "{}")))
        .isInstanceOf(ConfigurationValidationException.class)
        .hasMessageContaining("credential-like");
    assertThat(pipelines.findAll()).noneMatch(pipeline -> pipeline.name().equals("unsafe-feed"));
  }

  @Test
  void enforcesOptimisticLocking() {
    PipelineDefinitionEntity created =
        pipelines.saveAndFlush(new PipelineDefinitionEntity("versioned-feed"));
    PipelineDefinitionEntity stale = pipelines.findById(created.id()).orElseThrow();
    PipelineDefinitionEntity current = pipelines.findById(created.id()).orElseThrow();

    current.rename("versioned-feed-current");
    pipelines.saveAndFlush(current);
    stale.rename("versioned-feed-stale");

    assertThatThrownBy(() -> pipelines.saveAndFlush(stale))
        .isInstanceOf(OptimisticLockingFailureException.class);
  }

  @Test
  void servesVersionedPipelineLifecycleValidationAndProblemDetails() throws Exception {
    String pipeline =
        """
        {
          "name":"api-pipeline-%s",
          "description":"API lifecycle test",
          "configuration":%s
        }
        """
            .formatted(java.util.UUID.randomUUID(), pipelineConfiguration());

    String response =
        mockMvc
            .perform(post("/api/v1/pipelines").contentType("application/json").content(pipeline))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.latestRevision.revisionNumber").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String id = objectMapper.readTree(response).get("id").asText();

    mockMvc
        .perform(get("/api/v1/pipelines/{id}/runs/latest", id))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            post("/api/v1/pipelines/{id}/runs", id)
                .contentType("application/json")
                .content(
                    """
                    {"deadLetter":{"policy":"QUARANTINE","includePayload":true,"maximumPayloadBytes":0}}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].field").value("deadLetter"));
    mockMvc
        .perform(
            post("/api/v1/pipelines/validate")
                .contentType("application/json")
                .content("{\"configuration\":" + pipelineConfiguration() + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true));
    mockMvc
        .perform(
            post("/api/v1/pipelines/{id}/revisions", id)
                .contentType("application/json")
                .content("{\"configuration\":" + pipelineConfiguration() + "}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.latestRevision.revisionNumber").value(2));
    mockMvc
        .perform(get("/api/v1/pipelines/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.latestRevision.revisionNumber").value(2));
    mockMvc
        .perform(
            patch("/api/v1/pipelines/{id}", id)
                .contentType("application/json")
                .content("{\"name\":\"renamed-pipeline\",\"description\":\"updated\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("renamed-pipeline"));
    mockMvc
        .perform(post("/api/v1/pipelines/{id}/archive", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.archived").value(true));
    mockMvc
        .perform(
            post("/api/v1/pipelines/{id}/runs", id).contentType("application/json").content("{}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Resource conflict"));
    mockMvc
        .perform(
            post("/api/v1/pipelines/validate")
                .contentType("application/json")
                .content("{\"configuration\":{\"transform\":{},\"blueprint\":{},\"output\":{}}}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation failed"))
        .andExpect(jsonPath("$.errors[0].field").exists());
  }

  @Test
  void servesVersionedSchemaLifecycleAndOpenApiDescription() throws Exception {
    String document =
        "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\"}";
    String response =
        mockMvc
            .perform(
                post("/api/v1/schemas")
                    .contentType("application/json")
                    .content(
                        "{\"name\":\"trade-schema-"
                            + java.util.UUID.randomUUID()
                            + "\",\"description\":\"trade schema\",\"document\":"
                            + document
                            + "}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.latestRevision.revisionNumber").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode created = objectMapper.readTree(response);
    String id = created.get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/schemas/{id}/revisions", id)
                .contentType("application/json")
                .content("{\"document\":" + document + "}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.latestRevision.revisionNumber").value(2));
    mockMvc.perform(get("/api/v1/schemas/{id}", id)).andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/schemas/{id}/archive", id))
        .andExpect(jsonPath("$.archived").value(true));
    mockMvc
        .perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.info.title").value("StreamForge Control Plane API"));
  }

  private static String pipelineConfiguration() {
    return """
        {
          "input":{"type":"STP_BINARY"},
          "transform":{"schemaVersion":"1.0","operations":[{"op":"rename","from":"instrument.symbol","to":"instrument.ticker"}]},
          "blueprint":{"schemaVersion":"1.0","output":{"kind":"object","fields":{"symbol":{"kind":"reference","source":"canonical","path":"instrument.symbol"}}}},
          "output":{"type":"JSONL"}
        }
        """;
  }
}
