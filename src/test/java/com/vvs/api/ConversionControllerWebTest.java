package com.vvs.api;

import com.vvs.model.ConversionResult;
import com.vvs.service.ConversionException;
import com.vvs.service.ConversionService;
import com.vvs.storage.ArtifactStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConversionController.class)
class ConversionControllerWebTest {

    private static final String VALID_CONVERSION_ID = "0e9cd925-a33f-4b89-8cc8-a5e94c111008";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ConversionService conversionService;

    @MockBean
    private ArtifactStorage artifactStorage;

    @TempDir
    private Path tempDir;

    @Test
    void convertsMultipartJmxFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jmx",
                MediaType.APPLICATION_XML_VALUE,
                "<jmeterTestPlan/>".getBytes()
        );
        Path generatedPath = Path.of("artifacts", VALID_CONVERSION_ID, "test.k6.js");
        ConversionResult result = new ConversionResult(
                VALID_CONVERSION_ID,
                "test.jmx",
                "test.k6.js",
                generatedPath,
                1
        );

        when(conversionService.convert(eq("test.jmx"), any(InputStream.class), anyList())).thenReturn(result);

        mockMvc.perform(multipart("/api/v1/conversions").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversionId").value(VALID_CONVERSION_ID))
                .andExpect(jsonPath("$.sourceFileName").value("test.jmx"))
                .andExpect(jsonPath("$.generatedFileName").value("test.k6.js"))
                .andExpect(jsonPath("$.downloadUrl").value("/api/v1/conversions/" + VALID_CONVERSION_ID + "/script"))
                .andExpect(jsonPath("$.bundleUrl").value("/api/v1/conversions/" + VALID_CONVERSION_ID + "/bundle"))
                .andExpect(jsonPath("$.resourceCount").value(0))
                .andExpect(jsonPath("$.requestCount").value(1));
    }

    @Test
    void convertsMultipartJmxFileWithSupportResources() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.jmx",
                MediaType.APPLICATION_XML_VALUE,
                "<jmeterTestPlan/>".getBytes()
        );
        MockMultipartFile csv = new MockMultipartFile(
                "resources",
                "opencart_test_users.csv",
                "text/csv",
                "email,password\nuser@example.com,secret\n".getBytes()
        );
        ConversionResult result = new ConversionResult(
                VALID_CONVERSION_ID,
                "test.jmx",
                "test.k6.js",
                Path.of("artifacts", VALID_CONVERSION_ID, "test.k6.js"),
                1
        );

        when(conversionService.convert(eq("test.jmx"), any(InputStream.class), anyList())).thenReturn(result);

        mockMvc.perform(multipart("/api/v1/conversions").file(file).file(csv))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceCount").value(1))
                .andExpect(jsonPath("$.bundleUrl").value("/api/v1/conversions/" + VALID_CONVERSION_ID + "/bundle"));
    }

    @Test
    void returnsBadRequestForEmptyUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.jmx",
                MediaType.APPLICATION_XML_VALUE,
                new byte[0]
        );

        mockMvc.perform(multipart("/api/v1/conversions").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Uploaded JMX file is empty"));
    }

    @Test
    void returnsBadRequestForMissingFilePart() throws Exception {
        mockMvc.perform(multipart("/api/v1/conversions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @Test
    void returnsBadRequestForInvalidJmx() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.jmx",
                MediaType.APPLICATION_XML_VALUE,
                "<not-jmx/>".getBytes()
        );

        when(conversionService.convert(eq("broken.jmx"), any(InputStream.class), anyList()))
                .thenThrow(new ConversionException("JMX file does not contain HTTPSamplerProxy elements"));

        mockMvc.perform(multipart("/api/v1/conversions").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("JMX file does not contain HTTPSamplerProxy elements"));
    }

    @Test
    void returnsUnsupportedMediaTypeForNonMultipartConversionRequest() throws Exception {
        mockMvc.perform(post("/api/v1/conversions")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not multipart"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.error").value("Unsupported Media Type"));
    }

    @Test
    void returnsMethodNotAllowedForWrongMethod() throws Exception {
        mockMvc.perform(get("/api/v1/conversions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));
    }

    @Test
    void returnsBadRequestForInvalidConversionId() throws Exception {
        mockMvc.perform(get("/api/v1/conversions/not-a-uuid/script"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("conversionId must be a valid UUID"));

        verify(artifactStorage, never()).findK6Script(any());
    }

    @Test
    void returnsNotFoundForMissingGeneratedScript() throws Exception {
        when(artifactStorage.findK6Script(VALID_CONVERSION_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/conversions/" + VALID_CONVERSION_ID + "/script"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Generated k6 script was not found"));
    }

    @Test
    void downloadsGeneratedScript() throws Exception {
        Path scriptPath = tempDir.resolve("test.k6.js");
        Files.writeString(scriptPath, "export default function () {}\n");
        when(artifactStorage.findK6Script(VALID_CONVERSION_ID)).thenReturn(Optional.of(scriptPath));

        mockMvc.perform(get("/api/v1/conversions/" + VALID_CONVERSION_ID + "/script"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/javascript"))
                .andExpect(header().string("Content-Disposition", containsString("filename=\"test.k6.js\"")));
    }

    @Test
    void downloadsBundleWithGeneratedScriptAndSupportFiles() throws Exception {
        Path scriptPath = tempDir.resolve("test.k6.js");
        Path csvPath = tempDir.resolve("opencart_test_users.csv");
        Files.writeString(scriptPath, "export default function () {}\n");
        Files.writeString(csvPath, "email,password\nuser@example.com,secret\n");
        when(artifactStorage.listArtifacts(VALID_CONVERSION_ID)).thenReturn(List.of(csvPath, scriptPath));

        mockMvc.perform(get("/api/v1/conversions/" + VALID_CONVERSION_ID + "/bundle"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andExpect(header().string("Content-Disposition", containsString("filename=\"" + VALID_CONVERSION_ID + ".k6-bundle.zip\"")));
    }
}
