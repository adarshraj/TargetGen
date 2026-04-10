package in.adarshr.targetgen.build;

import in.adarshr.targetgen.model.PipelineContext;
import in.adarshr.targetgen.model.Repo;
import in.adarshr.targetgen.model.ResolvedComponent;
import in.adarshr.targetgen.model.Unit;
import in.adarshr.targetgen.schema.input.Component;
import in.adarshr.targetgen.schema.input.ComponentInfo;
import in.adarshr.targetgen.schema.input.Environment;
import in.adarshr.targetgen.schema.input.SelectedGroups;
import in.adarshr.targetgen.schema.output.Target;
import in.adarshr.targetgen.utils.JaxbUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetBuilderTest {

    @Test
    void buildsOneTargetPerComponentWithExpectedFilenameAndContent() throws Exception {
        ComponentInfo info = baseComponentInfo();
        Component component = simpleComponent("ABC");
        info.getComponents().getComponent().add(component);

        Repo repo = new Repo();
        repo.setGroup("org.eclipse");
        repo.setArtifact("orbit");
        repo.setLocation("https://example.org/release/4.29.0/content.jar");

        Unit keptUnit = unit("org.eclipse.something", "1.0.0");
        Unit filteredUnit = unit("com.thirdparty.junk", "2.0.0");

        ResolvedComponent resolved = new ResolvedComponent("ABC", component, List.of(repo));
        PipelineContext ctx = new PipelineContext(
                info,
                List.of(resolved),
                Map.of(repo, List.of(keptUnit, filteredUnit)));

        Map<String, Target> targets = new TargetBuilder().buildTargets(ctx);

        assertEquals(1, targets.size());
        assertTrue(targets.containsKey("BAG_ABC_4.29.0.target"),
                "expected filename from targetSaveFormat, got: " + targets.keySet());

        Target target = targets.values().iterator().next();
        assertEquals("ABC_4.29.0", target.getName());
        assertEquals(1, target.getLocations().getLocation().size());
        assertEquals("https://example.org/release/4.29.0/",
                target.getLocations().getLocation().get(0).getRepository().getLocation());

        // includeGroups=org.eclipse keeps only the eclipse unit
        List<in.adarshr.targetgen.schema.output.Unit> outUnits =
                target.getLocations().getLocation().get(0).getUnit();
        assertEquals(1, outUnits.size());
        assertEquals("org.eclipse.something", outUnits.get(0).getId());
    }

    @Test
    void marshalledOutputStripsNs2PrefixAndContainsPdeInstruction() throws Exception {
        ComponentInfo info = baseComponentInfo();
        Component component = simpleComponent("ABC");
        info.getComponents().getComponent().add(component);

        Repo repo = new Repo();
        repo.setGroup("org.eclipse");
        repo.setArtifact("orbit");
        repo.setLocation("https://example.org/release/4.29.0/content.jar");

        ResolvedComponent resolved = new ResolvedComponent("ABC", component, List.of(repo));
        PipelineContext ctx = new PipelineContext(info, List.of(resolved),
                Map.of(repo, List.of(unit("org.eclipse.x", "1.0"))));

        Map<String, Target> targets = new TargetBuilder().buildTargets(ctx);
        Target target = targets.values().iterator().next();

        String xml = JaxbUtils.marshalWithInstruction(target, Target.class);

        assertNotNull(xml);
        assertTrue(xml.contains("<?pde version=\"3.8\"?>"),
                "PDE processing instruction should be injected");
        assertTrue(xml.contains("<target"), "root element should be plain <target> after ns2: stripping, got:\n" + xml);
        assertTrue(!xml.contains("ns2:"), "ns2: prefix should be stripped, got:\n" + xml);
        assertTrue(!xml.contains("xmlns:ns2"), "ns2 namespace declaration should be stripped, got:\n" + xml);
    }

    private static ComponentInfo baseComponentInfo() {
        ComponentInfo info = new ComponentInfo();
        info.setVersion("4.29.0");
        info.setTargetNameFormat("$COMPONENT$_$VERSION$");
        info.setTargetSaveFormat("BAG_$COMPONENT$_$VERSION$");
        info.setTargetIncludeMode("feature");
        info.setLocationIncludeMode("slicer");
        info.setSequenceNumber("221");
        info.setIncludeAllPlatforms("true");
        info.setIncludeConfigurePhase("true");
        info.setTargetLocationType("InstallableUnit");
        info.setJrePath("JavaSE-17");
        info.setProgramArguments("");
        info.setVmArguments("");
        info.setGroupUrlPattern("");
        info.setArtifactUrlPattern("");

        SelectedGroups include = new SelectedGroups();
        include.getGroup().add("org.eclipse");
        info.setIncludeGroups(include);

        info.setComponents(new in.adarshr.targetgen.schema.input.Components());
        return info;
    }

    private static Component simpleComponent(String name) {
        Component component = new Component();
        component.setName(name);
        Environment env = new Environment();
        env.setNl("fr");
        env.setOs("");
        env.setWs("");
        env.setArch("");
        component.setEnvironment(env);
        return component;
    }

    private static Unit unit(String id, String version) {
        Unit u = new Unit();
        u.setId(id);
        u.setVersion(version);
        return u;
    }
}
