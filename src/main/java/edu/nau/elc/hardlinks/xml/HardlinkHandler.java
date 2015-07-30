package edu.nau.elc.hardlinks.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * This parses XML files that hold course content items to extract their HTML/text contents.
 */
public class HardlinkHandler extends DefaultHandler {

    private boolean isAssessment = false;
    private boolean isAnnouncement = false;
    private boolean isDiscussion = false;
    private boolean istext = false;
    private boolean istitle = false;
    private boolean readingAssessType = false;
    private String text = "";
    private String title = "";
    private String assessType = "";

	@Override
    public void characters(char ch[], int start, int length)
            throws SAXException {
        if (istext) {
            text += new String(ch, start, length);
        }
        if (readingAssessType) {
            assessType = new String(ch, start, length);
        }
    }

	@Override
    public void endDocument() {
    }

	@Override
    public void endElement(String uri, String localName, String qName)
            throws SAXException {
        if (istitle) {
            istitle = false;
        }
        if (istext) {
            istext = false;
        }
        if (isAssessment && readingAssessType) {
            readingAssessType = false;
        }
    }

	/**
	 * Gets assessment type (if a test, survey, or pool).
	 *
	 * @return the assessment type
	 */
	public String getAssessType() {
        return this.assessType;
    }

	/**
	 * Gets the text content of the content item (usually HTML).
	 *
	 * @return the text of the course content item.
	 */
	public String getText() {
		if (isDiscussion || isAnnouncement) return "";
		return this.text;
    }

	/**
	 * Get's the content item's title.
	 *
	 * @return the content item's title.
	 */
	public String getTitle() {
        return this.title;
    }

	/**
	 * Gets the type of the course content item. There are many types of content items, but this currently only
	 * implements Announcements, Discussion Forums, and Tests.
	 *
	 * @return the content item's type.
	 */
	public String getType() {
        if (isAnnouncement) {
            return "Announcements";
        }
        if (isDiscussion) {
            return "Discussion Forums";
        }
        if (isAssessment) {
            return "Tests, Surveys & Pools";
        }
        return "";
    }

	@Override
    public void startElement(String uri, String localName, String qName,
                             Attributes attributes) throws SAXException {
        if (qName.equalsIgnoreCase("ANNOUNCEMENT")) {
            isAnnouncement = true;
        }
        if (qName.equalsIgnoreCase("FORUM")) {
            isDiscussion = true;
        }
        if (qName.equalsIgnoreCase("assessment")) {
            isAssessment = true;
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = attributes.getQName(i);
                if (name.equals("title")) {
                    title = attributes.getValue(i);
                }
            }
        }

        if (isAssessment && qName.equalsIgnoreCase("bbmd_assessmenttype")) {
            readingAssessType = true;
        }

        if (qName.equalsIgnoreCase("TITLE")) {
            for (int i = 0; i < attributes.getLength(); i++) {
                String name = attributes.getQName(i);
                if (name.equals("value")) {
                    title = attributes.getValue(i);
                }
            }
        }

        if (qName.equalsIgnoreCase("TEXT")) {
            istext = true;
        }

        for (int i = 0; i < attributes.getLength(); i++) {
            if (attributes.getValue(i).contains("TEXT")) {
                istext = true;
            }
        }
    }
}
