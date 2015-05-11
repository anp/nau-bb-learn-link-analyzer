package edu.nau.elc.restructurelinks;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.swing.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.*;
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

public class GetLinks extends SwingWorker<Void, String> {

    private static final String xidString = "__xid-[0-9]{6,8}_[0-9]";
    private static final Pattern xidPattern = Pattern.compile(xidString);
    private final File in;
    private final GetLinkWindow parent;
    private File ccBaseDir;
    private int counted;
    private ArrayList<File> datFiles = new ArrayList<>();
    private NodeList manifestNodes;
    private int maxCount;
    private ArrayList<File> xmlFiles = new ArrayList<>();

    GetLinks(File input, GetLinkWindow window) {
        in = input;
        parent = window;
    }

    private void buildDOM(File manifest) throws Exception {
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory
                .newInstance();
        DocumentBuilder builder = builderFactory.newDocumentBuilder();
        Document document = builder.parse(new FileInputStream(manifest));
        manifestNodes = document.getDocumentElement().getElementsByTagName(
                "item");
    }

    private int countRelevantFiles(File[] files) {
        int numFiles = 0;

        numFiles += getFilesOfExt(files, ".dat").size();
        numFiles += getFilesOfExt(files, ".html").size();
        numFiles += getFilesOfExt(files, ".htm").size();

        return numFiles;
    }

    @Override
    public Void doInBackground() throws Exception {
        String path = in.getAbsolutePath().replace(in.getName(), "");

        //String className = in.getName().replaceAll("ExportFile_", "");
        //className = className.substring(0, className.lastIndexOf("_"));

        String timeStamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
        File outFolder = Files.createTempDirectory("restructure_" + timeStamp)
                .toFile();

        publish("\nExtracting files...");
        extractAllFiles(in.getAbsolutePath(), outFolder.getAbsolutePath());

        ccBaseDir = new File(outFolder.getAbsolutePath() + File.separatorChar
                + "csfiles" + File.separatorChar + "home_dir");

        sanitizeXIDFilenames(outFolder);
        xmlFiles = getFilesOfExt(outFolder.listFiles(), ".xml");
        datFiles = getFilesOfExt(outFolder.listFiles(), ".dat");

        File manifest = new File(outFolder.getAbsolutePath()
                + File.separatorChar + "imsmanifest.xml");

        publish("Analyzing course structure & building model...");
        buildDOM(manifest);

        maxCount = countRelevantFiles(outFolder.listFiles());

        publish("Searching content items for bad links...");
        ArrayList<CourseItem> dats = getDats(outFolder.listFiles());
        Collections.sort(dats);

        publish("Searching for HTML files and their bad links...");
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

        publish("Writing report to:\n" + reportPath + "\n");
        writeResults(reportPath, dats, htmls, notDeployed);

        deleteDirectory(outFolder);

        Desktop.getDesktop().open(new File(reportPath));

        return null;
    }

    private void writeResults(String outPath, ArrayList<CourseItem> content,
                              ArrayList<CourseItem> htmlFiles, ArrayList<CourseItem> undeployed) {
        Workbook wb = new XSSFWorkbook();

        Sheet contentSheet = wb.createSheet("Content Items");
        Sheet htmlSheet = wb.createSheet("HTML Files");
        Sheet undeployedSheet = wb.createSheet("Undeployed HTML Files");
        Sheet xidSheet = wb.createSheet("x-id Links");
        Sheet discardSheet = wb.createSheet("Discarded links");

        String[] tops = {
                "Course Location",            //0
                "Content Collection Path",    //1
                "Item Name",                //2
                "Link/Alt Text",            //3
                "Link Address",                //4
                "x-id"};                    //5

        Font headerFont = wb.createFont();
        headerFont.setFontHeightInPoints((short) 10);
        headerFont.setFontName("Arial");
        headerFont.setUnderline(Font.U_SINGLE);

        CellStyle headerStyle = wb.createCellStyle();
        headerStyle.setFont(headerFont);
        headerStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
        headerStyle.setFillPattern(CellStyle.SOLID_FOREGROUND);

        Row contentHeaderRow = contentSheet.createRow(0);
        contentHeaderRow.createCell(0).setCellValue(tops[0]);
        contentHeaderRow.createCell(1).setCellValue(tops[2]);
        contentHeaderRow.createCell(2).setCellValue(tops[3]);
        contentHeaderRow.createCell(3).setCellValue(tops[4]);
        contentHeaderRow.createCell(4).setCellValue(tops[5]);
        for (Cell c : contentHeaderRow) {
            c.setCellStyle(headerStyle);
        }

        Row htmlHeaderRow = htmlSheet.createRow(0);
        htmlHeaderRow.setRowStyle(headerStyle);
        htmlHeaderRow.createCell(0).setCellValue(tops[0]);
        htmlHeaderRow.createCell(1).setCellValue(tops[1]);
        htmlHeaderRow.createCell(2).setCellValue(tops[2]);
        htmlHeaderRow.createCell(3).setCellValue(tops[3]);
        htmlHeaderRow.createCell(4).setCellValue(tops[4]);
        htmlHeaderRow.createCell(5).setCellValue(tops[5]);
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
        discardHeaderRow.createCell(3).setCellValue(tops[3]);
        discardHeaderRow.createCell(4).setCellValue(tops[4]);
        for (Cell c : discardHeaderRow) {
            c.setCellStyle(headerStyle);
        }

        int contentCurrentRow = 1;
        int discardCurrentRow = 1;
        int xidCurrentRow = 1;

        for (CourseItem i : content) {
            for (Link l : i.getFoundLinks()) {
                Row r = contentSheet.createRow(contentCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getName());
                r.createCell(2).setCellValue(l.getLinkText());
                r.createCell(3).setCellValue(l.getAddress());
                r.createCell(4).setCellValue(l.getXid());
                contentCurrentRow++;

            }

            for (Link l : i.getDiscardedURLs()) {
                Row r = discardSheet.createRow(discardCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
                r.createCell(3).setCellValue(l.getLinkText());
                r.createCell(4).setCellValue(l.getAddress());
                discardCurrentRow++;
            }

            for (Link l : i.getXIDLinks()) {
                Row r = xidSheet.createRow(xidCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
                r.createCell(3).setCellValue(l.getLinkText());
                r.createCell(4).setCellValue(l.getAddress());
                xidCurrentRow++;
            }
        }

        int htmlCurrentRow = 1;
        for (CourseItem i : htmlFiles) {
            Matcher m = Pattern.compile("(DVD|VT)[0-9]{1,6}_").matcher(i.getName());
            if (m.find()) {
                continue;
            }

            if (i.getFoundLinks().size() == 0) {
                Row r = htmlSheet.createRow(htmlCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
                r.createCell(3).setCellValue("NO BAD LINKS FOUND, CONVERT TO BLANK PG?");
                htmlCurrentRow++;
            }

            for (Link l : i.getFoundLinks()) {
                Row r = htmlSheet.createRow(htmlCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
                r.createCell(3).setCellValue(l.getLinkText());
                r.createCell(4).setCellValue(l.getAddress());
                r.createCell(5).setCellValue(l.getXid());
                htmlCurrentRow++;
            }

            for (Link l : i.getDiscardedURLs()) {
                Row r = discardSheet.createRow(discardCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
                r.createCell(3).setCellValue(l.getLinkText());
                r.createCell(4).setCellValue(l.getAddress());
                discardCurrentRow++;
            }

            for (Link l : i.getXIDLinks()) {
                Row r = xidSheet.createRow(xidCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
                r.createCell(3).setCellValue(l.getLinkText());
                r.createCell(4).setCellValue(l.getAddress());
                xidCurrentRow++;
            }
        }

        int undeployedCurrentRow = 1;
        for (CourseItem i : undeployed) {
            Matcher m = Pattern.compile("(DVD|VT)[0-9]{1,6}_").matcher(i.getName());
            if (m.find()) {
                continue;
            }

            if (i.getFoundLinks().size() == 0) {
                Row r = undeployedSheet.createRow(undeployedCurrentRow);
                r.createCell(0).setCellValue(i.getCollectionPath());
                r.createCell(1).setCellValue(i.getName());
                r.createCell(2).setCellValue("NO BAD LINKS FOUND, CONSIDER DELETE");
                undeployedCurrentRow++;
            }

            for (Link l : i.getFoundLinks()) {
                Row r = undeployedSheet.createRow(undeployedCurrentRow);
                r.createCell(0).setCellValue(i.getCollectionPath());
                r.createCell(1).setCellValue(i.getName());
                r.createCell(2).setCellValue(l.getLinkText());
                r.createCell(3).setCellValue(l.getAddress());
                r.createCell(4).setCellValue(l.getXid());
                htmlCurrentRow++;
            }

            for (Link l : i.getDiscardedURLs()) {
                Row r = discardSheet.createRow(discardCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
                r.createCell(3).setCellValue(l.getLinkText());
                r.createCell(4).setCellValue(l.getAddress());
                discardCurrentRow++;
            }

            for (Link l : i.getXIDLinks()) {
                Row r = xidSheet.createRow(xidCurrentRow);
                r.createCell(0).setCellValue(i.getContentPath());
                r.createCell(1).setCellValue(i.getCollectionPath());
                r.createCell(2).setCellValue(i.getName());
                r.createCell(3).setCellValue(l.getLinkText());
                r.createCell(4).setCellValue(l.getAddress());
                xidCurrentRow++;
            }
        }

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

            publish("Found & recorded " + totalWritten + " probable bad links.");
            discardCurrentRow -= 1;
            xidCurrentRow -= 1;
            publish("Found & discarded " + discardCurrentRow + " links");
            publish("Found & recorded " + xidCurrentRow + " x-id links");
            publish("Complete!");
            out.close();
        } catch (IOException e) {
            publish("ERROR: cannot write report file.");
            publish(e.getLocalizedMessage());
        }
    }

    private void extractAllFiles(String file, String outputDir)
            throws ZipException {
        ZipFile zipFile = new ZipFile(file);
        zipFile.extractAll(outputDir);
    }

    public File getCCDir() {
        return ccBaseDir;
    }

    public ArrayList<File> getDatFiles() {
        return datFiles;
    }

    private ArrayList<CourseItem> getDats(File[] files) throws Exception {
        ArrayList<CourseItem> datItems = new ArrayList<>();
        for (File f : getFilesOfExt(files, ".dat")) {
            tickProgress();
            datItems.add(new CourseItem(f, this));
        }
        return datItems;
    }

    public NodeList getDOM() {
        return manifestNodes;
    }

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

    private ArrayList<CourseItem> getHTMLFiles(File[] files) throws Exception {
        ArrayList<CourseItem> htmlFiles = new ArrayList<>();
        for (File f : getFilesOfExt(files, ".htm")) {
            tickProgress();
            htmlFiles.add(new CourseItem(f, this));
        }
        for (File f : getFilesOfExt(files, ".html")) {
            tickProgress();
            htmlFiles.add(new CourseItem(f, this));
        }
        return htmlFiles;
    }

    public ArrayList<File> getXMLFiles() {
        return xmlFiles;
    }

    @Override
    protected void process(List<String> chunks) {
        for (String s : chunks) {
            parent.textArea.append(s + "\n");
        }
    }

    private void sanitizeXIDFilenames(File dir) {
        if (dir != null) {
            File[] files = dir.listFiles();
            if (files != null && files.length > 0) {
                for (File f : files) {
                    String newPath = f.getAbsolutePath().replace(f.getName(), "")
                            + xidPattern.matcher(f.getName()).replaceAll("");
                    File f2 = new File(newPath);
                    f.renameTo(f2);
                    if (f2.isDirectory()) {
                        sanitizeXIDFilenames(f2);
                    }
                }
            }
        }
    }

    private void tickProgress() {
        counted++;
        setProgress((counted * 100) / maxCount);
    }

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
