package com.vvs.api;

import com.vvs.api.dto.ConversionResponse;
import com.vvs.api.dto.ErrorResponse;
import com.vvs.model.ConversionResult;
import com.vvs.model.SupportFile;
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
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
    public ConversionResponse convert(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "resources", required = false) List<MultipartFile> resources
    ) throws IOException {
        if (file.isEmpty()) {
            throw new ConversionException("Uploaded JMX file is empty");
        }

        List<MultipartFile> supportResources = resources == null ? List.of() : resources.stream()
                .filter(resource -> !resource.isEmpty())
                .toList();
        List<SupportFile> supportFiles = supportResources.stream()
                .map(resource -> toSupportFile(resource))
                .toList();

        ConversionResult result = conversionService.convert(file.getOriginalFilename(), file.getInputStream(), supportFiles);
        return new ConversionResponse(
                result.conversionId(),
                result.sourceFileName(),
                result.generatedFileName(),
                "/api/v1/conversions/" + result.conversionId() + "/script",
                "/api/v1/conversions/" + result.conversionId() + "/bundle",
                supportResources.size(),
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

    @Operation(summary = "Download generated k6 script and support files as a zip bundle")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Generated k6 bundle",
                    content = @Content(mediaType = "application/zip", schema = @Schema(type = "string", format = "binary"))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid conversion id",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Conversion artifacts were not found",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class))
            )
    })
    @GetMapping("/{conversionId}/bundle")
    public ResponseEntity<byte[]> downloadBundle(@PathVariable String conversionId) throws IOException {
        validateConversionId(conversionId);

        List<Path> artifacts = artifactStorage.listArtifacts(conversionId);
        if (artifacts.isEmpty()) {
            throw new ResourceNotFoundException("Conversion artifacts were not found");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (Path artifact : artifacts) {
                zipOutputStream.putNextEntry(new ZipEntry(artifact.getFileName().toString()));
                zipOutputStream.write(Files.readAllBytes(artifact));
                zipOutputStream.closeEntry();
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(conversionId + ".k6-bundle.zip")
                                .build()
                                .toString()
                )
                .body(outputStream.toByteArray());
    }

    private SupportFile toSupportFile(MultipartFile resource) {
        try {
            return new SupportFile(resource.getOriginalFilename(), resource.getInputStream());
        } catch (IOException ex) {
            throw new ConversionException("Failed to read support file: " + resource.getOriginalFilename(), ex);
        }
    }

    private void validateConversionId(String conversionId) {
        try {
            UUID.fromString(conversionId);
        } catch (IllegalArgumentException ex) {
            throw new ConversionException("conversionId must be a valid UUID");
        }
    }
}
