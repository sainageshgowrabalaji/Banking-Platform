package com.sainagesh.bank.payments.iso20022;

import java.io.StringReader;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Reads XML that came from outside, safely.
 *
 * <p>XML has features that let a document pull in files from the server that reads it, or grow to
 * gigabytes from a few lines. These are the classic XXE and "billion laughs" attacks. A payment message
 * needs none of those features, so they are all switched off before anything is parsed.
 */
final class XmlReader {

    private XmlReader() {}

    static Element parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            return document.getDocumentElement();
        } catch (Exception e) {
            throw new MessageFormatException("The message is not well-formed XML", e);
        }
    }

    /** The child element with this local name, whatever its namespace prefix. Null when there is none. */
    static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                return element;
            }
        }
        return null;
    }

    /** Follows a path of element names down from a starting element. Null when any step is missing. */
    /** How many direct children of a parent have this name. */
    static int count(Element parent, String name) {
        if (parent == null) {
            return 0;
        }
        int found = 0;
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(element.getLocalName())) {
                found++;
            }
        }
        return found;
    }

    static Element at(Element start, String... path) {
        Element current = start;
        for (String name : path) {
            current = child(current, name);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    /** The text at a path, trimmed. Null when the element is missing or empty. */
    static String text(Element start, String... path) {
        Element element = at(start, path);
        if (element == null) {
            return null;
        }
        String text = element.getTextContent().trim();
        return text.isEmpty() ? null : text;
    }

    /** The text at a path, or an error that names the missing field. */
    static String required(Element start, String... path) {
        String text = text(start, path);
        if (text == null) {
            throw new MessageFormatException("The message has no " + String.join("/", path));
        }
        return text;
    }
}
