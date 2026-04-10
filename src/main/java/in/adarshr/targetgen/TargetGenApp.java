package in.adarshr.targetgen;

import in.adarshr.targetgen.build.TargetBuilder;
import in.adarshr.targetgen.exception.TargetGenException;
import in.adarshr.targetgen.model.ComparisonReport;
import in.adarshr.targetgen.model.PipelineContext;
import in.adarshr.targetgen.model.Repo;
import in.adarshr.targetgen.model.Report;
import in.adarshr.targetgen.model.ResolvedComponent;
import in.adarshr.targetgen.model.Unit;
import in.adarshr.targetgen.schema.input.ComponentInfo;
import in.adarshr.targetgen.schema.output.Target;
import in.adarshr.targetgen.service.ComponentResolver;
import in.adarshr.targetgen.service.ContentXmlParser;
import in.adarshr.targetgen.service.DeliveryReportLoader;
import in.adarshr.targetgen.service.InputComponentResolver;
import in.adarshr.targetgen.service.JarDownloader;
import in.adarshr.targetgen.service.ReportComponentResolver;
import in.adarshr.targetgen.service.TargetComparator;
import in.adarshr.targetgen.service.TargetWriter;
import in.adarshr.targetgen.utils.JaxbUtils;
import jakarta.xml.bind.JAXBException;
import lombok.extern.slf4j.Slf4j;
import org.xml.sax.SAXException;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
public class TargetGenApp {

    public record Config(Path inputXml, Path inputXsd, Path outputDir, Path comparePath) {
        public Config(Path inputXml, Path inputXsd, Path outputDir) {
            this(inputXml, inputXsd, outputDir, null);
        }
    }

    private final Config config;

    public TargetGenApp(Config config) {
        this.config = config;
    }

    public void run() {
        log.info("Starting TargetGen with input={}, schema={}, output={}",
                config.inputXml(), config.inputXsd(), config.outputDir());

        ComponentInfo componentInfo = readInput();

        ComponentResolver repoSource = chooseComponentResolver(componentInfo);
        List<ResolvedComponent> components = repoSource.resolveComponents(componentInfo);
        log.info("Resolved {} components", components.size());

        Set<Repo> distinctRepos = collectDistinctRepos(components);
        if (distinctRepos.isEmpty()) {
            log.error("No repository jar URLs found; nothing to do");
            return;
        }

        Map<Repo, String> repoXml = new JarDownloader().downloadContentXml(distinctRepos);
        Map<Repo, List<Unit>> repoUnits = new ContentXmlParser().parseAll(repoXml);

        PipelineContext ctx = new PipelineContext(componentInfo, components, repoUnits);
        Map<String, Target> targets = new TargetBuilder().buildTargets(ctx);

        if (config.comparePath() != null) {
            ComparisonReport report = new TargetComparator().compare(
                    components,
                    targets,
                    componentInfo.getTargetSaveFormat(),
                    componentInfo.getVersion(),
                    config.comparePath());
            System.out.println(report.format());
        }

        new TargetWriter(config.outputDir()).writeAll(targets);

        log.info("All tasks completed; wrote {} target file(s)", targets.size());
    }

    private ComponentInfo readInput() {
        try {
            return JaxbUtils.unmarshalAndValidate(
                    config.inputXml().toFile(),
                    config.inputXsd().toFile(),
                    ComponentInfo.class);
        } catch (JAXBException | SAXException e) {
            throw new TargetGenException("Failed to read or validate input XML: " + config.inputXml(), e);
        }
    }

    private static ComponentResolver chooseComponentResolver(ComponentInfo info) {
        if (info.isCreateBasedOnReport()) {
            List<Report> reports = new DeliveryReportLoader().load(info);
            return new ReportComponentResolver(reports);
        }
        return new InputComponentResolver();
    }

    private static Set<Repo> collectDistinctRepos(List<ResolvedComponent> components) {
        Set<Repo> distinct = new HashSet<>();
        for (ResolvedComponent component : components) {
            distinct.addAll(component.repos());
        }
        return distinct;
    }
}
