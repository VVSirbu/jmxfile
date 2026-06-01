package com.vvs.model;

import java.io.InputStream;

public record SupportFile(
        String fileName,
        InputStream content
) {
}
