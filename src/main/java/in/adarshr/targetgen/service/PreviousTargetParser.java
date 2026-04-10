package in.adarshr.targetgen.service;

import in.adarshr.targetgen.exception.TargetGenException;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class PreviousTargetParser {

    public record PreviousTarget(List<PreviousLocation> locations) { }
    public record PreviousLocation(String url, List<PreviousUnit> units) { }
    public record PreviousUnit(String id, String version) { }

    public PreviousTarget parse(Path file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(file.toFile());
            doc.getDocumentElement().normalize();

            List<PreviousLocation> locations = new ArrayList<>();
            NodeList locationNodes = doc.getElementsByTagName("location");
            for (int i = 0; i < locationNodes.getLength(); i++) {
                Element locElement = (Element) locationNodes.item(i);
                String url = extractRepositoryLocation(locElement);
                List<PreviousUnit> units = extractUnits(locElement);
                locations.add(new PreviousLocation(url, units));
            }
            return new PreviousTarget(locations);
        } catch (Exception e) {
            throw new TargetGenException("Failed to parse previous target file: " + file, e);
        }
    }

    private static String extractRepositoryLocation(Element locElement) {
        NodeList repos = locElement.getElementsByTagName("repository");
        for (int i = 0; i < repos.getLength(); i++) {
            Node repo = repos.item(i);
            if (repo.getParentNode() == locElement) {
                return ((Element) repo).getAttribute("location");
            }
        }
        return "";
    }

    private static List<PreviousUnit> extractUnits(Element locElement) {
        List<PreviousUnit> units = new ArrayList<>();
        NodeList unitNodes = locElement.getElementsByTagName("unit");
        for (int i = 0; i < unitNodes.getLength(); i++) {
            Node unit = unitNodes.item(i);
            if (unit.getParentNode() == locElement) {
                Element ue = (Element) unit;
                units.add(new PreviousUnit(ue.getAttribute("id"), ue.getAttribute("version")));
            }
        }
        return units;
    }
}
