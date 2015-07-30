package edu.nau.elc.hardlinks.domain;

import edu.nau.elc.hardlinks.GetLinkWindow;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * One instance of GetLinks is constructed for each export file to process. It begins processing when doInBackground()
 * is called.
 */
public class CourseProcessor extends SwingWorker<Void, String> {

    private static final String xidString = "__xid-[0-9]{6,8}_[0-9]";
    private static final Pattern xidPattern = Pattern.compile(xidString);
    private final File in;
    private final GetLinkWindow parent;
    private File ccBaseDir;
    private ArrayList<File> datFiles = new ArrayList<>();
    private NodeList manifestNodes;
    private ArrayList<File> xmlFiles = new ArrayList<>();

	/**
	 * Instantiates a new GetLinks object, ready for processing.
	 *
	 * @param input  ZIP file that's a course export.
	 * @param window The parent window that we'll print status messages to.
	 */
	public CourseProcessor(File input, GetLinkWindow window) {
		in = input;
        parent = window;
	}

	/**
	 * This takes a bb-manifest.xml and gives us a DOM of the course content structure.
	 *
	 * @param manifest bb-manifest.xml file we want to parse.
	 * @throws IOException                  If the manifest file can't be read.
	 * @throws SAXException                 If it's not valid XML.
	 * @throws ParserConfigurationException Ask the JDK why this gets thrown.
	 */
	private void buildDOM(File manifest) throws IOException, SAXException, ParserConfigurationException {
		DocumentBuilderFactory builderFactory = DocumentBuilderFactory
                .newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        Document document = builder.parse(new FileInputStream(manifest));
        manifestNodes = document.getDocumentElement().getElementsByTagName(
				"item");
	}

	/**
	 * Begins background processing of the ZIP export.
	 * @return Nothing.
	 * @throws IOException If a file in the ZIP can't be read.
	 * @throws SAXException If we encounter invalid XML that SAX can't parse at all.
	 * @throws ParserConfigurationException If for some bizarre reason we can't configure the parser.
	 */
	@Override
	public Void doInBackground() throws IOException, SAXException, ParserConfigurationException {
		String path = in.getAbsolutePath().replace(in.getName(), "");

        //String className = in.getName().replaceAll("ExportFile_", "");
        //className = className.substring(0, className.lastIndexOf("_"));

        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
		File outFolder = Files.createTempDirectory("restructure_" + timeStamp).toFile();

		//publish("\nExtracting files...");
		extractAllFiles(in.getAbsolutePath(), outFolder.getAbsolutePath());

        ccBaseDir = new File(outFolder.getAbsolutePath() + File.separatorChar
                + "csfiles" + File.separatorChar + "home_dir");

        sanitizeXIDFilenames(outFolder);
        xmlFiles = getFilesOfExt(outFolder.listFiles(), ".xml");
        datFiles = getFilesOfExt(outFolder.listFiles(), ".dat");

        File manifest = new File(outFolder.getAbsolutePath()
                + File.separatorChar + "imsmanifest.xml");

		//publish("Analyzing course structure & building model...");
		buildDOM(manifest);

		//publish("Searching content items for bad links...");
		ArrayList<CourseItem> dats = getDats(outFolder.listFiles());
		Collections.sort(dats);

		//publish("Searching for HTML files and their bad links...");
		ArrayList<CourseItem> htmls = getHTMLFiles(outFolder.listFiles());


        Collections.sort(htmls);
        ArrayList<CourseItem> notDeployed = new ArrayList<>();

        Iterator<CourseItem> iter = htmls.iterator();
        while (iter.hasNext()) {
            CourseItem i = iter.next();
            if (i.getDatFile() == null) {
                iter.remove();
                notDeployed.add(i);
            }
        }

        String reportPath = path + in.getName().
                substring(0, in.getName().lastIndexOf('_'))
                .replace("ExportFile", "triage") + ".xlsx";

		//publish("Writing report to:\n" + reportPath + "\n");
		writeResults(reportPath, dats, htmls, notDeployed);

        deleteDirectory(outFolder);

		//Desktop.getDesktop().open(new File(reportPath));
		setProgress(1);

		return null;
	}

	/**
	 * Writes our results to an Excel sheet. Is a bit of a beast because POI doesn't have the most concise
	 * syntax. Fairly tightly coupled to the CourseItem and Link implementations, but can definitely be tweaked
	 * if need be. All styling and formatting is handled in code, rather than configuration, since this is only
	 * used a few dozen times a semester.
	 *
	 * @param outPath The path to write the report to (usually is the path of the export file as well).
	 * @param content A list of all content items found in the course.
	 * @param htmlFiles A list of all HTML files found in the content collection which are deployed in the course.
	 * @param undeployed A list of all HTML files found in the content collection which are <b>not</b> deployed.
	 */
	private void writeResults(String outPath, ArrayList<CourseItem> content,
							  ArrayList<CourseItem> htmlFiles, ArrayList<CourseItem> undeployed) {

		// all work is done in memory before writing to a file
		Workbook wb = new XSSFWorkbook();

		// first we'll create the sheets
		Sheet contentSheet = wb.createSheet("Content Items");
        Sheet htmlSheet = wb.createSheet("HTML Files");
        Sheet undeployedSheet = wb.createSheet("Undeployed HTML Files");
        Sheet xidSheet = wb.createSheet("x-id Links");
		Sheet discardSheet = wb.createSheet("Discarded links");

		// we'll define shared headers, although this is clunky in retrospect
		String[] tops = {
                "Course Location",            //0
                "Content Collection Path",    //1
                "Item Name",                //2
                "Link/Alt Text",            //3
                "Link Address",                //4
				"x-id"};                    //5

		// let's set some default styling across the board
		Font headerFont = wb.createFont();
		headerFont.setFontHeightInPoints((short) 11);
        headerFont.setFontName("Arial");
        headerFont.setUnderline(Font.U_SINGLE);

        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        headerStyle.setFillPattern(CellStyle.SOLID_FOREGROUND);

        Row contentHeaderRow = contentSheet.createRow(0);
		contentHeaderRow.createCell(0).setCellValue(tops[2]);
		contentHeaderRow.createCell(1).setCellValue(tops[4]);
		contentHeaderRow.createCell(2).setCellValue(tops[5]);
		contentHeaderRow.createCell(3).setCellValue(tops[3]);
		contentHeaderRow.createCell(4).setCellValue(tops[0]);
		for (Cell c : contentHeaderRow) {
            c.setCellStyle(headerStyle);
        }

        Row htmlHeaderRow = htmlSheet.createRow(0);
        htmlHeaderRow.setRowStyle(headerStyle);
		htmlHeaderRow.createCell(0).setCellValue(tops[2]);
		htmlHeaderRow.createCell(1).setCellValue(tops[4]);
		htmlHeaderRow.createCell(2).setCellValue(tops[5]);
		htmlHeaderRow.createCell(3).setCellValue(tops[3]);
		htmlHeaderRow.createCell(4).setCellValue(tops[0]);
		htmlHeaderRow.createCell(5).setCellValue(tops[1]);
		for (Cell c : htmlHeaderRow) {
            c.setCellStyle(headerStyle);
        }

        Row undeployedHeaderRow = undeployedSheet.createRow(0);
        undeployedHeaderRow.setRowStyle(headerStyle);
        undeployedHeaderRow.createCell(0).setCellValue(tops[1]);
        undeployedHeaderRow.createCell(1).setCellValue(tops[2]);
        undeployedHeaderRow.createCell(2).setCellValue(tops[3]);
        undeployedHeaderRow.createCell(3).setCellValue(tops[4]);
        undeployedHeaderRow.createCell(4).setCellValue(tops[5]);
        for (Cell c : undeployedHeaderRow) {
            c.setCellStyle(headerStyle);
        }

        Row xidHeaderRow = xidSheet.createRow(0);
        xidHeaderRow.setRowStyle(headerStyle);
        xidHeaderRow.createCell(0).setCellValue(tops[0]);
        xidHeaderRow.createCell(1).setCellValue(tops[1]);
        xidHeaderRow.createCell(2).setCellValue(tops[2]);
        xidHeaderRow.createCell(3).setCellValue(tops[3]);
        xidHeaderRow.createCell(4).setCellValue(tops[4]);
        for (Cell c : xidHeaderRow) {
            c.setCellStyle(headerStyle);
        }

        Row discardHeaderRow = discardSheet.createRow(0);
        discardHeaderRow.setRowStyle(headerStyle);
        discardHeaderRow.createCell(0).setCellValue(tops[0]);
        discardHeaderRow.createCell(1).setCellValue(tops[1]);
        discardHeaderRow.createCell(2).setCellValue(tops[2]);
		discardHeaderRow.createCell(3).setCellValue(tops[4]);
		discardHeaderRow.createCell(4).setCellValue(tops[3]);
		for (Cell c : discardHeaderRow) {
			c.setCellStyle(headerStyle);
		}

		//that's just for the header rows...ugh
		//i'm sure there's a better way to do this (how I long for pandas DataFrames in Java...)
		//now we can start writing the actual results

		// we need to track the current "cursor" index for all worksheets separately
		// so we know where to write each result
		int contentCurrentRow = 1;
        int discardCurrentRow = 1;
		int xidCurrentRow = 1;

		for (CourseItem i : content) {
			// first we'll process the actual hardlinks
			for (Link l : i.getHardLinks()) {
                Row r = contentSheet.createRow(contentCurrentRow);
				r.createCell(0).setCellValue(i.getName());
				r.createCell(1).setCellValue(l.getUrl());
				r.createCell(2).setCellValue(l.getXid());
				r.createCell(3).setCellValue(l.getLinkText());
				r.createCell(4).setCellValue(i.getContentPath());
				contentCurrentRow++;

            }

			// then we'll process the discards
			for (Link l : i.getDiscardedURLs()) {
                Row r = discardSheet.createRow(discardCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
				r.createCell(2).setCellValue(i.getName());
				r.createCell(3).setCellValue(l.getUrl());
				r.createCell(4).setCellValue(l.getLinkText());
				discardCurrentRow++;
            }

			// then we'll process the probably-legit xid links
			for (Link l : i.getXIDLinks()) {
                Row r = xidSheet.createRow(xidCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
				r.createCell(3).setCellValue(l.getLinkText());
				r.createCell(4).setCellValue(l.getUrl());
				xidCurrentRow++;
			}
		}

		// rinse and repeat for the deployed and undeployed HTML files

        int htmlCurrentRow = 1;
        for (CourseItem i : htmlFiles) {
            Matcher m = Pattern.compile("(DVD|VT)[0-9]{1,6}_").matcher(i.getName());
			if (m.find()) {
				continue;
            }

            if (i.getHardLinks().size() == 0) {
				Row r = htmlSheet.createRow(htmlCurrentRow);
				r.createCell(0).setCellValue(i.getName());
				r.createCell(1).setCellValue("NO BAD LINKS FOUND, CONVERT TO BLANK PG?");
				r.createCell(2);
				r.createCell(3);
				r.createCell(4).setCellValue(i.getContentPath());
				r.createCell(5).setCellValue(i.getCollectionPath());
				htmlCurrentRow++;
            }

            for (Link l : i.getHardLinks()) {
				Row r = htmlSheet.createRow(htmlCurrentRow);
				r.createCell(0).setCellValue(i.getName());
				r.createCell(1).setCellValue(l.getUrl());
				r.createCell(2).setCellValue(l.getXid());
				r.createCell(3).setCellValue(l.getLinkText());
				r.createCell(4).setCellValue(i.getContentPath());
				r.createCell(5).setCellValue(i.getCollectionPath());
				htmlCurrentRow++;
            }

            for (Link l : i.getDiscardedURLs()) {
                Row r = discardSheet.createRow(discardCurrentRow);
				r.createCell(0).setCellValue(i.getContentPath());
				r.createCell(1).setCellValue(i.getCollectionPath());
				r.createCell(2).setCellValue(i.getName());
				r.createCell(3).setCellValue(l.getUrl());
				r.createCell(4).setCellValue(l.getLinkText());
				discardCurrentRow++;
            }

            for (Link l : i.getXIDLinks()) {
                Row r = xidSheet.createRow(xidCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
				r.createCell(3).setCellValue(l.getLinkText());
				r.createCell(4).setCellValue(l.getUrl());
                xidCurrentRow++;
            }
        }

        int undeployedCurrentRow = 1;
        for (CourseItem i : undeployed) {
            Matcher m = Pattern.compile("(DVD|VT)[0-9]{1,6}_").matcher(i.getName());
			if (m.find()) {
				continue;
            }

            if (i.getHardLinks().size() == 0) {
				Row r = undeployedSheet.createRow(undeployedCurrentRow);
                r.createCell(0).setCellValue(i.getCollectionPath());
                r.createCell(1).setCellValue(i.getName());
                r.createCell(2).setCellValue("NO BAD LINKS FOUND, CONSIDER DELETE");
				undeployedCurrentRow++;
            }

            for (Link l : i.getHardLinks()) {
				Row r = undeployedSheet.createRow(undeployedCurrentRow);
                r.createCell(0).setCellValue(i.getCollectionPath());
                r.createCell(1).setCellValue(i.getName());
				r.createCell(2).setCellValue(l.getLinkText());
				r.createCell(3).setCellValue(l.getUrl());
                r.createCell(4).setCellValue(l.getXid());
                htmlCurrentRow++;
            }

            for (Link l : i.getDiscardedURLs()) {
                Row r = discardSheet.createRow(discardCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
				r.createCell(3).setCellValue(l.getLinkText());
				r.createCell(4).setCellValue(l.getUrl());
                discardCurrentRow++;
            }

            for (Link l : i.getXIDLinks()) {
                Row r = xidSheet.createRow(xidCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
				r.createCell(3).setCellValue(l.getLinkText());
				r.createCell(4).setCellValue(l.getUrl());
				xidCurrentRow++;
			}
        }

		// now we'll autosize all of the columns so it reads OK
		for (int i = 0; i < 5; i++) {
            contentSheet.autoSizeColumn(i);
            htmlSheet.autoSizeColumn(i);
            undeployedSheet.autoSizeColumn(i);
            xidSheet.autoSizeColumn(i);
            discardSheet.autoSizeColumn(i);
        }
        int totalWritten = contentCurrentRow + htmlCurrentRow + undeployedCurrentRow - 3;

        try {
            FileOutputStream out = new FileOutputStream(outPath);
            wb.write(out);

			publish("Wrote " + totalWritten + " links to " + outPath);
			out.close();
		} catch (IOException e) {
            publish("ERROR: cannot write report file.");
			publish(e.getLocalizedMessage());
		}
	}

	/**
	 * Extract's all ZIP files to a directory, preserving the internal structure of the archive.
	 * @param file ZIP file to extract.
	 * @param outputDir Directory to output to.
	 * @throws IOException
	 */
	private void extractAllFiles(String file, String outputDir)
			throws IOException {
		byte[] buffer = new byte[4096];

		File outFolder = new File(outputDir);
		if (!outFolder.exists()) outFolder.mkdir();

		ZipInputStream zipInputStream = new ZipInputStream(new FileInputStream(file));

		ZipEntry entry = zipInputStream.getNextEntry();

		while (entry != null) {
			String filename = entry.getName();
			File extracted = new File(outFolder.getAbsolutePath() + File.separatorChar + filename);
			extracted.getParentFile().mkdirs();

			FileOutputStream outputStream = new FileOutputStream(extracted);

			int length;
			while ((length = zipInputStream.read(buffer)) > 0) {
				outputStream.write(buffer, 0, length);
			}

			outputStream.flush();
			outputStream.close();

			zipInputStream.closeEntry();
			entry = zipInputStream.getNextEntry();
		}

		zipInputStream.close();
	}

	/**
	 * Gets the content collection directory.
	 *
	 * @return the content collection directory
	 */
	public File getCCDir() {
		return ccBaseDir;
	}

	/**
	 * Gets all XML files in the export
	 *
	 * @return the xml files
	 */
	public ArrayList<File> getDatFiles() {
		return datFiles;
	}

	/**
	 * Finds all files in the course export that end in ".dat".
	 *
	 * @param files Array of files at the root of the export.
	 * @return A flat list of XML files.
	 * @throws IOException
	 * @throws SAXException
	 */
	private ArrayList<CourseItem> getDats(File[] files) throws IOException, SAXException {
		ArrayList<CourseItem> datItems = new ArrayList<>();
        for (File f : getFilesOfExt(files, ".dat")) {
			datItems.add(new CourseItem(f, this));
		}
		return datItems;
	}

	/**
	 * Gets the DOM for the whole course navigation structure.
	 *
	 * @return the DOM
	 */
	public NodeList getDOM() {
		return manifestNodes;
	}

	/**
	 * Generic function to get a flat list of all files of a given extension.
	 * @param files Array of files at the root of the course export.
	 * @param ext Extension we're searching for.
	 * @return a flat list of all files matching that extension
	 */
	private ArrayList<File> getFilesOfExt(File[] files, String ext) {
        ArrayList<File> foundFiles = new ArrayList<>();
        for (File f : files) {
            String fn = f.getName();
            int extStart = fn.lastIndexOf('.');
            String extension = "";
            if (extStart > 1) {
                extension = fn.substring(extStart, fn.length());
            }

            if (f.isDirectory()) {
                foundFiles.addAll(getFilesOfExt(f.listFiles(), ext));
            } else if (extension.equals(ext)) {
				foundFiles.add(f);
			}
		}
		return foundFiles;
	}

	/**
	 * Get a flat list of all HTML files from an array of files at the root of an unzipped course export.
	 *
	 * @param files Array listing all files at the root of the course export.
	 * @return a list of all files ending in .html or .htm in the course export
	 * @throws IOException
	 * @throws SAXException
	 */
	private ArrayList<CourseItem> getHTMLFiles(File[] files) throws IOException, SAXException {
		ArrayList<CourseItem> htmlFiles = new ArrayList<>();

		for (File f : getFilesOfExt(files, ".htm")) {
			htmlFiles.add(new CourseItem(f, this));
		}

		for (File f : getFilesOfExt(files, ".html")) {
			htmlFiles.add(new CourseItem(f, this));
		}

		return htmlFiles;
	}

	/**
	 * Gets XML files in the course export.
	 *
	 * @return a list of all of the XML files in the export
	 */
	public ArrayList<File> getXMLFiles() {
		return xmlFiles;
	}

	/**
	 * Processes messages and passes them up to the parent GUI for printing.
	 * @param chunks List of messages to print.
	 */
	@Override
    protected void process(List<String> chunks) {
		for (String s : chunks) {
			parent.println(s);
		}
	}

	/**
	 * Remove xid strings from all content collection filenames. Makes getting the xid a little trickier,
	 * but simplifies seraching filesnames. Recursive, so may StackOverflow if used on an incredibly deep hierarchy.
	 * @param dir The directory to sanitize, usually the content collection root in the export.
	 */
	private void sanitizeXIDFilenames(File dir) {
        if (dir != null) {
            File[] files = dir.listFiles();
			if (files != null && files.length > 0) {
				for (File f : files) {

					String newFilename = xidPattern.matcher(f.getName()).replaceAll("");
					String newPath = f.getAbsolutePath().replace(f.getName(), "") + newFilename;

                    File f2 = new File(newPath);
                    f.renameTo(f2);

                    if (f2.isDirectory()) {
						sanitizeXIDFilenames(f2);
					}
				}
			}
		}
	}

	/**
	 * Delete directory and all contained files and subdirectories (recursive, will StackOverflow on <b>very</b> deep
	 * trees.
	 * @param dir Directory to delete.
	 */
	private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteDirectory(f);
                }
                f.delete();
            }
            dir.delete();
        }
    }
}
