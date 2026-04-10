package in.adarshr.targetgen.service;

import in.adarshr.targetgen.schema.input.ComponentInfo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RepoUrlResolverTest {

    @Test
    void substitutesArtifactGroupAndVersionAndAppendsContentJar() {
        ComponentInfo info = new ComponentInfo();
        info.setVersion("4.29.0");
        info.setGroupUrlPattern("");
        info.setArtifactUrlPattern("");

        RepoUrlResolver resolver = new RepoUrlResolver(info);
        String url = resolver.resolve(
                "https://example.org/$GROUP$/$ARTIFACT$/release/$VERSION$",
                "tools.orbit",
                "orbit-aggregation",
                "4.29.0");

        assertEquals("https://example.org/tools.orbit/orbit-aggregation/release/4.29.0/content.jar", url);
    }

    @Test
    void slashInPatternConvertsDotsToSlashes() {
        ComponentInfo info = new ComponentInfo();
        info.setGroupUrlPattern("a/b");
        info.setArtifactUrlPattern("");

        RepoUrlResolver resolver = new RepoUrlResolver(info);
        String url = resolver.resolve(
                "https://example.org/$GROUP$/x/$VERSION$",
                "com.example.tools",
                "any",
                "1.0.0");

        assertEquals("https://example.org/com/example/tools/x/1.0.0/content.jar", url);
    }

    @Test
    void stripContentJarLeavesTrailingSlash() {
        assertEquals("https://example.org/release/1.0/", RepoUrlResolver.stripContentJar("https://example.org/release/1.0/content.jar"));
    }
}
