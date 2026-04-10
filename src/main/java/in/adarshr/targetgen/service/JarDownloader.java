package in.adarshr.targetgen.service;

import in.adarshr.targetgen.model.Repo;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

@Slf4j
public class JarDownloader {

    public Map<Repo, String> downloadContentXml(Set<Repo> repos) {
        Map<Repo, String> result = new HashMap<>();
        repos.parallelStream().forEach(repo -> {
            String xml = downloadOne(repo);
            synchronized (result) {
                result.put(repo, xml);
            }
        });
        return result;
    }

    private String downloadOne(Repo repo) {
        log.info("Downloading JAR from URL: {}", repo.getLocation());
        try (InputStream in = URI.create(repo.getLocation()).toURL().openStream();
             JarInputStream jarStream = new JarInputStream(in);
             BufferedReader reader = new BufferedReader(new InputStreamReader(jarStream))) {
            JarEntry entry;
            while ((entry = jarStream.getNextJarEntry()) != null) {
                if (entry.getName().endsWith(".xml")) {
                    StringBuilder xml = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        xml.append(line).append('\n');
                    }
                    return xml.toString();
                }
            }
        } catch (IOException e) {
            log.error("Failed to download JAR from URL {}: {}", repo.getLocation(), e.getMessage());
        }
        return "";
    }
}
