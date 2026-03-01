package org.asamk.signal.json;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/openapi/json")
class JsonOpenApiController {

    @GetMapping("/models")
    @Operation(summary = "Signal JSON model schema catalog")
    @ApiResponse(responseCode = "200", description = "Catalog of Signal JSON model schemas", content = @Content(mediaType = "application/json", schema = @Schema(implementation = JsonSchemaCatalog.class)))
    JsonSchemaCatalog getModels() {
        return new JsonSchemaCatalog();
    }
}
