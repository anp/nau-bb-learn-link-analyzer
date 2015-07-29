# nau-bb-learn-link-analyzer
GUI tool to check a Bb Learn 9.1 course's links for "hard" links that bypass the content management system's unique IDs.

This tool unzips an exported Bb Learn 9.1 course (`ExportFile_...`) and analyzes the contained XML files for:

* Links that point directly to files in a course's content collection, rather than using Bb Learn's XID system.
* HTML files that should be converted to Blank Pages.
* A few other odds and ends that cause problems for Bb Learn students.

###Setup/Build

1. Make sure [gradle](https://docs.gradle.org/current/userguide/installation.html) is installed. This has been tested with 2.3, 2.4, and 2.5.
2. Clone the repo.
3. Run `gradle capsule` to build the JAR.
4. Copy `build/libs/nau-bb-learn-link-analyzer-VERSION-capsule.jar` to the desired directory. Make sure to copy the one with "capsule" in the name, as it contains all needed dependencies.

###Using the tool

1. Make sure Java is set as the default to handle opening executable JARs. You may also need to add the JAR as trusted because it is not signed (as of this writing).
2. Locate the JAR file that was built in the Setup instructions.
3. Double click the file (or run `java -jar nau-bb-learn-link-analyzer-VERSION-capsule.jar` on the command line) to open the window.
4. Download an export of the BbL 9.1 course(s) (archives work too, but are usually bigger and don't offer anything this tool needs).
5. Click `Browse`, and select the ZIP file(s) of the exported course(s) (`ExportFile_...`).
6. Each course will be processed, and a separate report file written to the same directory where the exports are.

###Caveats

* Make sure to check the discarded links tab in the excel spreadsheet -- the logic for finding bad links isn't perfect.
* Sometimes the ZIP files are corrupted either in the export or download process. If a single course is running for over half an hour, something is probably wrong with the export and it should be redownloaded.
* Sometimes this tool misses links. It can happen from malformed HTML not being parsed correctly, solar flares, or the influence of Lovecraftian monsters. Usually it's because of malformed HTML, so don't be surprised if it misses something occasionally.

###Extending and Modifying the Tool

#####Detection Logic

All link-checking logic lives in `CourseItem.getHardLinks`. There's a long list of conditional statements that check if a link is bad. Add something or take something away, but beward that this logic is a bit of spaghetti code and I've found some unpredicted results from tinkering with it too much.
 
#####Parsing the course structure

The XML parsing is done (quite painfully) with the Java SAX streaming XML parsing API. Hopefully the course export format won't change significantly anytime soon, but if it does then the logic should probably be changed either in `CourseItem.java` or in `HardlinkHandler.java`/`DatHandler.java`. If the overall structure of the exports changes (i.e. Blackboard totally changes this undocumented and unsupported way to access a course), then this tool will probably require a significant rewrite of the parsers after reverse-engineering the new format. This is fairly unlikely (why re-invent the wheel?) but it's a real danger so we should get as much mileage out of this as we can.

#####Report Output

The format of the report is fairly tightly coupled to the implementation code, but it can definitely be tweaked. The beastly method `GetLinks.writeResults` handles the actual writing to the Excel sheet, which just treats each page as an endless 2D array where each new row or cell needs to be created before it can be written to.

#####GUI

Hopefully the GUI shouldn't need to change drastically, but if it does it's all handled in `GetLinksWindow`, which creates instances of `GetLinks` objects, 1 for each course export.
