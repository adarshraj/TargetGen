package in.adarshr.targetgen.service;

import in.adarshr.targetgen.schema.input.ComponentInfo;

public class RepoUrlResolver {

    private static final String ARTIFACT_PLACEHOLDER = "$ARTIFACT$";
    private static final String GROUP_PLACEHOLDER = "$GROUP$";
    private static final String VERSION_PLACEHOLDER = "$VERSION$";
    private static final String CONTENT_JAR = "content.jar";

    private final ComponentInfo componentInfo;

    public RepoUrlResolver(ComponentInfo componentInfo) {
        this.componentInfo = componentInfo;
    }

    public String resolve(String location, String group, String artifact, String version) {
        if (location == null) {
            return null;
        }
        String resolved = substitute(location, ARTIFACT_PLACEHOLDER, artifact, componentInfo.getArtifactUrlPattern());
        resolved = substitute(resolved, GROUP_PLACEHOLDER, group, componentInfo.getGroupUrlPattern());
        if (resolved.contains(VERSION_PLACEHOLDER)) {
            resolved = resolved.replace(VERSION_PLACEHOLDER, version);
        }
        return resolved + "/" + CONTENT_JAR;
    }

    public static String stripContentJar(String url) {
        return url.replace(CONTENT_JAR, "");
    }

    private static String substitute(String location, String placeholder, String value, String pattern) {
        if (!location.contains(placeholder)) {
            return location;
        }
        String replacement = (pattern != null && pattern.contains("/")) ? value.replace(".", "/") : value;
        return location.replace(placeholder, replacement);
    }
}
