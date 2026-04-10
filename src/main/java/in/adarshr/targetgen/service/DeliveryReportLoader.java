package in.adarshr.targetgen.service;

import in.adarshr.targetgen.exception.TargetGenException;
import in.adarshr.targetgen.model.Report;
import in.adarshr.targetgen.schema.input.ComponentInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class DeliveryReportLoader {

    private static final int HEADER_LINES_TO_SKIP = 2;
    private static final String FIELD_DELIMITER = ":";
    private static final String VERSION_PLACEHOLDER = "$VERSION$";

    public List<Report> load(ComponentInfo componentInfo) {
        String location = expandVersion(componentInfo.getReportLocation(), componentInfo.getVersion());
        List<Report> raw = isUrl(location) ? readFromUrl(location) : readFromFile(Path.of(location));
        log.info("Loaded {} delivery report rows from {}", raw.size(), location);
        return filterByExtension(raw, componentInfo.getReportExtensionToUse());
    }

    private static String expandVersion(String location, String version) {
        if (location == null) {
            throw new TargetGenException("reportLocation is not set in input.xml");
        }
        return location.contains(VERSION_PLACEHOLDER) ? location.replace(VERSION_PLACEHOLDER, version) : location;
    }

    static boolean isUrl(String location) {
        try {
            URI uri = new URI(location);
            return uri.isAbsolute() && uri.getScheme() != null && !"file".equalsIgnoreCase(uri.getScheme());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private List<Report> readFromFile(Path file) {
        try (Stream<String> lines = Files.lines(file)) {
            return parse(lines);
        } catch (IOException e) {
            throw new TargetGenException("Failed to read delivery report file: " + file, e);
        }
    }

    private List<Report> readFromUrl(String url) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(URI.create(url).toURL().openStream()))) {
            return parse(reader.lines());
        } catch (IOException e) {
            throw new TargetGenException("Failed to read delivery report URL: " + url, e);
        }
    }

    private List<Report> parse(Stream<String> lines) {
        List<Report> reports = new ArrayList<>();
        lines.skip(HEADER_LINES_TO_SKIP).forEach(line -> {
            if (!line.isBlank()) {
                reports.add(Report.fromDelimitedString(line, FIELD_DELIMITER));
            }
        });
        return reports;
    }

    private static List<Report> filterByExtension(List<Report> reports, String wantedExtension) {
        return reports.stream()
                .filter(r -> StringUtils.isNotEmpty(r.getExtension())
                        && r.getExtension().equalsIgnoreCase(wantedExtension))
                .collect(Collectors.toList());
    }
}
