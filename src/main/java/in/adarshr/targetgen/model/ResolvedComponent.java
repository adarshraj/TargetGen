package in.adarshr.targetgen.model;

import in.adarshr.targetgen.model.Repo;
import in.adarshr.targetgen.schema.input.Component;

import java.util.List;

public record ResolvedComponent(String name, Component xml, List<Repo> repos) {
}
