package in.adarshr.targetgen.build;

import in.adarshr.targetgen.model.Repo;
import in.adarshr.targetgen.model.Unit;
import in.adarshr.targetgen.model.PipelineContext;
import in.adarshr.targetgen.model.ResolvedComponent;
import in.adarshr.targetgen.schema.input.ComponentInfo;
import in.adarshr.targetgen.schema.input.SelectedGroups;
import in.adarshr.targetgen.schema.output.ArchitectureType;
import in.adarshr.targetgen.schema.output.Environment;
import in.adarshr.targetgen.schema.output.LauncherArgs;
import in.adarshr.targetgen.schema.output.Location;
import in.adarshr.targetgen.schema.output.Locations;
import in.adarshr.targetgen.schema.output.OperatingSystem;
import in.adarshr.targetgen.schema.output.RepositoryLocation;
import in.adarshr.targetgen.schema.output.Target;
import in.adarshr.targetgen.schema.output.TargetJRE;
import in.adarshr.targetgen.service.RepoUrlResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class TargetBuilder {

    private static final String COMPONENT_PLACEHOLDER = "$COMPONENT$";
    private static final String VERSION_PLACEHOLDER = "$VERSION$";

    public Map<String, Target> buildTargets(PipelineContext ctx) {
        Map<String, Target> targets = new LinkedHashMap<>();
        for (ResolvedComponent component : ctx.components()) {
            Target target = buildOneTarget(ctx, component);
            targets.put(targetFileName(ctx, component.name()), target);
        }
        return targets;
    }

    private Target buildOneTarget(PipelineContext ctx, ResolvedComponent component) {
        ComponentInfo info = ctx.componentInfo();
        Target target = new Target();
        target.setName(formatName(info.getTargetNameFormat(), component.name(), ctx.version()));
        target.setIncludeMode(info.getTargetIncludeMode());
        target.setSequenceNumber(info.getSequenceNumber());
        target.setLauncherArgs(launcherArgs(info));
        target.setTargetJRE(targetJre(info));
        target.setEnvironment(environment(component));
        target.setLocations(locations(ctx, component));
        return target;
    }

    private static String targetFileName(PipelineContext ctx, String componentName) {
        return formatName(ctx.targetSaveFormat(), componentName, ctx.version()) + ".target";
    }

    private static String formatName(String pattern, String componentName, String version) {
        if (pattern == null) {
            return componentName + "_" + version;
        }
        return pattern
                .replace(COMPONENT_PLACEHOLDER, componentName)
                .replace(VERSION_PLACEHOLDER, version);
    }

    private static LauncherArgs launcherArgs(ComponentInfo info) {
        LauncherArgs args = new LauncherArgs();
        args.setProgramArgs(info.getProgramArguments());
        args.setVmArgs(info.getVmArguments());
        return args;
    }

    private static TargetJRE targetJre(ComponentInfo info) {
        TargetJRE jre = new TargetJRE();
        jre.setPath(info.getJrePath());
        return jre;
    }

    private static Environment environment(ResolvedComponent component) {
        Environment env = new Environment();
        in.adarshr.targetgen.schema.input.Environment src = component.xml().getEnvironment();
        if (src == null) {
            return env;
        }
        env.setNl(src.getNl());
        if (StringUtils.isNotEmpty(src.getArch())) {
            env.setArch(ArchitectureType.fromValue(src.getArch()));
        }
        if (StringUtils.isNotEmpty(src.getOs())) {
            env.setOs(OperatingSystem.fromValue(src.getOs()));
        }
        if (StringUtils.isNotEmpty(src.getWs())) {
            env.setWs(src.getWs());
        }
        return env;
    }

    private Locations locations(PipelineContext ctx, ResolvedComponent component) {
        Locations locations = new Locations();
        for (Repo repo : component.repos()) {
            locations.getLocation().add(buildLocation(ctx, repo));
        }
        return locations;
    }

    private Location buildLocation(PipelineContext ctx, Repo repo) {
        ComponentInfo info = ctx.componentInfo();
        Location location = new Location();
        location.setIncludeMode(info.getLocationIncludeMode());
        location.setType(info.getTargetLocationType());
        location.setIncludeAllPlatforms(info.getIncludeAllPlatforms());
        location.setIncludeConfigurePhase(info.getIncludeConfigurePhase());

        RepositoryLocation repositoryLocation = new RepositoryLocation();
        repositoryLocation.setLocation(RepoUrlResolver.stripContentJar(repo.getLocation()));
        location.setRepository(repositoryLocation);

        List<Unit> units = ctx.repoUnits().getOrDefault(repo, List.of());
        for (Unit unit : units) {
            if (includeUnit(unit, info)) {
                location.getUnit().add(toOutputUnit(unit));
            } else {
                log.debug("Filtering out unit: {}", unit);
            }
        }
        return location;
    }

    private static in.adarshr.targetgen.schema.output.Unit toOutputUnit(Unit unit) {
        in.adarshr.targetgen.schema.output.Unit out = new in.adarshr.targetgen.schema.output.Unit();
        out.setId(unit.getId());
        out.setVersion(unit.getVersion());
        return out;
    }

    private static boolean includeUnit(Unit unit, ComponentInfo info) {
        SelectedGroups include = info.getIncludeGroups();
        if (include != null && include.getGroup() != null && !include.getGroup().isEmpty()) {
            return include.getGroup().stream().anyMatch(unit.getId()::contains);
        }
        SelectedGroups exclude = info.getExcludeGroups();
        if (exclude != null && exclude.getGroup() != null && !exclude.getGroup().isEmpty()) {
            return exclude.getGroup().stream().noneMatch(unit.getId()::contains);
        }
        return true;
    }
}
