package com.vvs.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
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
    public Path saveSupportFile(String conversionId, String fileName, InputStream content) {
        try {
            Path conversionDir = artifactsDir.resolve(conversionId);
            Files.createDirectories(conversionDir);

            Path supportFilePath = conversionDir.resolve(toSafeFileName(fileName));
            Files.copy(content, supportFilePath, StandardCopyOption.REPLACE_EXISTING);
            return supportFilePath.toAbsolutePath().normalize();
        } catch (IOException ex) {
            throw new ArtifactStorageException("Failed to save support file", ex);
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

    @Override
    public List<Path> listArtifacts(String conversionId) {
        Path conversionDir = artifactsDir.resolve(conversionId);
        if (!Files.isDirectory(conversionDir)) {
            return List.of();
        }

        try (Stream<Path> files = Files.list(conversionDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList();
        } catch (IOException ex) {
            throw new ArtifactStorageException("Failed to list conversion artifacts", ex);
        }
    }

    private String toGeneratedFileName(String sourceFileName) {
        String safeName = toSafeFileName(sourceFileName);

        if (safeName.endsWith(".jmx")) {
            safeName = safeName.substring(0, safeName.length() - 4);
        }

        return safeName + ".k6.js";
    }

    private String toSafeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "converted";
        }

        String normalized = fileName.replace("\\", "/");
        int slash = normalized.lastIndexOf('/');
        String baseName = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        String safeName = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safeName.isBlank() || ".".equals(safeName) || "..".equals(safeName) ? "converted" : safeName;
    }
}
