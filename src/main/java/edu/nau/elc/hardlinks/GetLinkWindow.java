package edu.nau.elc.hardlinks;

import edu.nau.elc.hardlinks.domain.CourseProcessor;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.File;


/**
 * The entry point for this application and the GUI class which handles display.
 */
public class GetLinkWindow extends JFrame {

	private JTextArea textArea;
    private JButton browse;
    private JFrame frmGetTriageLinks;
    private JProgressBar progressBar;

    private GetLinkWindow() {
        initialize();
    }

	/**
	 * The entry point of application.
	 *
	 * Constructs the GetLinkWindow object and waits for user input.
	 *
	 * @param args Should be empty, all arguments are ignored.
	 */
	public static void main(String[] args) {
        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager
                        .getSystemLookAndFeelClassName());
                GetLinkWindow window = new GetLinkWindow();
                window.frmGetTriageLinks.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, e.getLocalizedMessage(),
                        "ERROR", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

	/**
	 * Build the GUI window and define the behavior of the Browse button.
	 */
    private void initialize() {
        frmGetTriageLinks = new JFrame();
        frmGetTriageLinks.setTitle("Get Triage Links");

		// set some arbitary starting sizes
		// could be better, but gets the job done
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
        int left = dim.width / 2 - 350;
        int top = dim.height / 2 - 250;

        frmGetTriageLinks.setBounds(left, top, 700, 400);
        frmGetTriageLinks.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        this.setLocation(this.getSize().width / 2,
				dim.height / 2 - this.getSize().height / 2);
        frmGetTriageLinks.getContentPane().setLayout(new BoxLayout(frmGetTriageLinks.getContentPane(), BoxLayout.Y_AXIS));

        JLabel lblPleaseChooseAn = new JLabel(
                "Please choose an exported zip to analyze");
        lblPleaseChooseAn.setHorizontalAlignment(SwingConstants.CENTER);
        lblPleaseChooseAn.setFont(new Font("Tahoma", Font.PLAIN, 15));
        frmGetTriageLinks.getContentPane().add(lblPleaseChooseAn);

        browse = new JButton("Browse...");
        frmGetTriageLinks.getContentPane().add(browse);

        textArea = new JTextArea();
        textArea.setLineWrap(true);

		// we want the text log to scroll to the bottom as messages are added
        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setViewportView(textArea);
        frmGetTriageLinks.getContentPane().add(scrollPane);

        progressBar = new JProgressBar();
        frmGetTriageLinks.getContentPane().add(progressBar);

		// define the browse button's behavior
		browse.addActionListener(e -> {
			browse.setEnabled(false);
			browse.setText("Working...");
			progressBar.setValue(0);

			// we only want users to upload zip files
			JFileChooser chooser = new JFileChooser();
			FileNameExtensionFilter filter = new FileNameExtensionFilter(
					"ZIP Archives", "zip");
			chooser.setFileFilter(filter);

			// default to the user's downloads directory if it exists
			// should work on Windows, OS X and most Linux DEs
			File downloadsDir = new File(System.getProperty("user.home") + File.separatorChar + "Downloads");
			if (downloadsDir.exists() && downloadsDir.isDirectory()) {
				chooser.setCurrentDirectory(downloadsDir);
			}

			// let the user select multiple files
			chooser.setMultiSelectionEnabled(true);

			int returnVal = chooser.showOpenDialog(GetLinkWindow.this);

			textArea.append("************************************************\n");
			textArea.append("Waiting for file...\n");

			// start processing selected files if we hit OK
			if (returnVal == JFileChooser.APPROVE_OPTION) {
				File[] selected = chooser.getSelectedFiles();
				progressBar.setMaximum(selected.length);
				for (File f : selected) {
					runAnalysis(f);
				}

			} else if (returnVal == JFileChooser.CANCEL_OPTION) {
				// back out and re-enable the browse button if we cancelled
				textArea.append("File selection cancelled.\n");
				browse.setEnabled(true);
				browse.setText("Browse...");
			}
		});
    }

	/**
	 * Run once per ZIP file selected. Initiates background processing and registers a
	 * listener for progress updates.
	 *
	 * @param selected The ZIP file to be processed.
	 */
    private void runAnalysis(File selected) {
		// this gets run once per file that the user selects

        try {
            CourseProcessor current = new CourseProcessor(selected, this);

            current.addPropertyChangeListener(e -> {
                if ("progress".equals(e.getPropertyName())) {
                    progressBar.setIndeterminate(false);
					progressBar.setValue(progressBar.getValue() + 1);
				}

				// this checks to see if we've processed all of the files
				// it's a bit fragile and a more robust "done!" tracking mechanism would be nice
				if (progressBar.getValue() >= progressBar.getMaximum()) {
                    progressBar.setValue(0);
                    browse.setEnabled(true);
                    browse.setText("Browse...");
					textArea.append("All done!");
				}
			});

            current.execute();

        } catch (Exception e) {
            textArea.append(e.getLocalizedMessage());
        }
    }

	/**
	 * Prints a message to the window's text area log.
	 *
	 * @param msg The message to be printed. Will have a newline appended to it before printing.
	 */
	public void println(String msg) {
		textArea.append(msg + "\n");
	}
}
