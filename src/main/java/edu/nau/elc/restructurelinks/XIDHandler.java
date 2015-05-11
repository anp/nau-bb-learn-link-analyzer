package edu.nau.elc.restructurelinks;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class XIDHandler extends DefaultHandler {

    private String ident = "";
    private boolean isident = false;

    public void characters(char ch[], int start, int length)
            throws SAXException {
        if (isident) {
            ident += new String(ch, start, length);
        }
    }

    public void endDocument() {

    }

    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (isident) {
            isident = false;
        }
    }

    public String getIdent() {
        return this.ident;
    }

    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
        if (qName.equalsIgnoreCase("identifier")) {
            isident = true;
        }
    }
}
