package in.adarshr.targetgen;

import in.adarshr.targetgen.exception.TargetGenException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.nio.file.Path;

@Slf4j
public final class TargetGen {

    private TargetGen() {
    }

    public static void main(String[] args) {
        Options options = buildOptions();
        try {
            CommandLine cli = new DefaultParser().parse(options, args);
            if (cli.hasOption("h")) {
                new HelpFormatter().printHelp("targetgen", options, true);
                return;
            }
            TargetGenApp.Config config = new TargetGenApp.Config(
                    Path.of(cli.getOptionValue("input", "input/input.xml")),
                    Path.of(cli.getOptionValue("schema", "schema/input.xsd")),
                    Path.of(cli.getOptionValue("output", "output")),
                    cli.hasOption("compare") ? Path.of(cli.getOptionValue("compare")) : null);
            new TargetGenApp(config).run();
        } catch (ParseException e) {
            log.error("Invalid arguments: {}", e.getMessage());
            new HelpFormatter().printHelp("targetgen", options, true);
            System.exit(2);
        } catch (TargetGenException e) {
            log.error("TargetGen failed: {}", e.getMessage(), e);
            System.exit(1);
        } catch (RuntimeException e) {
            log.error("Unexpected error", e);
            System.exit(1);
        }
    }

    private static Options buildOptions() {
        Options options = new Options();
        options.addOption(Option.builder("i").longOpt("input").hasArg().argName("file")
                .desc("Path to input XML (default: input/input.xml)").build());
        options.addOption(Option.builder("s").longOpt("schema").hasArg().argName("file")
                .desc("Path to input XSD (default: schema/input.xsd)").build());
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("dir")
                .desc("Output directory (default: output)").build());
        options.addOption(Option.builder("c").longOpt("compare").hasArg().argName("path")
                .desc("Diff newly generated targets against previous .target files in this directory or file").build());
        options.addOption(Option.builder("h").longOpt("help").desc("Show help").build());
        return options;
    }
}
