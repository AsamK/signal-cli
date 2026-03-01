package org.asamk.signal.openapi;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = "org.asamk.signal")
@OpenAPIDefinition(info = @Info(title = "signal-cli JSON Models", version = "v1"))
public class OpenApiDocumentationApplication {

    public static void main(String[] args) {
        SpringApplication.run(OpenApiDocumentationApplication.class, args);
    }

    @Bean
    GroupedOpenApi jsonModelsOpenApi() {
        return GroupedOpenApi.builder()
                .group("json-models")
                .packagesToScan("org.asamk.signal.json")
                .pathsToMatch("/openapi/json/models")
                .build();
    }
}
