package edu.nau.elc.hardlinks.domain;

import edu.nau.elc.hardlinks.xml.DatHandler;
import edu.nau.elc.hardlinks.xml.HardlinkHandler;
import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

/**
 * Represents a single item in the course (Item, Assignment, Test, Blank Page, etc.), and contains
 * all Links that come from that item.
 */
public class CourseItem implements Comparable<CourseItem> {
	private static DocumentBuilder documentBuilder;
	private static SAXParserFactory factory = SAXParserFactory.newInstance();
	private static SAXParser saxParser;

	private final ArrayList<Link> discardedURLs = new ArrayList<>();
	private final String extension;
	private final ArrayList<Link> foundLinks = new ArrayList<>();
	private final ArrayList<Link> xidLinks = new ArrayList<>();
	private final CourseProcessor parent;
	private String collectionPath = "";
	private String contentPath = "";
	private File datFile;
	private String name;

	/**
	 * Instantiates a new Course item.
	 *
	 * @param in the XML or HTML file that the instance represents
	 * @param parent the parent GetLinks instance (multiple may be running if multiple files selected)
	 * @throws IOException
	 * @throws SAXException
	 */
	public CourseItem(File in, CourseProcessor parent) throws IOException, SAXException {
		if (saxParser == null) {
			try {
				saxParser = factory.newSAXParser();
			} catch (ParserConfigurationException pce) {
				throw new SAXException(pce);
			}
		}

		if (documentBuilder == null) {
			try {
				documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			} catch (ParserConfigurationException pce) {
				throw new SAXException(pce);
			}
		}

		this.parent = parent;
		extension = FilenameUtils.getExtension(in.getName());

		// there are two types of course items we deal with, either XML (.dat) or HTML (.htm/.html)
		// the files need to be parsed separately before their text content is passed to the same link parsing logic
		if (extension.equals("dat")) {
			datFile = in;
			findXMLHardLinks(in);

		} else if (extension.equals("htm") || extension.equals("html")) {
			findAndSetDatFile(in); // find the dat file that corresponds to this content collection item

			findHTMLHardLinks(in);

			collectionPath = in.getAbsolutePath().replace(
					parent.getCCDir().getAbsolutePath(), "");
		}

		findAndSetContentPath();
	}

	public int compareTo(CourseItem other) {
		return other.foundLinks.size() - foundLinks.size();
	}

	/**
	 * Gets the item's content collection path.
	 *
	 * @return the content collection path
	 */
	public String getCollectionPath() {
		return collectionPath;
	}

	/**
	 * Gets content path within course structure.
	 *
	 * @return the content path (starting from left-hand navigation menu)
	 */
	public String getContentPath() {
		return contentPath;
	}

	/**
	 * Gets the corresponding XML file.
	 *
	 * @return the corresponding XML file
	 */
	public File getDatFile() {
		return datFile;
	}

	private void findAndSetDatFile(File in) throws IOException, SAXException {
		// brute force our way through all of the course's XML files
		// this would be really slow using DOM, so we'll use the clunky SAX parser
		for (File f : parent.getDatFiles()) {
			DatHandler handler = new DatHandler();

			InputStream inputStream = new FileInputStream(f);
			Reader reader = new InputStreamReader(inputStream, "UTF-8");

			InputSource is = new InputSource(reader);
			is.setEncoding("UTF-8");

			saxParser.parse(is, handler);

			if (in.getName().equals(handler.getLinkName())) {
				datFile = f;
				break; // forgot this before, nice speed-up from busting out of the loop when we have what we want
			}
		}
	}

	/**
	 * Gets a list of Links that are probably not a problem.
	 *
	 * @return the discarded URLs (probably not bad links)
	 */
	public ArrayList<Link> getDiscardedURLs() {
		return discardedURLs;
	}

	/**
	 * Gets found (probably bad) links.
	 *
	 * @return the found links
	 */
	public ArrayList<Link> getHardLinks() {
		return foundLinks;
	}

	/**
	 * Gets XID (probably good) links.
	 *
	 * @return the XID links
	 */
	public ArrayList<Link> getXIDLinks() {
		return xidLinks;
	}

	private void findHardLinks(String htmlContent) throws IOException, SAXException {
		// this is the main event, check some HTML for bad links
		Document doc = Jsoup.parse(htmlContent);
		TreeMap<String, String> links = new TreeMap<>();

		// get all of the a tags and img tags from the html
		// add them to a sorted map
		for (Element e : doc.select("a")) {
			links.put("text: " + e.text(), e.attr("href"));
		}

		for (Element e : doc.select("img")) {
			links.put("alt: " + e.attr("alt"), e.attr("src"));
		}


		for (Map.Entry<String, String> link : links.entrySet()) {
			String url = link.getValue().trim();
			String urlText = link.getKey().trim();
			if (url.length() == 0) {
				continue;
			}

			// this custom JSP parameter just stands for our URL
			// this can be changed to any base URL, and should probably be made configurable if we ever change URL
			url = url.replace("@X@EmbeddedFile.requestUrlStub@X@", "https://bblearn.nau.edu/").toLowerCase();

			if (url.startsWith("%20")) url = url.replaceFirst("%20", "");
			url = url.replace("%0d", "");

			// OWA redirect links are bad since students can't log in to OWA
			if (url.contains("iris.nau.edu/owa/redir.aspx")) {
				foundLinks.add(new Link(url, urlText, this, false));

			} else if ((url.contains("ppg/") && contentPath.equals("Tests, Surveys & Pools")) ||
					url.equalsIgnoreCase("about:blank") ||
					url.contains("@X@EmbeddedFile.location@X@")) {
				// if it's a pearson test image, or it's an embedded image, or it's javascript (blech), then we can ignore it

				this.discardedURLs.add(new Link(url, urlText, this, false));

			} else if (url.contains("xid") && url.contains("bbcswebdav")) {
				// if it points to WebDAV and has xid in it, we can assume it's using the CMS properly (most of the time)
				this.xidLinks.add(new Link(url, urlText, this, true));

			} else if ((url.startsWith("http://") || url.startsWith("https://") || url.startsWith("www"))
					&& !url.contains("bblearn") && !url.contains("vista")) {
				// if it doesn't match these criteria, we can be pretty sure it points outside of bblearn
				this.discardedURLs.add(new Link(url, urlText, this, true));

			} else if (url.contains("/images/ci/")) {
				// these are images embedded by the TinyMCE/VTBE content editor
				this.discardedURLs.add(new Link(url, urlText, this, true));

			} else if (
					(url.contains("courses") || url.contains("webapp") || url.contains("bbcswebdav") || url.contains("webct") || url.contains("vista"))
							&& !url.contains("/institution/")
							&& !url.contains("execute/viewdocumentation?")
							&& !url.contains("wvms-bb-bblearn")
							&& !url.contains("bb-collaborate-bblearn")
							&& !url.contains("webapps/vtbe-tinymce/tiny_mce")
							&& !url.contains("webapps/login")
							&& !url.contains("webapps/portal")
							&& !url.contains("bbgs-nbc-content-integration-bblearn")
							&& !url.contains("bb-selfpear-bblearn")) {

				// if it definitely points to bb learn (wasn't filtered out above
				// and it doesn't also point to a bunch of areas that have their links managed by B2s or content items
				// then it's probably a copypasta link done by the instructor. BAD!

				foundLinks.add(new Link(url, urlText, this, false));


			} else if (!url.startsWith("https://") && !url.startsWith("http://")
					&& !url.startsWith("javascript:")
					&& !url.startsWith("mailto:") && !url.startsWith("#")
					&& !url.contains("webapp")
					&& !url.startsWith("data:image/")
					&& !url.contains(".com")
					&& !url.contains(".net")
					&& !url.contains(".edu")
					&& !url.contains(".org")
					&& !url.contains("//cdn.slidesharecdn.com/")) {

				// if it doesn't point outside of bblearn, and it doesn't explicitly point to bb learn,
				// then it's a relative link (shame on you, instructor!), so we'll flag it as it will cause
				// permissions issues

				foundLinks.add(new Link(url, urlText, this, false));


			} else {
				// this catch all doesn't seem to be used often, but we want to make sure we are capturing all
				// found links just in case the detection logic has a hole in it that's not yet discovered

				this.discardedURLs.add(new Link(url, urlText, this, true));
			}
		}

	}

	private void findHTMLHardLinks(File html) throws SAXException, IOException {
		// we want to capture a little bit of metadata about the html file
		// then read it into memory and have JSoup parse it

		name = html.getName();
		int xidIndex = name.lastIndexOf("__xid");
		if (xidIndex > -1) {
			name = name.substring(0, xidIndex);
		}

		BufferedReader rdr = new BufferedReader(new FileReader(html));
		String text = "";
		String line = rdr.readLine();

		while (line != null) {
			text += line;
			line = rdr.readLine();
		}

		rdr.close();
		findHardLinks(text);
	}

	/**
	 * Gets the course processor this item belongs to.
	 *
	 * @return the course processor that contains this item
	 */
	public CourseProcessor getCourse() {
		return parent;
	}

	/**
	 * Gets the name/title of the item.
	 *
	 * @return the name/title
	 */
	public String getName() {
		return name;
	}

	private String getNodeTitle(org.w3c.dom.Element item) {
		return item.getElementsByTagName("title").item(0).getTextContent();
	}

	private String getPathToNode(org.w3c.dom.Element e) {
		StringBuilder build = new StringBuilder();

		org.w3c.dom.Element parent = (org.w3c.dom.Element) e.getParentNode();
		while (parent.getTagName().equals("item")) {
			String nodeTitle = getNodeTitle(parent);
			if (!nodeTitle.equals("--TOP--")) {
				build.insert(0, nodeTitle);
				build.insert(0, '\\');
			}
			parent = (org.w3c.dom.Element) parent.getParentNode();
		}

		build.append('\\');

		return build.toString();
	}

	private void findXMLHardLinks(File dat) throws IOException, SAXException {
		// here we need to parse the content item's XML file before
		// we check the text for links

		HardlinkHandler handler = new HardlinkHandler();
		InputStream inputStream = new FileInputStream(dat);
		Reader reader = new InputStreamReader(inputStream, "UTF-8");

		InputSource is = new InputSource(reader);
		is.setEncoding("UTF-8");

		saxParser.parse(is, handler);

		name = handler.getTitle();
		contentPath = handler.getType();
		if (contentPath.equalsIgnoreCase("Tests, Surveys & Pools")) {
			name = handler.getAssessType() + ": " + name;
		}
		findHardLinks(handler.getText());
	}

	private void findAndSetContentPath() {
		if (!contentPath.equals("")) {
			return;
		}
		if (datFile == null) {
			contentPath = "NOT DEPLOYED?";
			return;
		}
		String datString = datFile.getName();
		datString = datString.substring(0, datString.lastIndexOf('.'));

		NodeList nl = parent.getDOM();

		for (int i = 0; i < nl.getLength(); i++) {
			Node curr = nl.item(i);
			if (curr instanceof org.w3c.dom.Element) {
				org.w3c.dom.Element e = (org.w3c.dom.Element) curr;

				String datRef = e.getAttribute("identifierref");

				if (datRef.equalsIgnoreCase(datString)) {
					contentPath = getPathToNode(e);
					if (extension.equals("htm") || extension.equals("html")) {
						name = getNodeTitle(e);
					}
					break;
				}
			}
		}

		if (contentPath == null) {
			contentPath = "";
		}
	}
}
