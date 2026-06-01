package com.vvs.service;

import com.vvs.converter.K6ScriptGenerator;
import com.vvs.model.ConversionResult;
import com.vvs.model.JmxTestPlan;
import com.vvs.model.SupportFile;
import com.vvs.parser.JmxParser;
import com.vvs.storage.ArtifactStorage;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class ConversionService {

    private final JmxParser jmxParser;
    private final K6ScriptGenerator k6ScriptGenerator;
    private final ArtifactStorage artifactStorage;

    public ConversionService(
            JmxParser jmxParser,
            K6ScriptGenerator k6ScriptGenerator,
            ArtifactStorage artifactStorage
    ) {
        this.jmxParser = jmxParser;
        this.k6ScriptGenerator = k6ScriptGenerator;
        this.artifactStorage = artifactStorage;
    }

    public ConversionResult convert(String sourceFileName, InputStream jmxInputStream) {
        return convert(sourceFileName, jmxInputStream, List.of());
    }

    public ConversionResult convert(String sourceFileName, InputStream jmxInputStream, List<SupportFile> supportFiles) {
        if (jmxInputStream == null) {
            throw new ConversionException("JMX input stream is required");
        }

        String conversionId = UUID.randomUUID().toString();
        JmxTestPlan testPlan = jmxParser.parse(jmxInputStream);

        if (testPlan.requests().isEmpty()) {
            throw new ConversionException("JMX file does not contain HTTPSamplerProxy elements");
        }

        String k6Script = k6ScriptGenerator.generate(testPlan);
        Path generatedFilePath = artifactStorage.saveK6Script(conversionId, sourceFileName, k6Script);
        for (SupportFile supportFile : supportFiles) {
            artifactStorage.saveSupportFile(conversionId, supportFile.fileName(), supportFile.content());
        }

        return new ConversionResult(
                conversionId,
                sourceFileName,
                generatedFilePath.getFileName().toString(),
                generatedFilePath,
                testPlan.requests().size()
        );
    }
}
