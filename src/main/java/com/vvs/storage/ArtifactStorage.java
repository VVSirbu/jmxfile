package com.vvs.storage;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public interface ArtifactStorage {

    Path saveK6Script(String conversionId, String sourceFileName, String content);

    Path saveSupportFile(String conversionId, String fileName, InputStream content);

    Optional<Path> findK6Script(String conversionId);

    List<Path> listArtifacts(String conversionId);
}
