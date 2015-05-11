package edu.nau.elc.restructurelinks;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.File;

public class GetLinkWindow extends JFrame {

    public JTextArea textArea;
    private JButton browse;
    private JFrame frmGetTriageLinks;
    private JProgressBar progressBar;

    private GetLinkWindow() {
        initialize();
    }

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

    private void initialize() {
        frmGetTriageLinks = new JFrame();
        frmGetTriageLinks.setTitle("Get Triage Links");

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
        browse.addActionListener(e -> {
            browse.setEnabled(false);
            browse.setText("Working...");
            progressBar.setValue(0);

            JFileChooser chooser = new JFileChooser();
            FileNameExtensionFilter filter = new FileNameExtensionFilter(
                    "ZIP Archives", "zip");
            chooser.setFileFilter(filter);

            int returnVal = chooser.showOpenDialog(GetLinkWindow.this);

            textArea.append("************************************************\n");
            textArea.append("Waiting for file...\n");

            if (returnVal == JFileChooser.APPROVE_OPTION) {
                File selected = chooser.getSelectedFile();
                progressBar.setIndeterminate(true);
                runAnalysis(selected);
            } else if (returnVal == JFileChooser.CANCEL_OPTION) {
                textArea.append("File selection cancelled.\n");
                browse.setEnabled(true);
                browse.setText("Browse...");
            }
        });

        frmGetTriageLinks.getContentPane().add(browse);

        textArea = new JTextArea();
        textArea.setLineWrap(true);

        DefaultCaret caret = (DefaultCaret) textArea.getCaret();
        caret.setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);

        JScrollPane scrollPane = new JScrollPane();
        scrollPane.setViewportView(textArea);
        frmGetTriageLinks.getContentPane().add(scrollPane);

        progressBar = new JProgressBar();
        frmGetTriageLinks.getContentPane().add(progressBar);
    }

    private void runAnalysis(File selected) {
        try {
            textArea.append("File chosen:\n" + selected.getAbsolutePath()
                    + "\n");

            GetLinks current = new GetLinks(selected, this);
            current.addPropertyChangeListener(e -> {
                if ("progress".equals(e.getPropertyName())) {
                    progressBar.setIndeterminate(false);
                    progressBar.setValue((Integer) e.getNewValue());
                }
                if (progressBar.getValue() >= progressBar.getMaximum()) {
                    progressBar.setValue(0);
                    browse.setEnabled(true);
                    browse.setText("Browse...");
                }
            });
            current.execute();

        } catch (Exception e) {
            textArea.append(e.getLocalizedMessage());
        }
    }

}
