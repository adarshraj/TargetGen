package in.adarshr.targetgen.utils;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlSchema;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.sax.SAXSource;
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

        SAXSource source = namespaceLenientSource(xmlFile, namespaceOf(clazz));
        return unmarshaller.unmarshal(source, clazz).getValue();
    }

    private static String namespaceOf(Class<?> clazz) {
        Package pkg = clazz.getPackage();
        if (pkg == null) {
            return "";
        }
        XmlSchema schemaAnnotation = pkg.getAnnotation(XmlSchema.class);
        return schemaAnnotation != null ? schemaAnnotation.namespace() : "";
    }

    private static SAXSource namespaceLenientSource(File xmlFile, String targetNamespace) throws JAXBException {
        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            spf.setNamespaceAware(true);
            spf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            XMLReader xmlReader = spf.newSAXParser().getXMLReader();
            RootNamespaceInjector filter = new RootNamespaceInjector(xmlReader, targetNamespace);
            return new SAXSource(filter, new InputSource(xmlFile.toURI().toString()));
        } catch (ParserConfigurationException | SAXException e) {
            throw new JAXBException("Failed to set up namespace-lenient SAX parser", e);
        }
    }

    /**
     * SAX filter that injects the target namespace on the root element when the input
     * provides none. Child elements pass through unchanged because the input schema uses
     * elementFormDefault="unqualified" — only the root lives in the namespace.
     */
    private static final class RootNamespaceInjector extends XMLFilterImpl {

        private final String targetNamespace;
        private int depth = 0;

        RootNamespaceInjector(XMLReader parent, String targetNamespace) {
            super(parent);
            this.targetNamespace = targetNamespace;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            String effectiveUri = uri;
            if (depth == 0 && uri.isEmpty() && !targetNamespace.isEmpty()) {
                effectiveUri = targetNamespace;
            }
            depth++;
            super.startElement(effectiveUri, localName, qName, atts);
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            depth--;
            String effectiveUri = uri;
            if (depth == 0 && uri.isEmpty() && !targetNamespace.isEmpty()) {
                effectiveUri = targetNamespace;
            }
            super.endElement(effectiveUri, localName, qName);
        }
    }
}
