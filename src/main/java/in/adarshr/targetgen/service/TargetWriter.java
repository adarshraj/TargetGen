package in.adarshr.targetgen.service;

import in.adarshr.targetgen.exception.TargetGenException;
import in.adarshr.targetgen.schema.output.Target;
import in.adarshr.targetgen.utils.JaxbUtils;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class TargetWriter {

    private final Path outputDir;

    public TargetWriter(Path outputDir) {
        this.outputDir = outputDir;
    }

    public void writeAll(Map<String, Target> targets) {
        try {
            Files.createDirectories(outputDir);
        } catch (IOException e) {
            throw new TargetGenException("Failed to create output directory: " + outputDir, e);
        }
        targets.forEach(this::writeOne);
    }

    private void writeOne(String fileName, Target target) {
        Path file = outputDir.resolve(fileName);
        try {
            String xml = JaxbUtils.marshalWithInstruction(target, Target.class);
            Files.writeString(file, xml, StandardCharsets.UTF_8);
            log.info("Wrote target file: {}", file);
        } catch (JAXBException | IOException e) {
            throw new TargetGenException("Failed to write target file: " + file, e);
        }
    }
}
