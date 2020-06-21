import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.File;


/**
 * Implements the export to CSV dialog
 */
public class ExportDialog extends JDialog
{
    /////////////////////////////////////////////////////////////////
    // members

    // flag that indicates if the operation is to be performed
    private boolean m_export;

    // the file to export to
    private File m_exportFile;

    // ui elements
    private JTextField m_filenameField;
    private JButton m_browseButton;
    private JButton m_okButton, m_cancelButton;

    /**
     * Default ctor
     * @param parent
     * @param title
     */
    public ExportDialog(Frame parent, String title)
    {
        super(parent, title);
        initUI();
    }


    /**
     * Initialize UI elements
     */
    private void initUI()
    {
        setResizable(false);

        // create main content pane with vertically layed controls
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

        // create box for file path
        Box filenameBox = Box.createHorizontalBox();
        m_filenameField = new JTextField();
        m_filenameField.setPreferredSize(new Dimension(200, 20));
        m_filenameField.setMaximumSize(new Dimension(200, 20));
        m_browseButton = new JButton("Browse...");
        m_browseButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // browse for a file
                
            }
        });
        filenameBox.add(Box.createHorizontalStrut(4));
        filenameBox.add(m_filenameField);
        filenameBox.add(Box.createHorizontalStrut(10));
        filenameBox.add(m_browseButton);
        filenameBox.add(Box.createHorizontalStrut(4));
        Border border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
        filenameBox.setBorder(BorderFactory.createTitledBorder(border, "Export to file"));

        // create box for ok and cancel buttons
        Box buttonBox = Box.createHorizontalBox();
        m_okButton = new JButton("Ok");
        m_okButton.setMnemonic('O');
        m_okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // if the selected file is writable, ok to export
                if (m_exportFile != null && m_exportFile.canWrite())
                {
                    m_export = true;
                }

                // hide us
                setVisible(false);
            }
        });
        m_cancelButton = new JButton("Cancel");
        m_cancelButton.setMnemonic('C');
        m_cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // hide us
                setVisible(false);
                reset();
            }
        });
        m_okButton.setPreferredSize(m_cancelButton.getPreferredSize());
        buttonBox.add(Box.createHorizontalGlue());
        buttonBox.add(m_okButton);
        buttonBox.add(Box.createHorizontalStrut(5));
        buttonBox.add(m_cancelButton);
        buttonBox.add(Box.createHorizontalStrut(5));

        // add boxes to the content pane
        contentPane.add(Box.createVerticalStrut(4));
        contentPane.add(filenameBox);
        contentPane.add(Box.createVerticalStrut(10));
        contentPane.add(buttonBox);
        contentPane.add(Box.createVerticalStrut(4));
        setContentPane(contentPane);
    }


    /**
     * show the dialog
     */
    public void setVisible(boolean show)
    {
        if (show)
        {
            setLocationRelativeTo(getParent());
            pack();
        }
        super.setVisible(show);
    }


    /**
     * Reset the state of this dialog
     */
    public void reset()
    {
        m_export = false;
        m_filenameField.setText("");
        m_exportFile = null;
    }


    /**
     * returns true if we are to export the spline
     * @return
     */
    public boolean shouldExport()
    {
        return m_export;
    }


}