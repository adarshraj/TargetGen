package in.adarshr.targetgen.model;

import in.adarshr.targetgen.exception.TargetGenException;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Arrays;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Report {
    private String status;
    private String group;
    private String artifact;
    private String version;
    private String classifier;
    private String extension;

    public static Report fromDelimitedString(String currentLine, String delimiter) {
        String[] parts = Arrays.stream(currentLine.split(delimiter))
                .map(String::trim)
                .toArray(String[]::new);
        if (parts.length < 6) {
            throw new TargetGenException("Insufficient fields to create a Report object: " + currentLine);
        }
        return new Report(parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]);
    }
}
