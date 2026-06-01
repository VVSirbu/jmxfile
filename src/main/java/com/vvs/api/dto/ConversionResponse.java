package com.vvs.api.dto;

public record ConversionResponse(
        String conversionId,
        String sourceFileName,
        String generatedFileName,
        String downloadUrl,
        String bundleUrl,
        int resourceCount,
        int requestCount
) {
}
