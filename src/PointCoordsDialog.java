import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;


/**
 * A dialog to specifically set the coords for a point
 */
public class PointCoordsDialog extends JDialog
{
    /////////////////////////////////////////////////////////////////
    // members

    // point coords
    float m_px, m_py;

    // ui elements
    private JLabel m_xLabel, m_yLabel;
    private JTextField m_xField, m_yField;
    private JButton m_okButton, m_cancelButton;


    /**
     * default ctor
     * @param owner
     * @param title
     */
    public PointCoordsDialog(Frame owner, String title)
    {
        // this dialog is modal
        super(owner, title, true);
        initUI();
    }


    /**
     * Initialize the UI of this dialog
     */
    private void initUI()
    {
        setResizable(false);

        // create main content pane with vertically layed controls
        JPanel contentPane = new JPanel();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.Y_AXIS));

        // create box for x coord
        Box xBox = Box.createHorizontalBox();
        m_xLabel = new JLabel("Point X coord:");
        xBox.add(Box.createHorizontalStrut(4));
        xBox.add(m_xLabel);
        xBox.add(Box.createHorizontalGlue());
        m_xField = new JTextField();
        m_xField.setPreferredSize(new Dimension(50, 20));
        m_xField.setMaximumSize(new Dimension(50, 20));
        xBox.add(m_xField);
        xBox.add(Box.createHorizontalStrut(4));
        xBox.setPreferredSize(new Dimension(150, 24));

        // create box for y coord
        Box yBox = Box.createHorizontalBox();
        m_yLabel = new JLabel("Point Y coord:");
        yBox.add(Box.createHorizontalStrut(4));
        yBox.add(m_yLabel);
        yBox.add(Box.createHorizontalGlue());
        m_yField = new JTextField();
        m_yField.setPreferredSize(new Dimension(50, 20));
        m_yField.setMaximumSize(new Dimension(50, 20));
        yBox.add(m_yField);
        yBox.add(Box.createHorizontalStrut(4));
        yBox.setPreferredSize(new Dimension(150, 24));

        // merge the two fields to one box
        Box coordsPanel = Box.createVerticalBox();
        coordsPanel.add(xBox);
        coordsPanel.add(Box.createVerticalStrut(4));
        coordsPanel.add(yBox);
        Border border = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
        coordsPanel.setBorder(BorderFactory.createTitledBorder(border, "Point coordinates"));

        // create buttons
        Box buttonBox = Box.createHorizontalBox();
        m_okButton = new JButton("Ok");
        m_okButton.setMnemonic('O');
        m_okButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // parse fields
                try
                {
                    float x = Float.parseFloat(m_xField.getText());
                    float y = Float.parseFloat(m_yField.getText());

                    // if ok, set and hide us
                    m_px = x;
                    m_py = y;
                    setVisible(false);
                }
                catch(NumberFormatException nfe)
                {
                    // invalid field
                    JOptionPane.showConfirmDialog(PointCoordsDialog.this, "Invalid location entered", "Error", JOptionPane.CANCEL_OPTION);
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
        buttonBox.add(Box.createHorizontalGlue());
        buttonBox.add(m_okButton);
        buttonBox.add(Box.createHorizontalStrut(5));
        buttonBox.add(m_cancelButton);
        buttonBox.add(Box.createHorizontalStrut(5));

        // add all boxes to the content pane
        contentPane.add(Box.createVerticalStrut(4));
        contentPane.add(coordsPanel);
        contentPane.add(Box.createVerticalStrut(10));
        contentPane.add(buttonBox);
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
     * Sets current coords for the point
     * @param x
     * @param y
     */
    public void setPointCoords(float x, float y)
    {
        m_px = x;
        m_xField.setText(Float.toString(x));
        m_py = y;
        m_yField.setText(Float.toString(y));
    }


    /**
     * gets the X coord of the point
     * @return
     */
    public float getPointX()
    {
        return m_px;
    }


    /**
     * gets the Y coord of the point
     * @return
     */
    public float getPointY()
    {
        return m_py;
    }

}