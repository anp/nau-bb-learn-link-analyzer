# nau-bb-learn-link-analyzer
GUI tool to check a Bb Learn 9.1 course's links for "hard" links that bypass the content management system's unique IDs.

This tool unzips an exported Bb Learn 9.1 course ("ExportFile_...") and analyzes the contained XML files for:

* Links that point directly to files in a course's content collection, rather than using Bb Learn's XID system.
* HTML files that should be converted Blank Pages.

It's currently specific to Northern Arizona University's system, but it would be simple to change the URL patterns that are checked (see `CourseItem.java`). It's also an OK start at a Java parser for Bb Learn XML course exports.

###Using the tool

1. Clone the repo.
2. Export the Bb Learn course and download the zip (Course > Packages and Utilities > Export/Archive Course).
3. In the repo's folder, run `gradle runApp`, which will build a JAR for the future (`build/libs/...-capsule.jar`) and then run the application.
4. Click `Browse`, and select the ZIP file of the exported course (`ExportFile_...`).
5. The analyzer will run and save an XLSX file of the report, and will attempt to open it with the default OS handler. *Note: this is not currently \*nix friendly, nor is it friendly to systems where a default Excel-compatible program is not installed. This was not considered a problem at the time of implementation, all users at NAU who use this are running Windows + Office.*


