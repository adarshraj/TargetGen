package in.adarshr.targetgen.service;

import in.adarshr.targetgen.model.Repo;
import in.adarshr.targetgen.model.ResolvedComponent;
import in.adarshr.targetgen.exception.TargetGenException;
import in.adarshr.targetgen.schema.input.Component;
import in.adarshr.targetgen.schema.input.ComponentInfo;

import java.util.ArrayList;
import java.util.List;

public class InputComponentResolver implements ComponentResolver {

    @Override
    public List<ResolvedComponent> resolveComponents(ComponentInfo componentInfo) {
        if (componentInfo == null || componentInfo.getComponents() == null) {
            throw new TargetGenException("ComponentInfo or its components element is null");
        }
        RepoUrlResolver resolver = new RepoUrlResolver(componentInfo);
        List<ResolvedComponent> result = new ArrayList<>();
        for (Component component : componentInfo.getComponents().getComponent()) {
            result.add(new ResolvedComponent(component.getName(), component, resolveRepos(component, resolver, componentInfo)));
        }
        return result;
    }

    private static List<Repo> resolveRepos(Component component, RepoUrlResolver resolver, ComponentInfo componentInfo) {
        if (component.getRepository() == null || component.getRepository().getRepos() == null) {
            throw new TargetGenException("Repository is null for component: " + component.getName());
        }
        List<Repo> repos = new ArrayList<>();
        for (in.adarshr.targetgen.schema.input.Repo xmlRepo : component.getRepository().getRepos().getRepo()) {
            Repo repo = new Repo();
            repo.setGroup(xmlRepo.getGroup());
            repo.setArtifact(xmlRepo.getArtifact());
            repo.setVersion(xmlRepo.getVersion());
            String version = (xmlRepo.getVersion() != null && !xmlRepo.getVersion().isEmpty())
                    ? xmlRepo.getVersion()
                    : componentInfo.getVersion();
            repo.setLocation(resolver.resolve(xmlRepo.getLocation(), xmlRepo.getGroup(), xmlRepo.getArtifact(), version));
            repos.add(repo);
        }
        return repos;
    }
}
