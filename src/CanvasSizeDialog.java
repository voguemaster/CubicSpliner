import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;


/**
 * The canvas size dialog
 */
public class CanvasSizeDialog extends JDialog
{
    ///////////////////////////////////////////////////////////
    // members
    public static final String WIDTH_STR = "Width: ";
    public static final String HEIGHT_STR = "Height: ";

    // current width, height
    private int m_width, m_height;

    // labels for width and height
    private JLabel m_widthLabel, m_heightLabel;
    private JTextField m_widthField, m_heightField;
    private JButton m_okButton, m_cancelButton;

    /**
     * ctor
     * @param owner
     * @param title
     */
    public CanvasSizeDialog(Frame owner, String title)
    {
        // this dialog is modal
        super(owner, title, true);
        initUI();
    }


    /**
     * Initialize all UI components
     */
    private void initUI()
    {
        setResizable(false);

        // create main content pane with vertically layed controls
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

        // calculate width for label elements
        JLabel dummyLabel = new JLabel("MMMMMMMMMM");
        Dimension labelMaxDim = dummyLabel.getPreferredSize();

        // create label for current width
        Box widthBox = Box.createHorizontalBox();
        m_widthLabel = new JLabel(WIDTH_STR);
        widthBox.add(Box.createHorizontalStrut(4));
        widthBox.add(m_widthLabel);
        widthBox.add(Box.createHorizontalGlue());
        widthBox.setPreferredSize(new Dimension(100, 20));

        // create label for current height
        Box heightBox = Box.createHorizontalBox();
        m_heightLabel = new JLabel(HEIGHT_STR);
        heightBox.add(Box.createHorizontalStrut(4));
        heightBox.add(m_heightLabel);
        heightBox.add(Box.createHorizontalGlue());
        heightBox.setPreferredSize(new Dimension(100, 20));

        // merge labels with a container
        Box labelsPanel = Box.createVerticalBox();
        labelsPanel.add(widthBox);
        labelsPanel.add(Box.createVerticalStrut(4));
        labelsPanel.add(heightBox);
        Border border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
        labelsPanel.setBorder(BorderFactory.createTitledBorder(border, "Original dimensions"));

        // create text field for new width
        widthBox = Box.createHorizontalBox();
        m_widthField = new JTextField();
        m_widthField.setPreferredSize(new Dimension(50, 20));
        m_widthField.setMaximumSize(new Dimension(50, 20));
        widthBox.add(Box.createHorizontalStrut(4));
        JLabel wlabel = new JLabel(WIDTH_STR);
        wlabel.setPreferredSize(labelMaxDim);
        widthBox.add(wlabel);
        widthBox.add(m_widthField);
        widthBox.add(Box.createHorizontalGlue());
        widthBox.add(Box.createHorizontalStrut(4));
        widthBox.setPreferredSize(new Dimension(150, 24));

        // create text field for new height
        heightBox = Box.createHorizontalBox();
        m_heightField = new JTextField();
        m_heightField.setPreferredSize(new Dimension(50, 20));
        m_heightField.setMaximumSize(new Dimension(50, 20));
        heightBox.add(Box.createHorizontalStrut(4));
        JLabel hlabel = new JLabel(HEIGHT_STR);
        hlabel.setPreferredSize(labelMaxDim);
        heightBox.add(hlabel);
        heightBox.add(m_heightField);
        heightBox.add(Box.createHorizontalStrut(4));
        heightBox.add(Box.createHorizontalGlue());
        heightBox.setPreferredSize(new Dimension(150, 24));

        // merge fields with a container
        Box fieldsBox = Box.createVerticalBox();
        fieldsBox.add(widthBox);
        fieldsBox.add(Box.createVerticalStrut(4));
        fieldsBox.add(heightBox);
        border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
        fieldsBox.setBorder(BorderFactory.createTitledBorder(border, "New dimensions"));

        // create buttons
        Box buttonsBox = Box.createHorizontalBox();
        m_okButton = new JButton("Ok");
        m_okButton.setMnemonic('O');
        m_okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // parse fields
                try
                {
                    int w = Integer.parseInt(m_widthField.getText());
                    int h = Integer.parseInt(m_heightField.getText());

                    // if ok, set and hide us
                    m_width = w;
                    m_height = h;
                    setVisible(false);
                } catch(NumberFormatException nfe)
                {
                    // invalid field
                    JOptionPane.showConfirmDialog(CanvasSizeDialog.this, "Invalid size entered", "Error", JOptionPane.CANCEL_OPTION);
                }
            }
        });
        m_cancelButton = new JButton("Cancel");
        m_cancelButton.setMnemonic('C');
        m_cancelButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // hide us
                setVisible(false);
            }
        });
        m_okButton.setPreferredSize(m_cancelButton.getPreferredSize());
        buttonsBox.add(Box.createHorizontalGlue());
        buttonsBox.add(m_okButton);
        buttonsBox.add(Box.createHorizontalStrut(5));
        buttonsBox.add(m_cancelButton);
        buttonsBox.add(Box.createHorizontalStrut(5));

        // add all boxes
        contentPane.add(Box.createVerticalStrut(4));
        contentPane.add(labelsPanel);
        contentPane.add(Box.createVerticalStrut(5));
        contentPane.add(fieldsBox);
        contentPane.add(Box.createVerticalStrut(10));
        contentPane.add(buttonsBox);
        contentPane.add(Box.createVerticalStrut(5));
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
     * update with the current canvas size
     * @param width
     * @param height
     */
    public void setCanvasSize(int width, int height)
    {
        m_width = width;
        m_widthLabel.setText(WIDTH_STR+width);
        m_widthField.setText(Integer.toString(width));
        m_height = height;
        m_heightLabel.setText(HEIGHT_STR+height);
        m_heightField.setText(Integer.toString(height));
    }


    /**
     * returns canvas width
     * @return width
     */
    public int getCanvasWidth()
    {
        return m_width;
    }


    /**
     * returns canvas height
     * @return height
     */
    public int getCanvasHeight()
    {
        return m_height;
    }

}