package in.adarshr.targetgen.service;

import in.adarshr.targetgen.model.ComparisonReport;
import in.adarshr.targetgen.model.ComparisonReport.ComponentDiff;
import in.adarshr.targetgen.model.ComparisonReport.LocationDiff;
import in.adarshr.targetgen.model.ComparisonReport.LocationStatus;
import in.adarshr.targetgen.model.ComparisonReport.UnitDiff;
import in.adarshr.targetgen.model.ComparisonReport.UnitStatus;
import in.adarshr.targetgen.model.ResolvedComponent;
import in.adarshr.targetgen.schema.input.Component;
import in.adarshr.targetgen.schema.output.Location;
import in.adarshr.targetgen.schema.output.Locations;
import in.adarshr.targetgen.schema.output.RepositoryLocation;
import in.adarshr.targetgen.schema.output.Target;
import in.adarshr.targetgen.schema.output.Unit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetComparatorTest {

    private static final String SAVE_FORMAT = "BAG_$COMPONENT$_$VERSION$";

    @Test
    void allNewWhenNoMatchingPreviousFile(@TempDir Path tmp) {
        ResolvedComponent component = new ResolvedComponent("ABC", new Component(), List.of());
        Target newTarget = newTargetWith(
                location("https://example.org/release/4.30.0/", List.of(unit("eclipse.core", "1.0"))));
        Map<String, Target> targets = Map.of("BAG_ABC_4.30.0.target", newTarget);

        ComparisonReport report = new TargetComparator()
                .compare(List.of(component), targets, SAVE_FORMAT, "4.30.0", tmp);

        assertEquals(1, report.componentDiffs().size());
        ComponentDiff diff = report.componentDiffs().get(0);
        assertTrue(diff.isAllNew());
        assertNull(diff.previousFile());
        assertEquals("BAG_ABC_4.30.0.target", diff.newFile());
        assertEquals(1, diff.locationDiffs().size());
        assertEquals(LocationStatus.ADDED, diff.locationDiffs().get(0).status());
    }

    @Test
    void detectsAddedRemovedAndBumpedUnitsInsideAVersionBumpedLocation(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("BAG_ABC_4.29.0.target"), """
                <?xml version="1.0"?>
                <target name="ABC_4.29.0">
                  <locations>
                    <location includeMode="slicer" type="InstallableUnit">
                      <repository location="https://example.org/release/4.29.0/"/>
                      <unit id="eclipse.core" version="1.0.0"/>
                      <unit id="eclipse.removed" version="2.0.0"/>
                    </location>
                  </locations>
                </target>
                """);

        ResolvedComponent component = new ResolvedComponent("ABC", new Component(), List.of());
        Target newTarget = newTargetWith(location(
                "https://example.org/release/4.30.0/",
                List.of(
                        unit("eclipse.core", "1.1.0"),
                        unit("eclipse.added", "9.0.0"))));
        Map<String, Target> targets = Map.of("BAG_ABC_4.30.0.target", newTarget);

        ComparisonReport report = new TargetComparator()
                .compare(List.of(component), targets, SAVE_FORMAT, "4.30.0", tmp);

        ComponentDiff diff = report.componentDiffs().get(0);
        assertFalse(diff.isAllNew());
        assertEquals("BAG_ABC_4.29.0.target", diff.previousFile());

        assertEquals(1, diff.locationDiffs().size());
        LocationDiff loc = diff.locationDiffs().get(0);
        assertEquals(LocationStatus.VERSION_BUMPED, loc.status());
        assertEquals("https://example.org/release/4.29.0/", loc.previousUrl());
        assertEquals("https://example.org/release/4.30.0/", loc.newUrl());

        Map<String, UnitDiff> byId = loc.unitDiffs().stream()
                .collect(java.util.stream.Collectors.toMap(UnitDiff::id, ud -> ud));
        assertEquals(UnitStatus.BUMPED, byId.get("eclipse.core").status());
        assertEquals("1.0.0", byId.get("eclipse.core").previousVersion());
        assertEquals("1.1.0", byId.get("eclipse.core").newVersion());
        assertEquals(UnitStatus.REMOVED, byId.get("eclipse.removed").status());
        assertEquals(UnitStatus.ADDED, byId.get("eclipse.added").status());
    }

    @Test
    void unmatchedPreviousFileIsReportedAsRemovedComponent(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("BAG_GONE_27.5.target"),
                "<?xml version=\"1.0\"?><target name=\"GONE\"><locations/></target>");

        ResolvedComponent kept = new ResolvedComponent("ABC", new Component(), List.of());
        Target newTarget = newTargetWith(location("https://example.org/release/4.30.0/", List.of()));
        Map<String, Target> targets = Map.of("BAG_ABC_4.30.0.target", newTarget);

        ComparisonReport report = new TargetComparator()
                .compare(List.of(kept), targets, SAVE_FORMAT, "4.30.0", tmp);

        assertEquals(1, report.removedComponentFiles().size());
        assertEquals("BAG_GONE_27.5.target", report.removedComponentFiles().get(0));
    }

    @Test
    void picksLexicographicallyLatestPreviousFileOnTie(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("BAG_ABC_27.0.target"),
                "<?xml version=\"1.0\"?><target name=\"ABC_27.0\"><locations/></target>");
        Files.writeString(tmp.resolve("BAG_ABC_28.0.target"),
                "<?xml version=\"1.0\"?><target name=\"ABC_28.0\"><locations/></target>");
        Files.writeString(tmp.resolve("BAG_ABC_27.5.target"),
                "<?xml version=\"1.0\"?><target name=\"ABC_27.5\"><locations/></target>");

        ResolvedComponent component = new ResolvedComponent("ABC", new Component(), List.of());
        Target newTarget = newTargetWith();
        Map<String, Target> targets = Map.of("BAG_ABC_28.1.target", newTarget);

        ComparisonReport report = new TargetComparator()
                .compare(List.of(component), targets, SAVE_FORMAT, "28.1", tmp);

        assertEquals("BAG_ABC_28.0.target", report.componentDiffs().get(0).previousFile());
    }

    @Test
    void hasChangesIsFalseWhenEverythingMatches(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("BAG_ABC_4.29.0.target"), """
                <?xml version="1.0"?>
                <target name="ABC_4.29.0">
                  <locations>
                    <location>
                      <repository location="https://example.org/release/4.29.0/"/>
                      <unit id="eclipse.core" version="1.0.0"/>
                    </location>
                  </locations>
                </target>
                """);

        ResolvedComponent component = new ResolvedComponent("ABC", new Component(), List.of());
        Target newTarget = newTargetWith(location(
                "https://example.org/release/4.29.0/",
                List.of(unit("eclipse.core", "1.0.0"))));
        Map<String, Target> targets = Map.of("BAG_ABC_4.29.0.target", newTarget);

        ComparisonReport report = new TargetComparator()
                .compare(List.of(component), targets, SAVE_FORMAT, "4.29.0", tmp);

        assertFalse(report.hasChanges());
    }

    @Test
    void formatProducesReadableReport(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("BAG_ABC_4.29.0.target"), """
                <?xml version="1.0"?>
                <target name="ABC_4.29.0">
                  <locations>
                    <location>
                      <repository location="https://example.org/release/4.29.0/"/>
                      <unit id="eclipse.core" version="1.0.0"/>
                      <unit id="eclipse.removed" version="2.0.0"/>
                    </location>
                  </locations>
                </target>
                """);

        ResolvedComponent component = new ResolvedComponent("ABC", new Component(), List.of());
        Target newTarget = newTargetWith(location(
                "https://example.org/release/4.30.0/",
                List.of(unit("eclipse.core", "1.1.0"), unit("eclipse.added", "9.0.0"))));
        Map<String, Target> targets = Map.of("BAG_ABC_4.30.0.target", newTarget);

        ComparisonReport report = new TargetComparator()
                .compare(List.of(component), targets, SAVE_FORMAT, "4.30.0", tmp);
        String formatted = report.format();

        assertTrue(formatted.contains("== ABC =="), formatted);
        assertTrue(formatted.contains("BAG_ABC_4.29.0.target"), formatted);
        assertTrue(formatted.contains("BAG_ABC_4.30.0.target"), formatted);
        assertTrue(formatted.contains("~ https://example.org/release/4.30.0/"), formatted);
        assertTrue(formatted.contains("(was: https://example.org/release/4.29.0/)"), formatted);
        assertTrue(formatted.contains("+ eclipse.added"), formatted);
        assertTrue(formatted.contains("- eclipse.removed"), formatted);
        assertTrue(formatted.contains("~ eclipse.core"), formatted);
        assertTrue(formatted.contains("Summary:"), formatted);
    }

    @Test
    void buildFilenamePatternEscapesRegexMetacharacters() {
        java.util.regex.Pattern p = TargetComparator.buildFilenamePattern("BAG_$COMPONENT$_$VERSION$", "ABC");
        assertTrue(p.matcher("BAG_ABC_4.29.0.target").matches());
        assertTrue(p.matcher("BAG_ABC_28.1.target").matches());
        assertFalse(p.matcher("BAG_XYZ_4.29.0.target").matches());
        assertFalse(p.matcher("BAG_ABC_4.29.0.txt").matches());
    }

    @Test
    void urlsDifferOnlyInOneSegmentDetectsVersionBump() {
        assertTrue(TargetComparator.urlsDifferOnlyInOneSegment(
                "https://example.org/release/4.29.0/",
                "https://example.org/release/4.30.0/"));
        assertFalse(TargetComparator.urlsDifferOnlyInOneSegment(
                "https://example.org/release/4.29.0/",
                "https://other.org/release/4.30.0/"));
        assertFalse(TargetComparator.urlsDifferOnlyInOneSegment(
                "https://example.org/release/4.29.0/",
                "https://example.org/release/4.29.0/"));
    }

    private static Target newTargetWith(Location... locs) {
        Target t = new Target();
        Locations locations = new Locations();
        for (Location l : locs) {
            locations.getLocation().add(l);
        }
        t.setLocations(locations);
        return t;
    }

    private static Location location(String url, List<Unit> units) {
        Location loc = new Location();
        RepositoryLocation rl = new RepositoryLocation();
        rl.setLocation(url);
        loc.setRepository(rl);
        for (Unit u : units) {
            loc.getUnit().add(u);
        }
        return loc;
    }

    private static Unit unit(String id, String version) {
        Unit u = new Unit();
        u.setId(id);
        u.setVersion(version);
        return u;
    }
}
