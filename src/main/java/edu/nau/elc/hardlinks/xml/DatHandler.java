package edu.nau.elc.hardlinks.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This parses XML files to find which ones correspond to a particular HTML file in the Content Collection.
 */
public class DatHandler extends DefaultHandler {

    private boolean isfile = false;
    private String linkName;

	@Override
    public void characters(char ch[], int start, int length)
            throws SAXException {
    }

	@Override
    public void endDocument() {

    }

	@Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
    }

	/**
	 * Gets link name. Will return an incorrect value before parsing is complete.
	 *
	 * @return the link name
	 */
	public String getLinkName() {
        return linkName;
    }

	@Override
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
