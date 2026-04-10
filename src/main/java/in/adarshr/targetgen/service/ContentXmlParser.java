package in.adarshr.targetgen.service;

import in.adarshr.targetgen.model.Repo;
import in.adarshr.targetgen.model.Unit;
import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ContentXmlParser {

    public Map<Repo, List<Unit>> parseAll(Map<Repo, String> repoXmlMap) {
        Map<Repo, List<Unit>> result = new HashMap<>();
        repoXmlMap.forEach((repo, xml) -> result.put(repo, parse(xml)));
        return result;
    }

    public List<Unit> parse(String xml) {
        if (xml == null || xml.isBlank()) {
            return new ArrayList<>();
        }
        List<Unit> units = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            doc.getDocumentElement().normalize();
            NodeList nodes = doc.getElementsByTagName("unit");
            // Iterate in reverse to preserve historical unit ordering in generated .target files;
            // customers diff these between minor versions and forward order would create churn.
            for (int i = nodes.getLength() - 1; i >= 0; i--) {
                units.add(toUnit((Element) nodes.item(i)));
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error("Failed to parse content XML: {}", e.getMessage());
        }
        return units;
    }

    private static Unit toUnit(Element element) {
        Unit unit = new Unit();
        unit.setId(element.getAttribute("id"));
        unit.setVersion(element.getAttribute("version"));
        unit.setSingleton(element.getAttribute("singleton"));
        unit.setGeneration(element.getAttribute("generation"));
        return unit;
    }
}
