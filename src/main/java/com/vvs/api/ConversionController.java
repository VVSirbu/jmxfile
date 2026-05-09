package com.vvs.api;

import com.vvs.api.dto.ConversionResponse;
import com.vvs.api.dto.ErrorResponse;
import com.vvs.model.ConversionResult;
import com.vvs.service.ConversionException;
import com.vvs.service.ConversionService;
import com.vvs.storage.ArtifactStorage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversions")
public class ConversionController {

    private final ConversionService conversionService;
    private final ArtifactStorage artifactStorage;

    public ConversionController(ConversionService conversionService, ArtifactStorage artifactStorage) {
        this.conversionService = conversionService;
        this.artifactStorage = artifactStorage;
    }

    @Operation(summary = "Convert a JMeter JMX file to a k6 script")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "JMX file was converted"),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid or empty JMX file",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "415",
                    description = "Unsupported content type",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Storage or unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConversionResponse convert(@RequestPart("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new ConversionException("Uploaded JMX file is empty");
        }

        ConversionResult result = conversionService.convert(file.getOriginalFilename(), file.getInputStream());
        return new ConversionResponse(
                result.conversionId(),
                result.sourceFileName(),
                result.generatedFileName(),
                "/api/v1/conversions/" + result.conversionId() + "/script",
                result.requestCount()
        );
    }

    @Operation(summary = "Download a generated k6 script")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Generated k6 script",
                    content = @Content(mediaType = "application/javascript", schema = @Schema(type = "string", format = "binary"))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid conversion id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Conversion or generated script was not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Storage or unexpected server error",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{conversionId}/script")
    public ResponseEntity<Resource> downloadScript(@PathVariable String conversionId) {
        validateConversionId(conversionId);

        Path scriptPath = artifactStorage.findK6Script(conversionId)
                .orElseThrow(() -> new ResourceNotFoundException("Generated k6 script was not found"));

        FileSystemResource resource = new FileSystemResource(scriptPath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/javascript"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(scriptPath.getFileName().toString())
                                .build()
                                .toString()
                )
                .body(resource);
    }

    private void validateConversionId(String conversionId) {
        try {
            UUID.fromString(conversionId);
        } catch (IllegalArgumentException ex) {
            throw new ConversionException("conversionId must be a valid UUID");
        }
    }
}
