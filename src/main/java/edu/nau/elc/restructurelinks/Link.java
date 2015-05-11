package edu.nau.elc.restructurelinks;

import org.xml.sax.InputSource;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.*;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class Link {
    private final CourseItem parent;
    private final String address;
    private final String linkText;
    private String xid = "";

    public Link(String address, String text, CourseItem i, boolean discarded) throws Exception {
        parent = i;
        this.address = address;
        linkText = text;
        if (!discarded) {
            findXID();
        }
    }

    private static String getIdentifier(File f) throws Exception {
        XIDHandler handler = new XIDHandler();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser saxParser = factory.newSAXParser();
        InputStream inputStream = new FileInputStream(f);
        Reader reader = new InputStreamReader(inputStream, "UTF-8");

        InputSource is = new InputSource(reader);
        is.setEncoding("UTF-8");

        saxParser.parse(is, handler);

        return handler.getIdent();
    }

    // copied from
    // http://en.wikibooks.org/wiki/Algorithm_Implementation/Strings/Levenshtein_distance#Java
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

    private void findXID() throws Exception {
        ArrayList<File> candidates = new ArrayList<File>();
        String[] splitted = address.split("/");
        String filename = splitted[splitted.length - 1];
        String xmlFilename = filename + ".xml";

        candidates.addAll(parent.getInstance()
                .getXMLFiles().stream()
                .filter(x -> x.getName().equals(xmlFilename))
                .collect(Collectors.toList()));

        String prefix = "https://bblearn.nau.edu/bbcswebdav/xid-";
        if (candidates.size() == 0) {
            if (isAllASCII(address)) {
                xid = "NOT FOUND IN COLLECTION";
            } else {
                xid = "NON-ASCII CHARS IN LINK";
            }
        } else if (candidates.size() == 1) {
            xid = prefix + getIdentifier(candidates.get(0)).split("#")[0];
        } else {
            String regex = "/courses/[0-9]{4}-NAU[0-9]{2}-[A-Z]{2,4}-"
                    + "[0-9]{3}[A-Z]{0,2}-SEC[0-9A-Z]{1,4}-[0-9]{2,5}.NAU-PSSIS/";
            int minVal = 10000000;
            for (File f : candidates) {
                String[] idents = getIdentifier(f).split("#");
                String identPath = idents[1].replaceAll(regex, "");
                int currVal = levDist(address.replaceAll(" ", "%20"), identPath);
                if (currVal < minVal) {
                    xid = prefix + idents[0];
                    minVal = currVal;
                }
            }
        }

    }

    public String getAddress() {
        return address;
    }

    public String getLinkText() {
        return linkText;
    }

    public String getXid() {
        return xid;
    }
}
