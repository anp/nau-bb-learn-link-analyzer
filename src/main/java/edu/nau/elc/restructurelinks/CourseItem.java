package edu.nau.elc.restructurelinks;

import org.apache.commons.io.FilenameUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public class CourseItem implements Comparable<CourseItem> {
	private final ArrayList<Link> discardedURLs = new ArrayList<>();
	private final String extension;
	private final ArrayList<Link> foundLinks = new ArrayList<>();
	private final ArrayList<Link> xidLinks = new ArrayList<>();
	private final GetLinks parent;
	private String collectionPath = "";
	private String contentPath = "";
	private File datFile;
	private String name;

	public CourseItem(File in, GetLinks parent) throws Exception {
		this.parent = parent;
		extension = FilenameUtils.getExtension(in.getName());

		if (extension.equals("dat")) {
			datFile = in;
			foundLinks.addAll(getXMLHardLinks(in));
		} else if (extension.equals("htm") || extension.equals("html")) {
			setDatFile(in);
			foundLinks.addAll(getHTMLFileLinks(in));
			collectionPath = in.getAbsolutePath().replace(
					parent.getCCDir().getAbsolutePath(), "");
		}

		setContentPath();
	}

	public int compareTo(CourseItem other) {
		return other.foundLinks.size() - foundLinks.size();
	}

	public String getCollectionPath() {
		return collectionPath;
	}

	public String getContentPath() {
		return contentPath;
	}

	public File getDatFile() {
		return datFile;
	}

	private void setDatFile(File in) throws Exception {
		for (File f : parent.getDatFiles()) {
			DatHandler handler = new DatHandler();
			SAXParserFactory factory = SAXParserFactory.newInstance();
			SAXParser saxParser = factory.newSAXParser();
			InputStream inputStream = new FileInputStream(f);
			Reader reader = new InputStreamReader(inputStream, "UTF-8");

			InputSource is = new InputSource(reader);
			is.setEncoding("UTF-8");

			saxParser.parse(is, handler);

			if (in.getName().equals(handler.getLinkName())) {
				datFile = f;
			}
		}
	}

	public ArrayList<Link> getDiscardedURLs() {
		return discardedURLs;
	}

	public ArrayList<Link> getFoundLinks() {
		return foundLinks;
	}

	public ArrayList<Link> getXIDLinks() {
		return xidLinks;
	}

	private ArrayList<Link> getHardLinks(String htmlContent) throws Exception {
		Document doc = Jsoup.parse(htmlContent);
		TreeMap<String, String> hardlinks = new TreeMap<>();

		for (Element e : doc.select("a")) {
			hardlinks.put("text: " + e.text(), e.attr("href"));
		}

		for (Element e : doc.select("img")) {
			hardlinks.put("alt: " + e.attr("alt"), e.attr("src"));
		}

		ArrayList<Link> hardlinksNoDupes = new ArrayList<>();
		for (Map.Entry<String, String> link : hardlinks.entrySet()) {
			String url = link.getValue().trim();
			if (url.length() == 0) {
				continue;
			}
			String urlText = link.getKey();
			url = url.replace("@X@EmbeddedFile.requestUrlStub@X@", "https://bblearn.nau.edu/").toLowerCase();

			if (url.startsWith("%20")) url = url.replaceFirst("%20", "");
			url = url.replace("%0d", "");

			if (url.contains("iris.nau.edu/owa/redir.aspx")) {
				hardlinksNoDupes.add(new Link(url, urlText, this, false));

			} else if ((url.contains("ppg/") && contentPath.equals("Tests, Surveys & Pools")) ||
					url.equalsIgnoreCase("about:blank") ||
					url.contains("@X@EmbeddedFile.location@X@")) {
				this.discardedURLs.add(new Link(url, urlText, this, false));

			} else if (url.contains("xid") && url.contains("bbcswebdav")) {
				this.xidLinks.add(new Link(url, urlText, this, true));

			} else if ((url.startsWith("http://") || url.startsWith("https://") || url.startsWith("www"))
					&& !url.contains("bblearn") && !url.contains("vista")) {
				this.discardedURLs.add(new Link(url, urlText, this, true));

			} else if (url.contains("/images/ci/")) {
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
				hardlinksNoDupes.add(new Link(url, urlText, this, false));


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
				hardlinksNoDupes.add(new Link(url, urlText, this, false));


			} else {
				this.discardedURLs.add(new Link(url, urlText, this, true));
			}
		}
		return hardlinksNoDupes;
	}

	private ArrayList<Link> getHTMLFileLinks(File html) throws Exception {
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
		return getHardLinks(text);
	}

	public GetLinks getInstance() {
		return parent;
	}

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

	private ArrayList<Link> getXMLHardLinks(File dat) throws Exception {
		HardlinkHandler handler = new HardlinkHandler();
		SAXParserFactory factory = SAXParserFactory.newInstance();
		SAXParser saxParser = factory.newSAXParser();
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
		return getHardLinks(handler.getText());
	}

	private void setContentPath() {
		if (!contentPath.equals("")) {
			return;
		}
		if (datFile == null) {
			contentPath = "NOT DEPLOYED???";
			return;
		}
		String datString = datFile.getName();
		datString = datString.substring(0, datString.lastIndexOf('.'));

		NodeList nl = parent.getDOM();
		org.w3c.dom.Element thisNode;

		for (int i = 0; i < nl.getLength(); i++) {
			Node curr = nl.item(i);
			if (curr instanceof org.w3c.dom.Element) {
				org.w3c.dom.Element e = (org.w3c.dom.Element) curr;
				String datRef = e.getAttribute("identifierref");
				if (datRef.equalsIgnoreCase(datString)) {
					thisNode = e;
					contentPath = getPathToNode(thisNode);
					if (extension.equals("htm") || extension.equals("html")) {
						name = getNodeTitle(thisNode);
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
