package com.vvs.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

@Component
public class FileSystemArtifactStorage implements ArtifactStorage {

    private final Path artifactsDir;

    public FileSystemArtifactStorage(@Value("${jmxtok6.artifacts-dir}") String artifactsDir) {
        this.artifactsDir = Path.of(artifactsDir);
    }

    @Override
    public Path saveK6Script(String conversionId, String sourceFileName, String content) {
        try {
            Path conversionDir = artifactsDir.resolve(conversionId);
            Files.createDirectories(conversionDir);

            Path scriptPath = conversionDir.resolve(toGeneratedFileName(sourceFileName));
            Files.writeString(scriptPath, content, StandardCharsets.UTF_8);
            return scriptPath.toAbsolutePath().normalize();
        } catch (IOException ex) {
            throw new ArtifactStorageException("Failed to save generated k6 script", ex);
        }
    }

    @Override
    public Optional<Path> findK6Script(String conversionId) {
        Path conversionDir = artifactsDir.resolve(conversionId);
        if (!Files.isDirectory(conversionDir)) {
            return Optional.empty();
        }

        try (Stream<Path> files = Files.list(conversionDir)) {
            return files
                    .filter(path -> path.getFileName().toString().endsWith(".js"))
                    .findFirst()
                    .map(path -> path.toAbsolutePath().normalize());
        } catch (IOException ex) {
            throw new ArtifactStorageException("Failed to read conversion artifacts", ex);
        }
    }

    private String toGeneratedFileName(String sourceFileName) {
        String safeName = sourceFileName == null || sourceFileName.isBlank()
                ? "converted"
                : sourceFileName.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (safeName.endsWith(".jmx")) {
            safeName = safeName.substring(0, safeName.length() - 4);
        }

        return safeName + ".k6.js";
    }
}
