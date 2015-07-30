package edu.nau.elc.hardlinks.domain;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Represents a single link (a or img tag) found in HTML anywhere in the course.
 */
public class Link {
	private static DocumentBuilder documentBuilder;

	private final CourseItem parent;
	private final String url;
	private final String linkText;
	private String xid = "";

	/**
	 * Instantiates a new Link.
	 *
	 * @param url        the URL
	 * @param text       the text/alt
	 * @param courseItem the parent course item
	 * @param discarded  if it isn't a "hardlink," it's discarded
	 * @throws IOException  If unable to read from the relevant files.
	 * @throws SAXException If unable to parse (severely malformed XML, usually).
	 */
	public Link(String url, String text, CourseItem courseItem, boolean discarded)
			throws IOException, SAXException {

		if (documentBuilder == null) {
			try {
				documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			} catch (ParserConfigurationException pce) {
				throw new SAXException(pce);
			}
		}

		parent = courseItem;
		this.url = url;
		linkText = text;
		if (!discarded) {
			findXID();
		}
	}

	/**
	 * Get the ID from a given XML file. Used for retreiving a content item's XID.
	 *
	 * @param f The file to parse.
	 * @return The "identifier" from the XML file.
	 * @throws IOException
	 * @throws SAXException
	 */
	private static String getIdentifier(File f) throws IOException, SAXException {
		Document doc = documentBuilder.parse(f);
		return doc.getElementsByTagName("identifier").item(0).getTextContent();
	}

	/**
	 * Gets the Levenshtein Distance between two strings. Copied from
	 * <a href="http://en.wikibooks.org/wiki/Algorithm_Implementation/Strings/Levenshtein_distance#Java">WikiBooks</a>.
	 * <br><br>
	 * This is used to find likely candidates for a filename match in the exported Content Collection. Because
	 * non-ASCII characters are handled differently in the XML than they are in the ZIP file entities, we just want
	 * to find the closest match, rather than an exact one.
	 *
	 * @param s0 The first string.
	 * @param s1 The second string.
	 * @return The "edit distance" between the two (see WikiBooks link for more information).
	 */
	private static int levDist(String s0, String s1) {
		int len0 = s0.length() + 1;
		int len1 = s1.length() + 1;

		// the array of distances
		int[] cost = new int[len0];
		int[] newcost = new int[len0];

		// initial cost of skipping prefix in String s0
		for (int i = 0; i < len0; i++)
			cost[i] = i;

		// dynamicaly computing the array of distances

		// transformation cost for each letter in s1
		for (int j = 1; j < len1; j++) {
			// initial cost of skipping prefix in String s1
			newcost[0] = j;

			// transformation cost for each letter in s0
			for (int i = 1; i < len0; i++) {
				// matching current letters in both strings
				int match = (s0.charAt(i - 1) == s1.charAt(j - 1)) ? 0 : 1;

				// computing cost for each transformation
				int cost_replace = cost[i - 1] + match;
				int cost_insert = cost[i] + 1;
				int cost_delete = newcost[i - 1] + 1;

				// keep minimum cost
				newcost[i] = Math.min(Math.min(cost_insert, cost_delete),
						cost_replace);
			}

			// swap cost/newcost arrays
			int[] swap = cost;
			cost = newcost;
			newcost = swap;
		}

		// the distance is the cost for transforming all letters in both strings
		return cost[len0 - 1];
	}

	/**
	 * Checks to see if we have non-ASCII characters. Used to check if we need to fuzzy match filenames instead of
	 * exact match.
	 *
	 * @param input The string to check for non-ASCII characters.
	 * @return whether or not there are non-ASCII characters.
	 */
	private static boolean isAllASCII(String input) {
		boolean isASCII = true;
		for (int i = 0; i < input.length(); i++) {
			int c = input.charAt(i);
			if (c > 0x7F) {
				isASCII = false;
				break;
			}
		}
		return isASCII;
	}

	/**
	 * If this links to a file in the Content Collection, the findXID method tries to match it up to a particular
	 * CMS id, rather than the absolute link.
	 *
	 * @throws IOException
	 * @throws SAXException
	 */
	private void findXID() throws IOException, SAXException {
		List<File> candidates;
		String[] splitted = url.split("/");
		String filename = splitted[splitted.length - 1];
		String xmlFilename = filename + ".xml";

		candidates = parent.getCourse()
				.getXMLFiles().stream()
				.filter(x -> x.getName().equals(xmlFilename))
				.collect(Collectors.toList());

		String prefix = "https://bblearn.nau.edu/bbcswebdav/xid-";
		if (candidates.size() == 0) {
			if (isAllASCII(url)) {
				xid = "NOT FOUND IN COLLECTION";
			} else {
				xid = "NON-ASCII CHARS IN LINK"; //rarely happens
			}
		} else if (candidates.size() == 1) {
			xid = prefix + getIdentifier(candidates.get(0)).split("#")[0];
		} else {
			String regex = "/courses/[0-9]{4}-NAU[0-9]{2}-[A-Z]{2,4}-"
					+ "[0-9]{3}[A-Z]{0,2}-SEC[0-9A-Z]{1,4}-[0-9]{2,5}.NAU-PSSIS/";

			int minVal = 10000000; //absurdly high levenshtein distance, shouldn't really happen
			for (File f : candidates) {
				String[] idents = getIdentifier(f).split("#");
				String identPath = idents[1].replaceAll(regex, "");
				int currVal = levDist(url.replaceAll(" ", "%20"), identPath);
				if (currVal < minVal) {
					xid = prefix + idents[0];
					minVal = currVal;
				}
			}
		}

	}

	/**
	 * Gets the link's URL.
	 *
	 * @return the url/URL
	 */
	public String getUrl() {
		return url;
	}

	/**
	 * Gets link text/image alt text.
	 *
	 * @return the link text/image alt text
	 */
	public String getLinkText() {
		return linkText;
	}

	/**
	 * Gets xid (if any).
	 *
	 * @return the xid
	 */
	public String getXid() {
		return xid;
	}
}
