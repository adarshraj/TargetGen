package in.adarshr.targetgen.service;

import in.adarshr.targetgen.model.ResolvedComponent;
import in.adarshr.targetgen.schema.input.ComponentInfo;

import java.util.List;

public interface ComponentResolver {
    List<ResolvedComponent> resolveComponents(ComponentInfo componentInfo);
}
