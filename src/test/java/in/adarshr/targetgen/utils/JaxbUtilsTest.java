package in.adarshr.targetgen.utils;

import in.adarshr.targetgen.schema.input.ComponentInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JaxbUtilsTest {

    private static final File SCHEMA = Path.of("schema", "input.xsd").toFile();

    private static final String INPUT_BODY = """
              <version>4.29.0</version>
              <createBasedOnReport>true</createBasedOnReport>
              <reportLocation>report/DeliveryReport.txt</reportLocation>
              <reportExtensionToUse>zip</reportExtensionToUse>
              <jrePath>JavaSE-17</jrePath>
              <targetNameFormat>$COMPONENT$_$VERSION$</targetNameFormat>
              <targetSaveFormat>BAG_$COMPONENT$_$VERSION$</targetSaveFormat>
              <targetIncludeMode>feature</targetIncludeMode>
              <locationIncludeMode>slicer</locationIncludeMode>
              <sequenceNumber>221</sequenceNumber>
              <includeAllPlatforms>true</includeAllPlatforms>
              <includeConfigurePhase>true</includeConfigurePhase>
              <includeSource>true</includeSource>
              <targetLocationType>InstallableUnit</targetLocationType>
              <components>
                <component>
                  <name>ABC</name>
                  <repository>
                    <repos>
                      <repo>
                        <group>org.eclipse</group>
                        <artifact>orbit</artifact>
                        <location>https://example.org/$GROUP$/$ARTIFACT$/release/$VERSION$</location>
                      </repo>
                    </repos>
                  </repository>
                </component>
              </components>
            """;

    @Test
    void unmarshalsInputWithExplicitNamespacePrefix(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("input-with-ns.xml");
        Files.writeString(xml, """
                <?xml version="1.0"?>
                <inp:componentInfo xmlns:inp="http://in.adarshr.targetgen.input/input.xsd">
                """ + INPUT_BODY + "</inp:componentInfo>");

        ComponentInfo info = JaxbUtils.unmarshalAndValidate(xml.toFile(), SCHEMA, ComponentInfo.class);

        assertNotNull(info);
        assertEquals("4.29.0", info.getVersion());
        assertEquals("ABC", info.getComponents().getComponent().get(0).getName());
    }

    @Test
    void unmarshalsInputWithoutNamespaceDeclaration(@TempDir Path tmp) throws Exception {
        Path xml = tmp.resolve("input-bare.xml");
        Files.writeString(xml, """
                <?xml version="1.0"?>
                <componentInfo>
                """ + INPUT_BODY + "</componentInfo>");

        ComponentInfo info = JaxbUtils.unmarshalAndValidate(xml.toFile(), SCHEMA, ComponentInfo.class);

        assertNotNull(info);
        assertEquals("4.29.0", info.getVersion());
        assertEquals("ABC", info.getComponents().getComponent().get(0).getName());
    }

}
