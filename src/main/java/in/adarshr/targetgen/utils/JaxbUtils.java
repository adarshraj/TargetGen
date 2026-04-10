package in.adarshr.targetgen.utils;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.File;
import java.io.StringWriter;

public final class JaxbUtils {

    private JaxbUtils() {
    }

    public static <T> String marshalWithInstruction(T object, Class<T> clazz) throws JAXBException {
        JAXBContext jaxbContext = JAXBContext.newInstance(clazz);
        Marshaller marshaller = jaxbContext.createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
        StringWriter sw = new StringWriter();
        marshaller.marshal(object, sw);
        String xml = sw.toString();
        int insertIndex = xml.indexOf("?>") + 2;
        String withInstruction = xml.substring(0, insertIndex) + "\n<?pde version=\"3.8\"?>" + xml.substring(insertIndex);
        return withInstruction
                .replace("ns2:", "")
                .replace("xmlns:ns2=\"http://in.adarshr.targetgen.output/output.xsd\"", "");
    }

    public static <T> T unmarshalAndValidate(File xmlFile, File xsdFile, Class<T> clazz)
            throws JAXBException, SAXException {
        SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        Schema schema = sf.newSchema(xsdFile);
        JAXBContext jc = JAXBContext.newInstance(clazz);
        Unmarshaller unmarshaller = jc.createUnmarshaller();
        unmarshaller.setSchema(schema);
        return unmarshaller.unmarshal(new StreamSource(xmlFile), clazz).getValue();
    }
}
