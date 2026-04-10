package in.adarshr.targetgen.service;

import in.adarshr.targetgen.model.Report;
import in.adarshr.targetgen.model.Repo;
import in.adarshr.targetgen.model.ResolvedComponent;
import in.adarshr.targetgen.schema.input.Component;
import in.adarshr.targetgen.schema.input.ComponentInfo;
import in.adarshr.targetgen.schema.input.Components;
import in.adarshr.targetgen.schema.input.Repository;
import in.adarshr.targetgen.schema.input.Repos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReportComponentResolverTest {

    @Test
    void unmatchedRepoLeavesLocationUnresolved() {
        ComponentInfo info = newComponentInfo("4.29.0");
        Component component = newComponent("ABC",
                newRepo("org.example", "thing-not-in-report", null,
                        "https://example.org/$GROUP$/$ARTIFACT$/$VERSION$"));
        info.getComponents().getComponent().add(component);

        // Report exists but contains no matching row for this repo
        Report otherReport = new Report("OK", "org.other", "different", "1.0.0", "", "zip");
        ReportComponentResolver resolver = new ReportComponentResolver(List.of(otherReport));

        List<ResolvedComponent> resolved = resolver.resolveComponents(info);

        assertEquals(1, resolved.size());
        Repo repo = resolved.get(0).repos().get(0);
        // Old behaviour: location stays raw with placeholders unresolved.
        // This guarantees no NPE on null xmlRepo.getVersion() and matches pre-refactor output.
        assertEquals("https://example.org/$GROUP$/$ARTIFACT$/$VERSION$", repo.getLocation());
    }

    @Test
    void matchedRepoUsesReportVersionWhenXmlVersionMissing() {
        ComponentInfo info = newComponentInfo("4.29.0");
        Component component = newComponent("ABC",
                newRepo("org.example", "thing", null,
                        "https://example.org/$GROUP$/$ARTIFACT$/release/$VERSION$"));
        info.getComponents().getComponent().add(component);

        Report report = new Report("OK", "org.example", "thing", "9.9.9", "", "zip");
        ReportComponentResolver resolver = new ReportComponentResolver(List.of(report));

        List<ResolvedComponent> resolved = resolver.resolveComponents(info);
        Repo repo = resolved.get(0).repos().get(0);

        assertEquals("https://example.org/org.example/thing/release/9.9.9/content.jar",
                repo.getLocation());
    }

    @Test
    void matchedRepoPrefersExplicitXmlVersionOverReport() {
        ComponentInfo info = newComponentInfo("4.29.0");
        Component component = newComponent("ABC",
                newRepo("org.example", "thing", "1.2.3",
                        "https://example.org/$GROUP$/$ARTIFACT$/release/$VERSION$"));
        info.getComponents().getComponent().add(component);

        Report report = new Report("OK", "org.example", "thing", "9.9.9", "", "zip");
        ReportComponentResolver resolver = new ReportComponentResolver(List.of(report));

        Repo repo = resolver.resolveComponents(info).get(0).repos().get(0);
        assertEquals("https://example.org/org.example/thing/release/1.2.3/content.jar",
                repo.getLocation());
    }

    private static ComponentInfo newComponentInfo(String version) {
        ComponentInfo info = new ComponentInfo();
        info.setVersion(version);
        info.setGroupUrlPattern("");
        info.setArtifactUrlPattern("");
        info.setComponents(new Components());
        return info;
    }

    private static Component newComponent(String name, in.adarshr.targetgen.schema.input.Repo... repos) {
        Component component = new Component();
        component.setName(name);
        Repository repository = new Repository();
        Repos reposNode = new Repos();
        for (in.adarshr.targetgen.schema.input.Repo repo : repos) {
            reposNode.getRepo().add(repo);
        }
        repository.setRepos(reposNode);
        component.setRepository(repository);
        return component;
    }

    private static in.adarshr.targetgen.schema.input.Repo newRepo(String group, String artifact, String version, String location) {
        in.adarshr.targetgen.schema.input.Repo repo = new in.adarshr.targetgen.schema.input.Repo();
        repo.setGroup(group);
        repo.setArtifact(artifact);
        repo.setVersion(version);
        repo.setLocation(location);
        return repo;
    }
}
