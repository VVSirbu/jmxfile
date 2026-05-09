package com.vvs.storage;

import java.nio.file.Path;
import java.util.Optional;

public interface ArtifactStorage {

    Path saveK6Script(String conversionId, String sourceFileName, String content);

    Optional<Path> findK6Script(String conversionId);
}
