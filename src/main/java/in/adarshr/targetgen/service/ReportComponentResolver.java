package in.adarshr.targetgen.service;

import in.adarshr.targetgen.model.Repo;
import in.adarshr.targetgen.model.Report;
import in.adarshr.targetgen.model.ResolvedComponent;
import in.adarshr.targetgen.exception.TargetGenException;
import in.adarshr.targetgen.schema.input.Component;
import in.adarshr.targetgen.schema.input.ComponentInfo;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

public class ReportComponentResolver implements ComponentResolver {

    private final List<Report> reports;

    public ReportComponentResolver(List<Report> reports) {
        this.reports = reports;
    }

    @Override
    public List<ResolvedComponent> resolveComponents(ComponentInfo componentInfo) {
        if (componentInfo == null || componentInfo.getComponents() == null) {
            throw new TargetGenException("ComponentInfo or its components element is null");
        }
        RepoUrlResolver resolver = new RepoUrlResolver(componentInfo);
        List<ResolvedComponent> result = new ArrayList<>();
        for (Component component : componentInfo.getComponents().getComponent()) {
            result.add(new ResolvedComponent(component.getName(), component, resolveRepos(component, resolver)));
        }
        return result;
    }

    private List<Repo> resolveRepos(Component component, RepoUrlResolver resolver) {
        if (component.getRepository() == null || component.getRepository().getRepos() == null) {
            throw new TargetGenException("Repository is null for component: " + component.getName());
        }
        List<Repo> repos = new ArrayList<>();
        for (in.adarshr.targetgen.schema.input.Repo xmlRepo : component.getRepository().getRepos().getRepo()) {
            Repo repo = new Repo();
            repo.setGroup(xmlRepo.getGroup());
            repo.setArtifact(xmlRepo.getArtifact());
            repo.setVersion(xmlRepo.getVersion());
            repo.setLocation(resolveLocation(xmlRepo, resolver));
            repos.add(repo);
        }
        return repos;
    }

    private String resolveLocation(in.adarshr.targetgen.schema.input.Repo xmlRepo, RepoUrlResolver resolver) {
        if (reports != null) {
            for (Report report : reports) {
                if (StringUtils.isNotEmpty(report.getGroup())
                        && StringUtils.isNotEmpty(report.getArtifact())
                        && StringUtils.isNotEmpty(report.getVersion())
                        && report.getGroup().equals(xmlRepo.getGroup())
                        && report.getArtifact().equals(xmlRepo.getArtifact())) {
                    String version = (xmlRepo.getVersion() != null && !xmlRepo.getVersion().isEmpty())
                            ? xmlRepo.getVersion()
                            : report.getVersion();
                    return resolver.resolve(xmlRepo.getLocation(), xmlRepo.getGroup(), xmlRepo.getArtifact(), version);
                }
            }
        }
        // No matching delivery report row: leave the location unresolved (matches the
        // original behaviour — download will fail loudly downstream and the customer
        // can spot the unmatched repo in their input.xml).
        return xmlRepo.getLocation();
    }
}
