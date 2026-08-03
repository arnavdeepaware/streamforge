package io.streamforge.controlplane;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/** OpenAPI metadata for the version-one control-plane REST surface. */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "StreamForge Control Plane API",
            version = "v1",
            description =
                "Versioned definition, local-run lifecycle, monitoring, and validation APIs."))
public class OpenApiConfiguration {}
