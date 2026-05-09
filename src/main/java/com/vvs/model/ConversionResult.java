package com.vvs.model;

import java.nio.file.Path;

public record ConversionResult(
        String conversionId,
        String sourceFileName,
        String generatedFileName,
        Path generatedFilePath,
        int requestCount
) {
}
