package in.adarshr.targetgen.model;

import java.util.List;

public record ComparisonReport(
        List<ComponentDiff> componentDiffs,
        List<String> removedComponentFiles
) {

    public boolean hasChanges() {
        if (!removedComponentFiles.isEmpty()) {
            return true;
        }
        return componentDiffs.stream().anyMatch(ComponentDiff::hasChanges);
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== TargetGen comparison report ===\n");
        for (ComponentDiff cd : componentDiffs) {
            sb.append('\n').append(cd.format());
        }
        if (!removedComponentFiles.isEmpty()) {
            sb.append("\n== Removed components ==\n");
            for (String file : removedComponentFiles) {
                sb.append("  - ").append(file).append('\n');
            }
        }
        if (!hasChanges()) {
            sb.append("\nNo changes detected.\n");
        }
        return sb.toString();
    }

    public record ComponentDiff(
            String componentName,
            String previousFile,
            String newFile,
            boolean isAllNew,
            List<LocationDiff> locationDiffs
    ) {

        public boolean hasChanges() {
            if (isAllNew) {
                return true;
            }
            return locationDiffs.stream().anyMatch(LocationDiff::hasChanges);
        }

        public String format() {
            StringBuilder sb = new StringBuilder();
            sb.append("== ").append(componentName).append(" ==");
            if (isAllNew) {
                sb.append("  (NEW: ").append(newFile).append(")\n");
                int totalUnits = locationDiffs.stream()
                        .mapToInt(ld -> ld.unitDiffs().size())
                        .sum();
                sb.append("  All ").append(locationDiffs.size())
                        .append(" location(s) and ").append(totalUnits)
                        .append(" unit(s) are new.\n");
                return sb.toString();
            }
            sb.append("  (").append(previousFile).append(" \u2192 ").append(newFile).append(")\n");
            if (!hasChanges()) {
                sb.append("  No changes.\n");
                return sb.toString();
            }
            for (LocationDiff ld : locationDiffs) {
                if (ld.hasChanges()) {
                    sb.append(ld.format("  "));
                }
            }
            sb.append(formatSummary());
            return sb.toString();
        }

        private String formatSummary() {
            int locAdded = 0, locRemoved = 0, locBumped = 0;
            int unitAdded = 0, unitRemoved = 0, unitBumped = 0;
            for (LocationDiff ld : locationDiffs) {
                switch (ld.status()) {
                    case ADDED -> locAdded++;
                    case REMOVED -> locRemoved++;
                    case VERSION_BUMPED -> locBumped++;
                    case UNCHANGED -> { }
                }
                for (UnitDiff ud : ld.unitDiffs()) {
                    switch (ud.status()) {
                        case ADDED -> unitAdded++;
                        case REMOVED -> unitRemoved++;
                        case BUMPED -> unitBumped++;
                    }
                }
            }
            return String.format(
                    "  Summary: locations %d added, %d removed, %d bumped; units %d added, %d removed, %d bumped%n",
                    locAdded, locRemoved, locBumped, unitAdded, unitRemoved, unitBumped);
        }
    }

    public record LocationDiff(
            String previousUrl,
            String newUrl,
            LocationStatus status,
            List<UnitDiff> unitDiffs
    ) {

        public boolean hasChanges() {
            if (status != LocationStatus.UNCHANGED) {
                return true;
            }
            return unitDiffs.stream().anyMatch(ud -> ud.status() != UnitStatus.UNCHANGED);
        }

        public String format(String indent) {
            StringBuilder sb = new StringBuilder();
            switch (status) {
                case ADDED -> sb.append(indent).append("+ ").append(newUrl).append('\n');
                case REMOVED -> sb.append(indent).append("- ").append(previousUrl).append('\n');
                case VERSION_BUMPED -> sb.append(indent).append("~ ").append(newUrl)
                        .append("\n").append(indent).append("    (was: ").append(previousUrl).append(")\n");
                case UNCHANGED -> sb.append(indent).append("  ").append(newUrl).append('\n');
            }
            String unitIndent = indent + "    ";
            for (UnitDiff ud : unitDiffs) {
                if (ud.status() != UnitStatus.UNCHANGED) {
                    sb.append(ud.format(unitIndent));
                }
            }
            return sb.toString();
        }
    }

    public record UnitDiff(
            String id,
            String previousVersion,
            String newVersion,
            UnitStatus status
    ) {
        public String format(String indent) {
            return switch (status) {
                case ADDED -> indent + "+ " + id + "  " + newVersion + "\n";
                case REMOVED -> indent + "- " + id + "  " + previousVersion + "\n";
                case BUMPED -> indent + "~ " + id + "  " + previousVersion + " \u2192 " + newVersion + "\n";
                case UNCHANGED -> "";
            };
        }
    }

    public enum LocationStatus { ADDED, REMOVED, VERSION_BUMPED, UNCHANGED }

    public enum UnitStatus { ADDED, REMOVED, BUMPED, UNCHANGED }
}
