package in.adarshr.targetgen.service;

import in.adarshr.targetgen.exception.TargetGenException;
import in.adarshr.targetgen.model.ComparisonReport;
import in.adarshr.targetgen.model.ComparisonReport.ComponentDiff;
import in.adarshr.targetgen.model.ComparisonReport.LocationDiff;
import in.adarshr.targetgen.model.ComparisonReport.LocationStatus;
import in.adarshr.targetgen.model.ComparisonReport.UnitDiff;
import in.adarshr.targetgen.model.ComparisonReport.UnitStatus;
import in.adarshr.targetgen.model.ResolvedComponent;
import in.adarshr.targetgen.schema.output.Location;
import in.adarshr.targetgen.schema.output.Target;
import in.adarshr.targetgen.service.PreviousTargetParser.PreviousLocation;
import in.adarshr.targetgen.service.PreviousTargetParser.PreviousTarget;
import in.adarshr.targetgen.service.PreviousTargetParser.PreviousUnit;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
public class TargetComparator {

    private static final String COMPONENT_PLACEHOLDER = "$COMPONENT$";
    private static final String VERSION_PLACEHOLDER = "$VERSION$";

    private final PreviousTargetParser parser = new PreviousTargetParser();

    public ComparisonReport compare(
            List<ResolvedComponent> components,
            Map<String, Target> newTargets,
            String targetSaveFormat,
            String version,
            Path previousLocation
    ) {
        List<Path> previousFiles = listPreviousTargetFiles(previousLocation);
        Set<Path> matched = new HashSet<>();
        List<ComponentDiff> componentDiffs = new ArrayList<>();

        for (ResolvedComponent component : components) {
            String newFileName = renderFileName(targetSaveFormat, component.name(), version);
            Target newTarget = newTargets.get(newFileName);
            if (newTarget == null) {
                log.warn("No newly-built target found for component {} (expected file: {})", component.name(), newFileName);
                continue;
            }

            Optional<Path> previousFile = findPreviousFile(previousFiles, targetSaveFormat, component.name());
            if (previousFile.isEmpty()) {
                componentDiffs.add(allNewComponentDiff(component.name(), newFileName, newTarget));
                continue;
            }
            matched.add(previousFile.get());

            PreviousTarget previous = parser.parse(previousFile.get());
            componentDiffs.add(diffOneComponent(
                    component.name(),
                    previousFile.get().getFileName().toString(),
                    newFileName,
                    previous,
                    newTarget));
        }

        List<String> removed = previousFiles.stream()
                .filter(p -> !matched.contains(p))
                .map(p -> p.getFileName().toString())
                .sorted()
                .toList();

        return new ComparisonReport(componentDiffs, removed);
    }

    private static List<Path> listPreviousTargetFiles(Path previousLocation) {
        if (!Files.exists(previousLocation)) {
            throw new TargetGenException("--compare path does not exist: " + previousLocation);
        }
        if (Files.isRegularFile(previousLocation)) {
            return List.of(previousLocation);
        }
        try (Stream<Path> stream = Files.list(previousLocation)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".target"))
                    .toList();
        } catch (IOException e) {
            throw new TargetGenException("Failed to list previous target directory: " + previousLocation, e);
        }
    }

    static Optional<Path> findPreviousFile(List<Path> previousFiles, String saveFormat, String componentName) {
        Pattern pattern = buildFilenamePattern(saveFormat, componentName);
        return previousFiles.stream()
                .filter(p -> pattern.matcher(p.getFileName().toString()).matches())
                .max(Comparator.comparing(p -> p.getFileName().toString()));
    }

    static Pattern buildFilenamePattern(String saveFormat, String componentName) {
        StringBuilder regex = new StringBuilder("^");
        int i = 0;
        while (i < saveFormat.length()) {
            if (saveFormat.startsWith(COMPONENT_PLACEHOLDER, i)) {
                regex.append(Pattern.quote(componentName));
                i += COMPONENT_PLACEHOLDER.length();
            } else if (saveFormat.startsWith(VERSION_PLACEHOLDER, i)) {
                regex.append(".+");
                i += VERSION_PLACEHOLDER.length();
            } else {
                char c = saveFormat.charAt(i);
                if ("\\.+*?()[]{}|^$".indexOf(c) >= 0) {
                    regex.append('\\');
                }
                regex.append(c);
                i++;
            }
        }
        regex.append("\\.target$");
        return Pattern.compile(regex.toString());
    }

    static String renderFileName(String saveFormat, String componentName, String version) {
        return saveFormat
                .replace(COMPONENT_PLACEHOLDER, componentName)
                .replace(VERSION_PLACEHOLDER, version) + ".target";
    }

    private static ComponentDiff allNewComponentDiff(String componentName, String newFileName, Target newTarget) {
        List<LocationDiff> locationDiffs = new ArrayList<>();
        for (Location loc : newTarget.getLocations().getLocation()) {
            List<UnitDiff> unitDiffs = loc.getUnit().stream()
                    .map(u -> new UnitDiff(u.getId(), null, u.getVersion(), UnitStatus.ADDED))
                    .toList();
            locationDiffs.add(new LocationDiff(
                    null,
                    loc.getRepository().getLocation(),
                    LocationStatus.ADDED,
                    unitDiffs));
        }
        return new ComponentDiff(componentName, null, newFileName, true, locationDiffs);
    }

    private ComponentDiff diffOneComponent(
            String componentName,
            String previousFileName,
            String newFileName,
            PreviousTarget previous,
            Target newTarget
    ) {
        List<Location> newLocations = new ArrayList<>(newTarget.getLocations().getLocation());
        List<PreviousLocation> previousLocations = new ArrayList<>(previous.locations());
        Set<PreviousLocation> usedPrevious = new HashSet<>();
        List<LocationDiff> diffs = new ArrayList<>();

        for (Location newLoc : newLocations) {
            String newUrl = newLoc.getRepository() != null ? newLoc.getRepository().getLocation() : "";
            PreviousLocation match = findMatchingPrevious(newUrl, previousLocations, usedPrevious);
            if (match == null) {
                diffs.add(new LocationDiff(null, newUrl, LocationStatus.ADDED,
                        toAddedUnits(newLoc)));
            } else {
                usedPrevious.add(match);
                LocationStatus status = newUrl.equals(match.url())
                        ? LocationStatus.UNCHANGED
                        : LocationStatus.VERSION_BUMPED;
                diffs.add(new LocationDiff(match.url(), newUrl, status,
                        diffUnits(match.units(), toUnitRefs(newLoc))));
            }
        }
        for (PreviousLocation prev : previousLocations) {
            if (!usedPrevious.contains(prev)) {
                List<UnitDiff> removed = prev.units().stream()
                        .map(u -> new UnitDiff(u.id(), u.version(), null, UnitStatus.REMOVED))
                        .toList();
                diffs.add(new LocationDiff(prev.url(), null, LocationStatus.REMOVED, removed));
            }
        }
        return new ComponentDiff(componentName, previousFileName, newFileName, false, diffs);
    }

    private static PreviousLocation findMatchingPrevious(
            String newUrl,
            List<PreviousLocation> previousLocations,
            Set<PreviousLocation> used
    ) {
        for (PreviousLocation prev : previousLocations) {
            if (!used.contains(prev) && newUrl.equals(prev.url())) {
                return prev;
            }
        }
        for (PreviousLocation prev : previousLocations) {
            if (!used.contains(prev) && urlsDifferOnlyInOneSegment(newUrl, prev.url())) {
                return prev;
            }
        }
        return null;
    }

    static boolean urlsDifferOnlyInOneSegment(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        String[] as = a.split("/");
        String[] bs = b.split("/");
        if (as.length != bs.length) {
            return false;
        }
        int differing = 0;
        for (int i = 0; i < as.length; i++) {
            if (!as[i].equals(bs[i])) {
                differing++;
                if (differing > 1) {
                    return false;
                }
            }
        }
        return differing == 1;
    }

    private static List<UnitDiff> toAddedUnits(Location location) {
        return location.getUnit().stream()
                .map(u -> new UnitDiff(u.getId(), null, u.getVersion(), UnitStatus.ADDED))
                .toList();
    }

    private static List<PreviousUnit> toUnitRefs(Location location) {
        return location.getUnit().stream()
                .map(u -> new PreviousUnit(u.getId(), u.getVersion()))
                .toList();
    }

    private static List<UnitDiff> diffUnits(List<PreviousUnit> previous, List<PreviousUnit> current) {
        Map<String, String> previousById = new HashMap<>();
        for (PreviousUnit u : previous) {
            previousById.putIfAbsent(u.id(), u.version());
        }
        Map<String, String> currentById = new HashMap<>();
        for (PreviousUnit u : current) {
            currentById.putIfAbsent(u.id(), u.version());
        }
        Set<String> allIds = new TreeSet<>();
        allIds.addAll(previousById.keySet());
        allIds.addAll(currentById.keySet());

        List<UnitDiff> diffs = new ArrayList<>();
        for (String id : allIds) {
            String prev = previousById.get(id);
            String curr = currentById.get(id);
            if (prev == null) {
                diffs.add(new UnitDiff(id, null, curr, UnitStatus.ADDED));
            } else if (curr == null) {
                diffs.add(new UnitDiff(id, prev, null, UnitStatus.REMOVED));
            } else if (!prev.equals(curr)) {
                diffs.add(new UnitDiff(id, prev, curr, UnitStatus.BUMPED));
            } else {
                diffs.add(new UnitDiff(id, prev, curr, UnitStatus.UNCHANGED));
            }
        }
        return diffs;
    }
}
