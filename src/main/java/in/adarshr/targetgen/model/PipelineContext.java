package in.adarshr.targetgen.model;

import in.adarshr.targetgen.model.Repo;
import in.adarshr.targetgen.model.Unit;
import in.adarshr.targetgen.schema.input.ComponentInfo;

import java.util.List;
import java.util.Map;

public record PipelineContext(
        ComponentInfo componentInfo,
        List<ResolvedComponent> components,
        Map<Repo, List<Unit>> repoUnits
) {
    public String version() {
        return componentInfo.getVersion();
    }

    public String targetSaveFormat() {
        return componentInfo.getTargetSaveFormat();
    }
}
