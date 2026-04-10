package in.adarshr.targetgen.service;

import in.adarshr.targetgen.model.Unit;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentXmlParserTest {

    @Test
    void preservesHistoricalReverseUnitOrder() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <repository>
                  <units size="3">
                    <unit id="alpha" version="1.0.0"/>
                    <unit id="beta"  version="2.0.0"/>
                    <unit id="gamma" version="3.0.0"/>
                  </units>
                </repository>
                """;

        List<Unit> units = new ContentXmlParser().parse(xml);

        assertEquals(3, units.size());
        assertEquals("gamma", units.get(0).getId());
        assertEquals("beta", units.get(1).getId());
        assertEquals("alpha", units.get(2).getId());
    }

    @Test
    void emptyXmlReturnsEmptyList() {
        assertTrue(new ContentXmlParser().parse("").isEmpty());
        assertTrue(new ContentXmlParser().parse(null).isEmpty());
    }

    @Test
    void doctypeIsRejectedForXxeSafety() {
        String xmlWithDoctype = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [<!ENTITY x "y">]>
                <repository><unit id="a" version="1"/></repository>
                """;
        // disallow-doctype-decl makes the parser throw; parse() catches and returns empty
        assertTrue(new ContentXmlParser().parse(xmlWithDoctype).isEmpty());
    }
}
