package edu.nau.elc.restructurelinks;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class DatHandler extends DefaultHandler {

    private boolean isfile = false;
    private String linkName;

    public void characters(char ch[], int start, int length)
            throws SAXException {
    }

    public void endDocument() {

    }

    public void endElement(String uri, String localName, String qName)
            throws SAXException {
    }

    public String getLinkName() {
        return linkName;
    }

    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
        if (qName.equalsIgnoreCase("contenthandler")) {
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = attributes.getQName(i);
                if (name.equals("value")) {
                    if (attributes.getValue(i).equals("resource/x-bb-file")) {
                        isfile = true;
                    }
                }
            }
        }

        if (isfile && qName.equalsIgnoreCase("linkname")) {
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = attributes.getQName(i);
                if (name.equals("value")) {
                    linkName = attributes.getValue(i);
                }
            }
        }
    }
}
