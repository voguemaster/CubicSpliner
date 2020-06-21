/**
 * Cusp - Cubic Spline editor
 * Copyright (c) 2008, Eli Kara
 *
 * This software is provided 'as-is', without any express or implied
 * warranty.  In no event will the authors be held liable for any damages
 * arising from the use of this software.
 *
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 *
 * 1. The origin of this software must not be misrepresented; you must not
 *    claim that you wrote the original software. If you use this software
 *    in a product, an acknowledgment in the product documentation would be
 *    appreciated but is not required.
 * 2. Altered source versions must be plainly marked as such, and must not be
 *    misrepresented as being the original software.
 * 3. This notice may not be removed or altered from any source distribution.
 *
 */

// This tool is used to create 2D parametric curves. It supports creation and editing of
// bezier curves in freeform mode and by cubic spline interpolation (smooth splines).
// The tool is used to export either control points or the entire path as a series of points
// that are sampled from the curve, in order to be used by a game engine to control an entity's
// trajectory.
//
// Version: 0.9 Beta1
//
//////////////////////////////////////////////////////////////////////////////
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.event.*;
import java.io.*;
import java.util.Calendar;
import java.text.DateFormat;


/**
 * Cusp main class. Implements the frame and major operations
 */
public class CubicSpliner implements MouseListener, MouseMotionListener, KeyListener {

    ///////////////////////////////////////////////////////////////////
    // constants

    // default width and height of the canvas
    public static final int DEFAULT_WIDTH = 480;
    public static final int DEFAULT_HEIGHT = 640;

    // default distance from normal point to control point
    public static final float DEFAULT_CONTROL_POINT_DISTANCE = 50.0f;

    // step size to use when drawing curves
    public static final float DEFAULT_SEGMENT_STEPSIZE = 0.02f;

    // maximum number of spline nodes (data points). Each one has 2 control points for bezier representation
    public static final int MAX_POINTS = 100;

    // top limit of running iterations for the jacobi solver and stopping epsilon
    public static final int MAX_JACOBI_ITERATIONS = 50;
    public static final float JACOBI_EPSILON = 0.01f;

    // displayed in the status bar when nothing happens
    public static final String CUSP = "Cusp";
    public static final String READY = "Canvas ready";
    public static final String MOVING_SPLINE = "Moving Entire Spline";

    // UI stuff
    private JFrame mainFrame;
    private Dimension m_canvasSize;
    private JLabel statusLabel;
    private DrawPanel drawPanel;
    private JMenuItem setSelectedPointCoordsItem;
    private JMenuItem saveSplineItem;
    private JMenuItem exportSplineItem;
    private CanvasSizeDialog m_canvasSizeDialog;
    private PointCoordsDialog m_pointCoordsDialog;
    private ExportDialog m_exportDialog;

    // spline stuff

	/// a flag indicating if the spline is in free form mode (able to move control points) or cubic
	/// spline interpolation
    private boolean m_bFreeform;

    /// number of node points (main points) that are currently allocated
    private int m_pointCount;

    /// contains allocated node points
    private Vector2D[] m_points;

    /// contains allocated control point structs (each CP struct correlates to one node point)
    private ControlPoints[] m_controlPoints;

    /// used to backup the spline node points in a scale operation
    private Vector2D[] m_origPoints;

    /// used to backup the control points in a scale operation
    private ControlPoints[] m_origControlPoints;

    /// index of selected point, if one is selected
    private int m_selectedPoint;

    /// index of selected control point if one is selected (only possible in freeform mode)
    private int m_selectedControlPoint;

    /// used to cache the last mouse pos for move/scale operations
    private Vector2D m_lastMousePos;

    /// dirty bit
    private boolean m_dirty;

    // cubic spline interpolation using B-splines members

    /// coefficients matrix in the 141 form, used with the jacobi solver to obtain control points
    float[][] m_BMatrix;

    /// The solution vector in each solving of a system of equations
    Vector2D[] m_SVector;

    /// an array used to store the final result of the jacobi solver and contains the
    /// coords for the Bi points
    Vector2D[] m_BSolutionsVector;

    /// constants array and solution array (to receive the solution) for each invocation of the solver
    /// (we solve for x and then y, naturally)
    float[] m_jacobiSolutions, m_jacobiConstants;

    // rendering
    private Color m_navyColor;
    private Ellipse2D.Float drawablePoint;
    private Ellipse2D.Float selectedPoint;
    private Dimension drawArea;

    // IO stuff
    private File m_splineFile;


    /**
     * main
     * @param args Arguments
     */
    public static void main(String[] args)
    {
        CubicSpliner tool = new CubicSpliner();
        tool.init();
    }


    /**
     * Init the app
     */
    public void init()
    {
        // init UI stuff
        initFrame();
        initMenus();
        initStatusBar();

        // create objects necessary for rendering
        drawablePoint = new Ellipse2D.Float(0,0,6,6);
        selectedPoint = new Ellipse2D.Float(0,0,8,8);
        drawArea = new Dimension();
        m_navyColor = new Color(0, 0, 128);

        // create storage arrays
        m_bFreeform = false;    // starting with cubic spline interpolation
        m_points = new Vector2D[MAX_POINTS];
        m_origPoints = new Vector2D[MAX_POINTS];
        m_controlPoints = new ControlPoints[MAX_POINTS];
        m_origControlPoints = new ControlPoints[MAX_POINTS];
        m_lastMousePos = new Vector2D();

        // reset spline
        reset();

        // show the frame
        mainFrame.pack();
        mainFrame.setLocationRelativeTo(null);
        mainFrame.setVisible(true);
    }


    /**
     * Initializes the main frame
     */
    private void initFrame()
    {
        // set look and feel
        try {
            String lookAndFeel = UIManager.getSystemLookAndFeelClassName();
            UIManager.setLookAndFeel(lookAndFeel);
        } catch(Exception e) {
            e.printStackTrace();
        }

        // create the frame
        mainFrame = new JFrame(CUSP);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.addComponentListener(new ComponentAdapter()
        {
            public void componentResized(ComponentEvent e)
            {
                // obtain client size and set status label
                Insets frameInsets = mainFrame.getInsets();
                m_canvasSize = mainFrame.getSize();
                m_canvasSize.width -= frameInsets.left+frameInsets.right;
                m_canvasSize.height -= frameInsets.top+frameInsets.bottom;
                StringBuffer sbuf = new StringBuffer(40);
                sbuf.append(READY);
                sbuf.append("  (");
                sbuf.append(m_canvasSize.width);
                sbuf.append(',');
                sbuf.append(m_canvasSize.height);
                sbuf.append(')');
                statusLabel.setText(sbuf.toString());
            }
        });
        JPanel contentPane = new JPanel(new BorderLayout());

        // create drawing panel
        drawPanel = new DrawPanel();
        Dimension d = new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        drawPanel.setMinimumSize(d);
        drawPanel.setPreferredSize(d);
        drawPanel.addMouseListener(this);
        drawPanel.addMouseMotionListener(this);
        drawPanel.addKeyListener(this);
        contentPane.add(drawPanel, BorderLayout.CENTER);

        // set content
        mainFrame.setContentPane(contentPane);
    }


    /**
     * Initialize menu UI
     */
    private void initMenus()
    {
        // create menus
        JMenuBar menuBar = new JMenuBar();

        // create file menu
        JMenu fileMenu = new JMenu("File");
        fileMenu.setMnemonic('F');

        // new spline
        JMenuItem newSplineItem = new JMenuItem("New Spline");
        newSplineItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // if there are points and the spline is not saved, ask for confirmation
                int rval = JOptionPane.YES_OPTION;
                if (m_pointCount > 0 && m_dirty)
                {
                    rval = JOptionPane.showConfirmDialog(mainFrame, "Your previous spline will be lost. Continue ?", "Confirm losing previous data",
                    JOptionPane.YES_NO_OPTION);
                }
                if (rval == JOptionPane.YES_OPTION)
                {
                    // clear out data
                    reset();
                    drawPanel.repaint();
                }
            }
        });

        // load spline
        JMenuItem loadSplineItem = new JMenuItem("Load");
        loadSplineItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // if the current spline hasn't been saved, ask for confirmation
                boolean load = false;
                if (m_pointCount > 0)
                {
                    int rval = JOptionPane.showConfirmDialog(mainFrame, "Your previous spline will be lost. Continue ?", "Confirm losing previous data",
                            JOptionPane.YES_NO_OPTION);
                    if (rval == JOptionPane.YES_OPTION)
                    {
                        load = true;
                    }
                }
                else
                {
                    // no spline, just load
                    load = true;
                }

                if (load)
                {
                    // load a spline from file
                    String cwd = System.getProperty("user.dir");
                    JFileChooser jfc = new JFileChooser(cwd);
                    jfc.setDialogTitle("Load Spline");
                    jfc.addChoosableFileFilter(new SplineFileFilter(".csp", "Cusp Spline (*.csp)"));
                    int rval = jfc.showDialog(mainFrame, "Load");
                    if (rval == JFileChooser.APPROVE_OPTION)
                    {
                        // get file and load it
                        File selectedFile = jfc.getSelectedFile();
                        if (selectedFile.canRead())
                        {
                            loadSpline(selectedFile);
                            saveSplineItem.setEnabled(true);
                            exportSplineItem.setEnabled(true);

                            // repaint
                            drawPanel.repaint();
                        }
                    }
                }
            }   // end actionPerformed
        });

        // save spline
        saveSplineItem = new JMenuItem("Save");
        saveSplineItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // save the spline
                String cwd = System.getProperty("user.dir");
                JFileChooser jfc = new JFileChooser(cwd);
                jfc.setDialogTitle("Save Spline");
                jfc.addChoosableFileFilter(new SplineFileFilter(".csp", "Cusp Spline (*.csp)"));
                if (m_splineFile != null && m_splineFile.canWrite())
                    jfc.setSelectedFile(m_splineFile);
                int rval = jfc.showSaveDialog(mainFrame);
                if (rval == JFileChooser.APPROVE_OPTION)
                {
                    // normalize filename
                    File splineFile = normalizeFile(jfc.getSelectedFile(), "csp");
                    saveSpline(splineFile);
                }
            }
        });

        // export spline
        exportSplineItem = new JMenuItem("Export CSV");
        exportSplineItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // export the spline to a series of points in CSV format
                if (m_exportDialog == null)
                    m_exportDialog = new ExportDialog(mainFrame, "Export Spline to CSV");

                // reset state and launch dialog
                m_exportDialog.reset();
                m_exportDialog.setVisible(true);
                if (m_exportDialog.shouldExport())
                {
                    exportSpline(m_exportDialog.getExportFile(), m_exportDialog.getSubdivisions());
                }
            }
        });

        // exit cusp
        JMenuItem exitItem = new JMenuItem("Exit");
        exitItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // exit
                int rval;
                if (m_dirty)
                {
                    // spline hasn't been saved, ask user what to do
                    rval = JOptionPane.showConfirmDialog(mainFrame, "Any unsaved changes will be lost. Are you sure you want to exit ?",
                            "Spline unsaved", JOptionPane.YES_NO_OPTION);
                }
                else
                {
                    // ask for normal confirmation to exit
                    rval = JOptionPane.showConfirmDialog(mainFrame, "Are you sure you want to exit Cusp ?",
                            "Exit confirmation", JOptionPane.YES_NO_OPTION);
                }

                if (rval == JOptionPane.YES_OPTION)
                {
                    mainFrame.setVisible(false);
                    System.exit(0);
                }
            }
        });

        fileMenu.add(newSplineItem);
        fileMenu.addSeparator();
        fileMenu.add(loadSplineItem);
        fileMenu.add(saveSplineItem);
        fileMenu.add(exportSplineItem);
        fileMenu.addSeparator();
        fileMenu.add(exitItem);

        // create edit menu
        JMenu editMenu = new JMenu("Edit");
        editMenu.setMnemonic('E');
        JMenuItem canvasSize = new JMenuItem("Canvas Size");
        canvasSize.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // set canvas size
                if (m_canvasSizeDialog == null)
                    m_canvasSizeDialog = new CanvasSizeDialog(mainFrame, "Canvas Size");

                // set params and launch dialog
                m_canvasSizeDialog.setCanvasSize(m_canvasSize.width, m_canvasSize.height);
                m_canvasSizeDialog.setVisible(true);

                // modal dialog is over, lets set new size
                scaleCanvas(m_canvasSizeDialog.getCanvasWidth(), m_canvasSizeDialog.getCanvasHeight());
            }
        });
        editMenu.add(canvasSize);

        // create spline menu
        JMenu splineMenu = new JMenu("Spline");
        splineMenu.setMnemonic('S');

        // set selected point coords
        setSelectedPointCoordsItem = new JMenuItem("Set point coords");
        setSelectedPointCoordsItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // only if one is selected and its a node point (not control point)
                if (m_selectedPoint >= 0 && m_selectedControlPoint < 0)
                {
                    // attempt to set point coords using the dialog
                    if (m_pointCoordsDialog == null)
                        m_pointCoordsDialog = new PointCoordsDialog(mainFrame, "Point coordinates");

                    // set params and launch dialog
                    Vector2D p = m_points[m_selectedPoint];
                    m_pointCoordsDialog.setPointCoords(p.m_x, p.m_y);
                    m_pointCoordsDialog.setVisible(true);

                    // modal dialog is over, lets set new coords
                    setPointCoords(m_pointCoordsDialog.getPointX(), m_pointCoordsDialog.getPointY());

                    // update display
                    drawPanel.repaint();
                }
            }
        });

        // horizontal flip
        JMenuItem flipHorizontal = new JMenuItem("Flip Horizontally");
        flipHorizontal.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // perform horizontal flip
                flipHorizontal();

                // refresh display
                drawPanel.repaint();
            }
        });

        // vertical flip
        JMenuItem flipVertical = new JMenuItem("Flip Vertically");
        flipVertical.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // perform vertical flip
                flipVertical();

                // refresh display
                drawPanel.repaint();
            }
        });

        // free form mode
        JMenuItem freeform = new JRadioButtonMenuItem("Freeform");
        freeform.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // free form selected, change flag only
                m_bFreeform = true;
                drawPanel.repaint();
            }
        });

        // cubic spline interpolation mode
        JMenuItem cubicSplineForm = new JRadioButtonMenuItem("Cubic Spline Interpolation");
        cubicSplineForm.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                // cubic spline mode, update flag
                m_bFreeform = false;

                // if a point is selected make sure its not a control point
                if (m_selectedPoint >= 0 && m_selectedControlPoint >= 0)
                    m_selectedControlPoint = -1;

                // update control points and repaint
                updateControlPoints(true);
                drawPanel.repaint();
            }
        });

        // add buttons to menu
        ButtonGroup group = new ButtonGroup();
        group.add(freeform);
        group.add(cubicSplineForm);
        cubicSplineForm.setSelected(true);  // default mode is cubic spline interpolation
        splineMenu.add(setSelectedPointCoordsItem);
        splineMenu.addSeparator();
        splineMenu.add(flipHorizontal);
        splineMenu.add(flipVertical);
        splineMenu.addSeparator();
        splineMenu.add(freeform);
        splineMenu.add(cubicSplineForm);

        // set menu bar
        menuBar.add(fileMenu);
        menuBar.add(editMenu);
        menuBar.add(splineMenu);
        mainFrame.setJMenuBar(menuBar);
    }


    /**
     * Initializes the status bar
     */
    private void initStatusBar()
    {
        // create status bar
        Box statusBar = Box.createHorizontalBox();
        statusBar.add(Box.createHorizontalStrut(5));
        statusLabel = new JLabel(READY);
        statusBar.add(statusLabel);
        statusBar.add(Box.createHorizontalGlue());
        statusBar.setBorder(BorderFactory.createLoweredBevelBorder());
        mainFrame.getContentPane().add(statusBar, BorderLayout.SOUTH);
    }


    /**
     * Cleans out any defined points
     */
    private void reset()
    {
        // clean out everything
        for(int i=0 ; i < m_pointCount ; i++)
        {
            m_points[i] = null;
            m_controlPoints[i] = null;
            m_origPoints[i] = null;
            m_origControlPoints[i] = null;
        }
        m_pointCount = 0;
        m_selectedPoint = -1;   // none
        m_selectedControlPoint = -1;    // none
        m_dirty = false;    // nothing to be dirty about

        // reset UI stuff
        setSelectedPointCoordsItem.setEnabled(false);
        saveSplineItem.setEnabled(false);
        exportSplineItem.setEnabled(false);
    }


    /**
     * Called when mouse is clicked on the draw panel
     * @param e
     */
    public void mouseClicked(MouseEvent e)
    {
    }

    public void mouseEntered(MouseEvent e)
    {
    }

    public void mouseExited(MouseEvent e)
    {
    }

    public void mousePressed(MouseEvent e)
    {
        // clear selection and menu item
        setSelectedPointCoordsItem.setEnabled(false);
        m_selectedPoint = m_selectedControlPoint = -1;

        // get mouse coords and based on the button pressed decide what to do
        int x = e.getX();
        int y = e.getY();
        if (e.getButton() == MouseEvent.BUTTON1 && !e.isShiftDown() && !e.isControlDown())
        {
            // normal click on the canvas selects a point or adds a new point in an empty area
            boolean added = selectOrAddPoint(x, y);
            if (added)
            {
                saveSplineItem.setEnabled(true);
                exportSplineItem.setEnabled(true);
            }

            // enable menu item that enables us to set coords for a point (we always have a selected point here
            // we just need to make sure its not a control point)
            if (m_selectedControlPoint < 0)
                setSelectedPointCoordsItem.setEnabled(true);
        }
        else if (e.getButton() == MouseEvent.BUTTON1 && e.isControlDown())
        {
            // preparing for spline scaling, replace whatever is in the arrays with the current spline
            backupSpline();
        }
        else if (e.getButton() == MouseEvent.BUTTON3)
        {
            // right-click, clear selection
            m_selectedPoint = -1;
            m_selectedControlPoint = -1;
        }

        // update last mouse pos
        m_lastMousePos.m_x = x;
        m_lastMousePos.m_y = y;

        // refresh display
        drawPanel.repaint();
    }

    public void mouseReleased(MouseEvent e)
    {
        StringBuffer sbuf = new StringBuffer(40);
        sbuf.append(READY);
        sbuf.append("  (");
        sbuf.append(drawArea.width);
        sbuf.append(',');
        sbuf.append(drawArea.height);
        sbuf.append(')');
        statusLabel.setText(sbuf.toString());
    }


    /**
     * dragged the mouse
     * @param e
     */
    public void mouseDragged(MouseEvent e)
    {
        if (e.isShiftDown())
        {
            // when shift is down, all points are moved (free form or not)
            moveSpline(e.getX(), e.getY());

            // update status bar
            statusLabel.setText(MOVING_SPLINE);

            // update display
            drawPanel.repaint();
        }
        else if (e.isControlDown())
        {
            // when control is down, scale the spline relative to its upper left corner
            scaleSpline(e.getX(), e.getY());
            drawPanel.repaint();
        }
        else
        {
            // if a point is selected, move it. only in free form mode
            // a control point can be selected in mousePressed
            int x = e.getX();
            int y = e.getY();
            if (moveSplinePoint(x, y))
            {
                // update status bar
                StringBuffer sbuf = new StringBuffer(40);
                sbuf.append(x);
                sbuf.append(',');
                sbuf.append(y);
                statusLabel.setText(sbuf.toString());

                // update display
                drawPanel.repaint();
            }
        }
    }

    public void mouseMoved(MouseEvent e)
    {
    }


    public void keyTyped(KeyEvent e)
    {
    }


    /**
     * React to key presses if they interest us
     * @param e
     */
    public void keyPressed(KeyEvent e)
    {
        if (e.getKeyCode() == KeyEvent.VK_DELETE)
        {
            // attempt to delete currently selected point, but only if
            // the node point is selected (can't delete control points)
            if (m_selectedPoint >= 0 && m_selectedControlPoint < 0)
            {
                deletePoint(m_selectedPoint);
                m_selectedPoint = -1;

                // update menu item so we cant set any point's coords
                setSelectedPointCoordsItem.setEnabled(false);

                // update display
                drawPanel.repaint();
            }

            // if no points left, disable save and export
            if (m_pointCount == 0)
            {
                saveSplineItem.setEnabled(false);
                exportSplineItem.setEnabled(false);
            }
        }
    }

    public void keyReleased(KeyEvent e)
    {
    }


    /**
     * Add a point to the curve
     * @param pt
     */
    private void addPoint(Vector2D pt)
    {
        // set new point and add a point to the backup array if needed
        m_points[m_pointCount] = pt;
        if (m_origPoints[m_pointCount] == null)
        {
            m_origPoints[m_pointCount] = new Vector2D(pt);
        }

        // create control points for the new point
        if (m_bFreeform)
        {
            // in free form mode, add control points based on direction from prev point
            addControlPoints(m_pointCount);
        }
        else
        {
            // in cubic interpolation mode the control points coords will be overriden
            m_controlPoints[m_pointCount] = new ControlPoints();
            m_controlPoints[m_pointCount].m_controlPoint[0] = new Vector2D(pt);
            m_controlPoints[m_pointCount].m_controlPoint[1] = new Vector2D(pt);
        }

        // add empty control points to the backup array
        if (m_origControlPoints[m_pointCount] == null)
        {
            m_origControlPoints[m_pointCount] = new ControlPoints();
        }

        // added point means one more segment as well. raise point count
        m_pointCount++;
        if (!m_bFreeform)
        {
            // update control points
            updateControlPoints(true);
        }

        m_dirty = true;
    }


    /**
     * Create control points for the point with specified index
     * @param pointID
     */
    private void addControlPoints(int pointID)
    {
        ControlPoints cp = new ControlPoints();
        Vector2D p = m_points[pointID];
        if (m_pointCount == 0)
        {
            // if this is the first point created, add control points near it
            Vector2D cp1 = cp.m_controlPoint[0];
            Vector2D cp2 = cp.m_controlPoint[1];
            cp1.m_x = p.m_x + DEFAULT_CONTROL_POINT_DISTANCE;
            cp1.m_y = p.m_y;
            cp2.m_x = p.m_x - DEFAULT_CONTROL_POINT_DISTANCE;
            cp2.m_y = p.m_y;
        }
        else
        {
            // find the vector from the previous point to this one and use it to add points
            // along that same vector
            Vector2D diff = new Vector2D(p);
            diff.substract(m_points[pointID-1]);
            diff.normalize();
            diff.multiply(DEFAULT_CONTROL_POINT_DISTANCE);

            // set first control point
            Vector2D cp1 = cp.m_controlPoint[0];
            cp1.m_x = p.m_x;
            cp1.m_y = p.m_y;
            cp1.add(diff);

            // set second control point
            diff.normalize();
            diff.multiply(-DEFAULT_CONTROL_POINT_DISTANCE);
            Vector2D cp2 = cp.m_controlPoint[1];
            cp2.m_x = p.m_x;
            cp2.m_y = p.m_y;
            cp2.add(diff);
        }

        // set new control points in array
        m_controlPoints[pointID] = cp;
    }


    /**
     * Delete a point at specified index
     * @param pindex
     */
    private void deletePoint(int pindex)
    {
        // shuffle points from the wanted position and clear the last point
        int i;
        for(i=pindex ; i < m_pointCount-1 ; i++)
        {
            m_points[i] = m_points[i+1];
            m_controlPoints[i] = m_controlPoints[i+1];
        }
        m_points[i] = null;
        m_controlPoints[i] = null;

        // one less point
        m_pointCount--;
        if (!m_bFreeform)
        {
            // update control points
            updateControlPoints(true);
        }

        m_dirty = true; // spline is dirty
    }


    /**
     * Flip the spline horizontally by mirroring it using a vertical line at the center
     * of the view
     */
    private void flipHorizontal()
    {
        // find center of viewport
        int hcenter = m_canvasSize.width/2;

        // iterate main points and mirror them
        int dx;
        for(int i=0 ; i < m_pointCount ; i++)
        {
            Vector2D p = m_points[i];
            dx = hcenter-(int)p.m_x;
            p.m_x = hcenter+dx; // mirror

            // if free form, mirror control points as well
            if (m_bFreeform)
            {
                for(int j=0 ; j < 2 ; j++)
                {
                    Vector2D cp = m_controlPoints[i].m_controlPoint[j];
                    dx = hcenter-(int)cp.m_x;
                    cp.m_x = hcenter+dx;    // mirror
                }
            }
        }

        // once all points are mirrored, if we're in cubic spline mode we resolve for control points
        if (!m_bFreeform)
        {
            updateControlPoints(true);
        }

        m_dirty = true;
    }


    /**
     * Flip the spline vertically by mirroring it using a horizontal line at the center of the view
     */
    private void flipVertical()
    {
        // find center of viewport
        int vcenter = m_canvasSize.height/2;

        // iterate main points and mirror them
        int dy;
        for(int i=0 ; i < m_pointCount ; i++)
        {
            Vector2D p = m_points[i];
            dy = vcenter-(int)p.m_y;
            p.m_y = vcenter+dy; // mirror

            // if free form, mirror control points as well
            if (m_bFreeform)
            {
                for(int j=0 ; j < 2 ; j++)
                {
                    Vector2D cp = m_controlPoints[i].m_controlPoint[j];
                    dy = vcenter-(int)cp.m_y;
                    cp.m_y = vcenter+dy;    // mirror
                }
            }
        }

        // once all points are mirrored, if we're in cubic spline mode we resolve for control points
        if (!m_bFreeform)
        {
            updateControlPoints(true);
        }

        m_dirty = true;
    }


    /**
     * Computes the spline's axis aligned bounding box
     * @return Two vectors: top-left corner and bottom-right corner
     */
    private Vector2D[] getSplineAABB()
    {
        Vector2D[] box = null;
        if (m_pointCount > 0)
        {
            box = new Vector2D[2];
            box[0] = new Vector2D(Float.MAX_VALUE, Float.MAX_VALUE);    // min point (top-left)
            box[1] = new Vector2D(Float.MIN_VALUE, Float.MIN_VALUE);    // max point (bottom-right)

            // iterate points and remember min and max positions
            for(int i=0 ; i < m_pointCount ; i++)
            {
                Vector2D p = m_points[i];
                if (p.m_x < box[0].m_x)
                    box[0].m_x = p.m_x;
                if (p.m_y < box[0].m_y)
                    box[0].m_y = p.m_y;
                if (p.m_x > box[1].m_x)
                    box[1].m_x = p.m_x;
                if (p.m_y > box[1].m_y)
                    box[1].m_y = p.m_y;
            }
        }
        return box;
    }


    /**
     * In cubic spline interpolation mode we take the input points (the main points)
     * and use B-splines and a control polygon to calculate the positions of the control
     * points so the spline goes through all main points and the first and second derivatives
     * there are equal for the two segments connected at the point
     *
     * problem:
     * B is the vector of the points making up the control polygon (we want to find it)
     * S is the vector of the main points in the editor
     * We define the control polygon by connecting all points in B and divide each segment
     * to 3 equal parts and these are the control points: (2/3*Bi-1 + 1/3*Bi) and (1/3*Bi-1 + 2/3*Bi)
     * for each of the main points in S.
     * Using these we can express our main points S:
     *
     * S0 = B0, Sn = Bn, Si = 0.5*(1/3 * Bi-1 + 2/3 * Bi) + 0.5*(2/3 * Bi + 1/3 * Bi+1) =
     *      1/6 * Bi-1 + 2/3 * Bi + 1/6 * Bi+1
     * (i = 0,1...n-1)
     *
     * The last equation for Si can be used to give B as a function of S (for all equations except
     * the first and last) by multiplying by 6:
     * Bi-1 + 4Bi + Bi+1 = Si
     *
     * The first and last equations in the matrix are: B0 + 4*B1 + B2 = 6*S1 ==> 4*B1 + B2 = 6*S1 - S0
     *
     * @param bUpdateMatrix True if the number of main points have changed and we need to recreate
     * both the 141 matrix (coef matrix) and the constants vector
     */
    private void updateControlPoints(boolean bUpdateMatrix)
    {
        // sanity
        if (m_pointCount < 3)
            return;

        boolean solve = false;
        if (bUpdateMatrix)
        {
            solve = true;

            // setting up the coefficient matrix
            create141Matrix(m_pointCount);

            // fill the constants vector, made from our S points
            createConstantsVector(m_pointCount);

            // create new solution vector
            createSolutionVector(m_pointCount);
        }
        else
        {
            // equations stay the same except one element in the constant vector
            // that might have changed
            if (m_selectedPoint >= 0)
            {
                solve = true;

                // update the element relevant for the point that moved
                updateSingleConstant(m_selectedPoint);
            }
        }

        // if something changed, solve new equations
        if (solve)
        {
            // set first B point (B0 = S0)
            m_BSolutionsVector[0].m_x = m_points[0].m_x;
            m_BSolutionsVector[0].m_y = m_points[0].m_y;

            // set last B point (Bn = Sn)
            m_BSolutionsVector[m_pointCount-1].m_x = m_points[m_pointCount-1].m_x;
            m_BSolutionsVector[m_pointCount-1].m_y = m_points[m_pointCount-1].m_y;

            // solve using jacobi method for x and update solution vector
            int n = m_pointCount-2;
            for(int i=0 ; i < n ; i++)
            {
                m_jacobiConstants[i] = m_SVector[i].m_x;
                m_jacobiSolutions[i] = 0.0f;
            }
            solveJacobi(m_BMatrix, m_jacobiSolutions, m_jacobiConstants);
            for(int i=0 ; i < n ; i++)
                m_BSolutionsVector[i+1].m_x = m_jacobiSolutions[i];

            // solve using jacobi method for y and update solution vector
            for(int i=0 ; i < n ; i++)
            {
                m_jacobiConstants[i] = m_SVector[i].m_y;
                m_jacobiSolutions[i] = 0.0f;
            }
            solveJacobi(m_BMatrix, m_jacobiSolutions, m_jacobiConstants);
            for(int i=0 ; i < n ; i++)
                m_BSolutionsVector[i+1].m_y = m_jacobiSolutions[i];

            // calculate control points from the B solution vector
            for(int i=1 ; i < m_pointCount ; i++)
            {
                Vector2D b1 = m_BSolutionsVector[i-1];
                Vector2D b2 = m_BSolutionsVector[i];

                // update first control point (it is the first/left point of the i-1 node point)
                // see the drawCurve method
                Vector2D cp = m_controlPoints[i-1].m_controlPoint[0];
                cp.m_x = 2.0f/3.0f * b1.m_x + 1.0f/3.0f * b2.m_x;
                cp.m_y = 2.0f/3.0f * b1.m_y + 1.0f/3.0f * b2.m_y;

                // update second control point (it is the second/right control point of the i node point)
                // see the drawCurve method
                cp = m_controlPoints[i].m_controlPoint[1];
                cp.m_x = 1.0f/3.0f * b1.m_x + 2.0f/3.0f * b2.m_x;
                cp.m_y = 1.0f/3.0f * b1.m_y + 2.0f/3.0f * b2.m_y;
            }
        }
    }


    /**
     * Create the coefficient matrix made up by the equations described above
     * @param n The number of main points we have (length of S)
     */
    private void create141Matrix(int n)
    {
        // create new equation vector for the B points. Since S0 = B0, Sn = Bn we need less equations
        int matrixSize = n-2;
        m_BMatrix = new float[matrixSize][matrixSize];

        // create all rows before the last one
        for(int row=0 ; row < matrixSize ; row++)
        {
            for(int col=0 ; col < matrixSize ; col++)
            {
                if (row == col)
                    m_BMatrix[row][col] = 4.0f;
                else if (col == row-1 || col == row+1)
                    m_BMatrix[row][col] = 1.0f;
            }
        }
    }


    /**
     * Fill the constants vector with our points (except first and last which are special)
     * @param n The number of main points we have
     */
    private void createConstantsVector(int n)
    {
        int vecSize = n-2;
        m_SVector = new Vector2D[vecSize];
        m_jacobiConstants = new float[vecSize];
        m_jacobiSolutions = new float[vecSize];

        // fill first point (6*S1 - S0)
        m_SVector[0] = new Vector2D(m_points[1]);
        m_SVector[0].multiply(6.0f);
        m_SVector[0].substract(m_points[0]);

        // fill the rest of the vector besides the last element
        for(int i=1 ; i < vecSize-1 ; i++)
        {
            m_SVector[i] = new Vector2D(m_points[i+1]);
            m_SVector[i].multiply(6.0f);
        }

        // fill the last element of the vector (6*Sn-1 - Sn)
        m_SVector[vecSize-1] = new Vector2D(m_points[n-2]);
        m_SVector[vecSize-1].multiply(6.0f);
        m_SVector[vecSize-1].substract(m_points[n-1]);
    }


    /**
     * Creates storage for the solution points B
     * @param n
     */
    private void createSolutionVector(int n)
    {
        m_BSolutionsVector = new Vector2D[n];
        for(int i= 0 ; i < n ; i++)
        {
            m_BSolutionsVector[i] = new Vector2D();
        }
    }


    /**
     * Given the index of the point that changed, update the relevant
     * constant in the constants vector
     * @param pointID
     */
    private void updateSingleConstant(int pointID)
    {
        if (pointID == 0 || pointID == 1)
        {
            // the first and second point makes the first constant in the S vector
            m_SVector[0] = new Vector2D(m_points[1]);
            m_SVector[0].multiply(6.0f);
            m_SVector[0].substract(m_points[0]);
        }
        else if (pointID == m_pointCount-1 || pointID == m_pointCount-2)
        {
            // the last and one before last points makes the last constant in the S vector
            int vecSize = m_pointCount-2;
            m_SVector[vecSize-1] = new Vector2D(m_points[m_pointCount-2]);
            m_SVector[vecSize-1].multiply(6.0f);
            m_SVector[vecSize-1].substract(m_points[m_pointCount-1]);
        }
        else
        {
            // every other constant is calculated directly from one point
            // since the S vector uses points 0 and 1 for the first entry, we use pointID-1
            m_SVector[pointID-1] = new Vector2D(m_points[pointID]);
            m_SVector[pointID-1].multiply(6.0f);
        }
    }


    /**
     * Use the Jacobi iterative solver to solve the system Ax = B. Since in our case A is
     * diagonal-dominant, jacobi works well.
     * @param A The 141 matrix
     * @param x A vector to receive the solutions
     * @param b The constants vector
     */
    private void solveJacobi(float[][] A, float[] x, float[] b)
    {
        // initial guess is a vector of 0
        int n = x.length;
        float[] r = new float[n];

        // perform up to max iterations
        for(int iteration=0 ; iteration < MAX_JACOBI_ITERATIONS ; iteration++)
        {
            for(int row=0 ; row < n ; row++)
            {
                float delta = 0.0f;

                // sum up elements of the L diagonal matrix (lower diagonal part of A)
                for(int col=0 ; col < row ; col++)
                {
                    delta += A[row][col] * r[col];
                }

                // sum up elements of the U diagonal matrix (upper diagonal part of A)
                for(int col=row+1 ; col < n ; col++)
                {
                    delta += A[row][col] * r[col];
                }

                // update x (solution vector)
                x[row] = (b[row] - delta) / A[row][row];
            }   // end row

            // check convergence by seeing if the norm of the difference between this iteration
            // result (x vector) to the previous iteration result (r vector) is small enough
            float norm = 0.0f;
            for(int i=0 ; i < n ; i++)
            {
                norm += (x[i]-r[i])*(x[i]-r[i]);
            }
            if (norm < JACOBI_EPSILON)
            {
                // exit gracefully (with updating)
                iteration = MAX_JACOBI_ITERATIONS;
            }

            // update guess vector
            System.arraycopy(x, 0, r, 0, n);
        }   // end iterations
    }


    /**
     * Move the entire spline
     * @param mx Mouse X
     * @param my Mouse Y
     */
    private void moveSpline(int mx, int my)
    {
        int dx = mx - (int)m_lastMousePos.m_x;
        int dy = my - (int)m_lastMousePos.m_y;
        for(int i=0 ; i < m_pointCount ; i++)
        {
            Vector2D p = m_points[i];
            p.m_x += dx;
            p.m_y += dy;
            p = m_controlPoints[i].m_controlPoint[0];
            p.m_x += dx;
            p.m_y += dy;
            p = m_controlPoints[i].m_controlPoint[1];
            p.m_x += dx;
            p.m_y += dy;
        }
        // update last coord
        m_lastMousePos.m_x = mx;
        m_lastMousePos.m_y = my;

        // spline gets dirty
        m_dirty = true;
    }


    /**
     * Moves one point or control point
    **/
    private boolean moveSplinePoint(int mx, int my)
    {
        // if a point is selected, move it. only in free form mode
        // a control point can be selected in mousePressed
        boolean moved = false;
        if (m_selectedPoint >= 0)
        {
            Vector2D p;
            if (m_selectedControlPoint >= 0 && m_selectedControlPoint < 2)
            {
                // move the relevant control point
                p = m_controlPoints[m_selectedPoint].m_controlPoint[m_selectedControlPoint];
                p.m_x = mx;
                p.m_y = my;
            }
            else
            {
                // normal node point moving
                p = m_points[m_selectedPoint];
                int dx = mx - (int)p.m_x;
                int dy = my - (int)p.m_y;
                p.m_x = mx;
                p.m_y = my;

                // move control points by the delta the normal point moved by (free form only)
                if (m_bFreeform)
                {
                    for(int i=0 ; i < 2 ; i++)
                    {
                        p = m_controlPoints[m_selectedPoint].m_controlPoint[i];
                        p.m_x += dx;
                        p.m_y += dy;
                    }
                }
                else
                {
                    // update control points
                    updateControlPoints(false);
                }
            }

            // spline is dirty
            m_dirty = true;
            moved = true;
        }

        // return if moved
        return moved;
    }


    /**
     * Selects an existing point or adds a new point if clicked at an empty canvas area
     * @param mx Mouse X
     * @param my Mouse Y
     */
    private boolean selectOrAddPoint(int mx, int my)
    {
        boolean added = false;

        // search all points and their control points to find which point is selected, if any
        // control points can only be selected in free form mode
        float fx = (float)mx;
        float fy = (float)my;
        for(int i=0 ; i < m_pointCount ; i++)
        {
            // if user clicked near a point, select it
            float dx = fx - m_points[i].m_x;
            float dy = fy - m_points[i].m_y;
            if (dx*dx + dy*dy <= 16.0f)  // select at radius of 4 px
            {
                // select the point
                m_selectedPoint = i;
                break;
            }

            // if the user clicked near a control point, select it
            // only works in free form mode
            if (m_bFreeform)
            {
                ControlPoints cp = m_controlPoints[i];
                for(int j=0 ; j < 2 ; j++)
                {
                    dx = fx - cp.m_controlPoint[j].m_x;
                    dy = fy - cp.m_controlPoint[j].m_y;
                    if (dx*dx + dy*dy <= 16.0f)  // select at radius of 4 px
                    {
                        // select control point j that belongs to node point i
                        m_selectedPoint = i;
                        m_selectedControlPoint = j;
                    }
                }
            }
        }

        // if none selected, add point
        if (m_selectedPoint < 0)
        {
            m_selectedPoint = m_pointCount; // new point is selected
            addPoint(new Vector2D(mx, my));
            added = true;
        }

        return added;
    }


    /**
     * Scales the canvas and perhaps the points
     * @param newWidth
     * @param newHeight
     */
    private void scaleCanvas(int newWidth, int newHeight)
    {
        // set new canvas size
        m_canvasSize.width = newWidth;
        m_canvasSize.height = newHeight;

        // update frame size
        Insets frameInsets = mainFrame.getInsets();
        mainFrame.setSize(m_canvasSize.width+frameInsets.left+frameInsets.right,
            m_canvasSize.height+frameInsets.top+frameInsets.bottom);
    }


    /**
     * Updates a point's coords and its control points by the same delta
     * @param x
     * @param y
     */
    private void setPointCoords(float x, float y)
    {
        // get current point and its coords (sanity)
        if (m_selectedPoint >= 0 && m_selectedControlPoint < 0)
        {
            Vector2D p = m_points[m_selectedPoint];
            float dx = x-p.m_x;
            float dy = y-p.m_y;

            // update point
            p.m_x = x;
            p.m_y = y;

            // update the point's control points (free form only)
            if (m_bFreeform)
            {
                for(int i=0 ; i < 2 ; i++)
                {
                    Vector2D cp = m_controlPoints[m_selectedPoint].m_controlPoint[i];
                    cp.m_x += dx;
                    cp.m_y += dy;
                }
            }
            else
            {
                // for cubic spline mode we resolve
                updateControlPoints(false);
            }

            m_dirty = true; // spline changed
        }
    }


    /**
     * Takes the current spline and backs it up to the orig array
     */
    private void backupSpline()
    {
        for(int i=0 ; i < m_pointCount ; i++)
        {
            // copy node point
            Vector2D p = m_points[i];
            Vector2D orig = m_origPoints[i];
            orig.m_x = p.m_x;
            orig.m_y = p.m_y;

            // copy control points
            ControlPoints cp = m_controlPoints[i];
            ControlPoints origCP = m_origControlPoints[i];
            for(int j=0 ; j < 2 ; j++)
            {
                origCP.m_controlPoint[j].m_x = cp.m_controlPoint[j].m_x;
                origCP.m_controlPoint[j].m_y = cp.m_controlPoint[j].m_y;
            }
        }
    }


    /**
     * Scales the spline based on the amount of relative motion the mouse moved
     * @param mx Mouse X
     * @param my Mouse Y
     */
    private void scaleSpline(int mx, int my)
    {
        // get spline's AABB
        Vector2D[] box = getSplineAABB();
        if (box != null)
        {
            // calculate deltas on X and Y
            int dx = mx - (int)m_lastMousePos.m_x;
            int dy = my - (int)m_lastMousePos.m_y;
            float scaleFactor = 1.0f;

            // calculate scale factor - the delta in mouse position relative to the box sizes (take the largest ratio)
            float boxWidth = box[1].m_x - box[0].m_x;
            float boxHeight = box[1].m_y - box[0].m_y;
            if (dx > dy)
            {
                // use delta in X to calculate factor
                scaleFactor += (float)dx / boxWidth;
            }
            else
            {
                // use delta in Y to calculate factor
                scaleFactor += (float)dy / boxHeight;
            }

            // do not allow to scale down to less than 10% of the orig size
            if (scaleFactor < 0.1f)
                return;

            // scale each point by finding its position relative to the box top-left corner and scaling that position
            for(int i=0 ; i < m_pointCount ; i++)
            {
                // use the backup to perform scaling and set result in the actual array
                Vector2D origPoint = m_origPoints[i];

                // scale actual spline point based on original point backup
                Vector2D p = m_points[i];
                p.m_x = origPoint.m_x;
                p.m_y = origPoint.m_y;
                p.substract(box[0]);
                p.multiply(scaleFactor);
                p.add(box[0]);

                // if in free form, scale control points as well
                if (m_bFreeform)
                {
                    // obtain the change in the node point's coords after the scale and move its
                    // control points accordingly
                    float deltax = p.m_x - origPoint.m_x;
                    float deltay = p.m_y - origPoint.m_y;
                    for(int j=0 ; j < 2 ; j++)
                    {
                        Vector2D origCP = m_origControlPoints[i].m_controlPoint[j];
                        Vector2D cp = m_controlPoints[i].m_controlPoint[j];
                        cp.m_x = origCP.m_x;
                        cp.m_y = origCP.m_y;
                        cp.m_x += deltax;
                        cp.m_y += deltay;
                    }
                }
            }

            // if in cubic spline mode, update control points
            if (!m_bFreeform)
                updateControlPoints(true);

            m_dirty = true; // changed
        }
    }


    /**
     * Save the spline
     */
    private void saveSpline(File splineFile)
    {
        if (m_pointCount == 0)
            return; // nothing to save

        // set the spline's file
        m_splineFile = splineFile;
        try
        {
            // open a buffered output stream writer to write the spline params
            FileOutputStream fos = new FileOutputStream(splineFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter writer = new BufferedWriter(osw);

            // write header
            String lineSep = System.getProperty("line.separator");
            writer.write("# Cusp generated spline, ");
            DateFormat df = DateFormat.getDateInstance(DateFormat.MEDIUM);
            writer.write(df.format(Calendar.getInstance().getTime())+lineSep);

            // write number of points
            writer.write("points="+m_pointCount+lineSep);

            // write points
            for(int i=0 ; i < m_pointCount ; i++)
            {
                // write node point info
                writer.write("p"+i+".x="+Float.toString(m_points[i].m_x)+lineSep);
                writer.write("p"+i+".y="+Float.toString(m_points[i].m_y)+lineSep);

                // write its control points
                ControlPoints cp = m_controlPoints[i];
                for(int j=0 ; j < 2 ; j++)
                {
                    writer.write("p"+i+"_cp"+j+".x="+Float.toString(cp.m_controlPoint[j].m_x)+lineSep);
                    writer.write("p"+i+"_cp"+j+".y="+Float.toString(cp.m_controlPoint[j].m_y)+lineSep);
                }
            }

            // close streams and flush
            writer.close();
            fos.close();

            // cleaned!
            m_dirty = false;
        }
        catch(IOException ioe)
        {
            // can't write for some reason
            JOptionPane.showMessageDialog(mainFrame, "Unable to save spline to: "+splineFile.getName(),
                    "Error saving spline", JOptionPane.CANCEL_OPTION);
        }
    }


    /**
     * Loads a spline from file
     * @param splineFile
     */
    private void loadSpline(File splineFile)
    {
        // perform reset on the current data
        reset();

        // since we're loading, this becomes the spline file
        m_splineFile = splineFile;
        try
        {
            // open the file in buffered mode
            FileInputStream fis = new FileInputStream(splineFile);
            InputStreamReader isr = new InputStreamReader(fis);
            BufferedReader reader = new BufferedReader(isr);

            // read header line
            String[] values;
            String line = reader.readLine();
            if (line.startsWith("# Cusp"))
            {
                // read number of points
                line = reader.readLine();
                values = line.split("=");
                if (values[0].equalsIgnoreCase("points"))
                    m_pointCount = Integer.parseInt(values[1]);

                // start reading points
                for(int i=0 ; i < m_pointCount ; i++)
                {
                    float x = 0.0f, y = 0.0f;

                    // read x coord line
                    line = reader.readLine();
                    values = line.split("=");
                    if (values[0].startsWith("p"+i))
                    {
                        // read x value
                        x = Float.parseFloat(values[1]);
                    }

                    // read y coord line
                    line = reader.readLine();
                    values = line.split("=");
                    if (values[0].startsWith("p"+i))
                    {
                        // read y value
                        y = Float.parseFloat(values[1]);
                    }

                    // add the main point directly
                    m_points[i] = new Vector2D(x, y);
                    m_origPoints[i] = new Vector2D(x, y);

                    // add control points to it
                    ControlPoints cp = new ControlPoints();
                    m_controlPoints[i] = cp;
                    m_origControlPoints[i] = new ControlPoints();

                    // start reading this point's control points
                    for(int j=0 ; j < 2 ; j++)
                    {
                        // read x coord line of control point
                        line = reader.readLine();
                        values = line.split("=");
                        if (values[0].startsWith("p"+i+"_cp"+j))
                        {
                            x = Float.parseFloat(values[1]);
                        }

                        // read y coord line of control point
                        line = reader.readLine();
                        values = line.split("=");
                        if (values[0].startsWith("p"+i+"_cp"+j))
                        {
                            y = Float.parseFloat(values[1]);
                        }

                        // set control point coords
                        cp.m_controlPoint[j].m_x = x;
                        cp.m_controlPoint[j].m_y = y;
                    }
                }

                // clean!
                m_dirty = false;

                // if we're in cubic spline mode we need to solve for control points
                if (!m_bFreeform)
                {
                    updateControlPoints(true);
                }
            }
            else
            {
                JOptionPane.showMessageDialog(mainFrame, "Invalid Cusp spline file: "+splineFile.getName(),
                        "Invalid spline", JOptionPane.CANCEL_OPTION);
            }

            // close streams
            reader.close();
            fis.close();
        }
        catch(IOException ioe)
        {
            // can't read spline for some reason
            JOptionPane.showMessageDialog(mainFrame, "Unable to read spline from: "+splineFile.getName(),
                    "Error loading spline", JOptionPane.CANCEL_OPTION);
        }
        catch(NumberFormatException nfe)
        {
            // invalid entry found
            JOptionPane.showMessageDialog(mainFrame, "Invalid data entry in file: "+splineFile.getName(),
                        "Invalid spline", JOptionPane.CANCEL_OPTION);
        }
    }


    /**
     * Exports the spline as a list of positions, based on the number of subdivisions wanted
     * @param exportFile The file to export to
     * @param subdivs The total number of subdivisions (samples) to perform on the spline
     */
    private void exportSpline(File exportFile, int subdivs)
    {
        if (m_pointCount == 0)
            return; // nothing to export

        try
        {
            // open file for writing
            FileOutputStream fos = new FileOutputStream(exportFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos);
            BufferedWriter writer = new BufferedWriter(osw);

            // write header
            int segments = m_pointCount-1;
            String lineSep = System.getProperty("line.separator");
            writer.write("# Cusp exported spline, points: "+subdivs+lineSep);

            // subdivide the spline with the wanted subdivisions per segment
            float segSteps = (float)subdivs / (float)segments;  // num steps per segment
            float stepSize = 1.0f / segSteps;
            int count = 0;
            for(int seg=0 ; seg < segments ; seg++)
            {
                // obtain this segment's points (first point, first point's 1st control point,
                // second point's 2nd control point and finally the second point)
                Vector2D startPt = m_points[seg];
                Vector2D startControlPt = m_controlPoints[seg].m_controlPoint[0];
                Vector2D endControlPt = m_controlPoints[seg+1].m_controlPoint[1];
                Vector2D endPt = m_points[seg+1];

                // subdivide the spline segment. we make sure we have at most subdivs number
                // of points so it matches the header (can be off because of floating point roundoff)
                float t = 0.0f;
                while (t < 1.0f && count < subdivs)
                {
                    // calculate new point position
                    // p(t) = (1-t)^3 * P0 + (1-t)^2 * t * P1 + (1-t) * t^2 * P2 + t^3 * P3
                    float px = startPt.m_x * (1-t)*(1-t)*(1-t);
                    float py = startPt.m_y * (1-t)*(1-t)*(1-t);
                    px += 3.0f * startControlPt.m_x * (1-t)*(1-t) * t;
                    py += 3.0f * startControlPt.m_y * (1-t)*(1-t) * t;
                    px += 3.0f * endControlPt.m_x * (1-t) * t*t;
                    py += 3.0f * endControlPt.m_y * (1-t) * t*t;
                    px += endPt.m_x * t*t*t;
                    py += endPt.m_y * t*t*t;

                    // write point position
                    writer.write(px+", "+py+lineSep);

                    // move forward in the segment by the step size
                    t += stepSize;
                    count++;
                }
            }

            // close streams
            writer.close();
            fos.close();
        }
        catch(IOException ioe)
        {
            // can't write for some reason
            JOptionPane.showMessageDialog(mainFrame, "Unable to export spline to: "+exportFile.getName(),
                    "Error exporting spline", JOptionPane.CANCEL_OPTION);
        }
    }


    /**
     * Normalize the file to point to something with csp extension
     * @param input Input save file from the file chooser dialog
     * @param inputExt The extension to verify
     * @return A corrected file if needed or the original
     */
    public static File normalizeFile(File input, String inputExt)
    {
        // normalize filename
        String parentPath = input.getParent();
        String filename = input.getName();
        int index = filename.lastIndexOf('.');
        if (index < 0)
        {
            // no extension, add csp extension
            input = new File(parentPath+File.separator+filename+"."+inputExt);
        }
        else
        {
            // make sure its csp extension
            if (filename.length() > index+1)
            {
                String ext = filename.substring(index+1);
                if (!ext.equalsIgnoreCase(inputExt))
                {
                    // add csp extension
                    input = new File(parentPath+File.separator+filename+"."+inputExt);
                }
            }
        }
        return input;
    }


    /**
     * A panel we can easily draw on
     */
    public class DrawPanel extends JPanel {

        public DrawPanel()
        {
            setOpaque(true);
            setBackground(Color.WHITE);
            setFocusable(true);
        }


        /**
         * Custom painting of the points and the circle if exists
         * @param g The graphics context
         */
        protected void paintComponent(Graphics g)
        {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D)g;

            // draw points as small filled circles
            drawPoints(g2d);

            // draw curve (freeform or interpolated cubic spline)
            drawCurve(g2d);

            // draw selected point if needed
            drawSelectedPoint(g2d);
        }


        /**
         * Draw all node points and if in free form mode, draw control points too
         * @param g2d
         */
        private void drawPoints(Graphics2D g2d)
        {
            g2d.setColor(m_navyColor);
            for(int i=0 ; i < m_pointCount ; i++)
            {
                // draw point. since the drawable point ellipse already has
                Vector2D p = m_points[i];
                drawablePoint.x = p.m_x - 2;
                drawablePoint.y = p.m_y - 2;
                g2d.fill(drawablePoint);
            }

            // draw control points for each node point and connecting lines (free form only)
            if (m_bFreeform)
            {
                for(int i=0 ; i < m_pointCount ; i++)
                {
                    int px = (int)m_points[i].m_x;
                    int py = (int)m_points[i].m_y;
                    ControlPoints cp = m_controlPoints[i];
                    for(int j=0 ; j < 2 ; j++)
                    {
                        int cx = (int)cp.m_controlPoint[j].m_x;
                        int cy = (int)cp.m_controlPoint[j].m_y;

                        // draw the line to the control point
                        g2d.setColor(Color.LIGHT_GRAY);
                        g2d.drawLine(px, py, cx, cy);

                        // draw the control point itself
                        drawablePoint.x = cx-2;
                        drawablePoint.y = cy-2;
                        g2d.setColor(Color.MAGENTA);
                        g2d.fill(drawablePoint);
                    }
                }
            }
        }   // end drawPoints


        /**
         * Draw the curve. The curve is made of several bezier curves (each one a segment)
         * @param g2d The graphics context to draw onto
         */
        private void drawCurve(Graphics2D g2d)
        {
            g2d.setColor(m_navyColor);

            // each node point contibutes one more segment (except the first)
            int segments = m_pointCount-1;
            for(int seg=0 ; seg < segments ; seg++)
            {
                // obtain this segment's points (first point, first point's 1st control point,
                // second point's 2nd control point and finally the second point)
                Vector2D startPt = m_points[seg];
                Vector2D startControlPt = m_controlPoints[seg].m_controlPoint[0];
                Vector2D endControlPt = m_controlPoints[seg+1].m_controlPoint[1];
                Vector2D endPt = m_points[seg+1];

                // line drawing coords
                int lastx = (int)startPt.m_x;
                int lasty = (int)startPt.m_y;

                // draw the spline segment
                float t = 0.0f;
                while (t < 1.0f)
                {
                    // calculate new point position
                    // p(t) = (1-t)^3 * P0 + (1-t)^2 * t * P1 + (1-t) * t^2 * P2 + t^3 * P3
                    float px = startPt.m_x * (1-t)*(1-t)*(1-t);
                    float py = startPt.m_y * (1-t)*(1-t)*(1-t);
                    px += 3.0f * startControlPt.m_x * (1-t)*(1-t) * t;
                    py += 3.0f * startControlPt.m_y * (1-t)*(1-t) * t;
                    px += 3.0f * endControlPt.m_x * (1-t) * t*t;
                    py += 3.0f * endControlPt.m_y * (1-t) * t*t;
                    px += endPt.m_x * t*t*t;
                    py += endPt.m_y * t*t*t;

                    // draw this step
                    g2d.drawLine(lastx, lasty, (int)px, (int)py);
                    lastx = (int)px;
                    lasty = (int)py;

                    // move forward in the segment by the step size
                    t += DEFAULT_SEGMENT_STEPSIZE;
                }
            }
        }   // end drawCurve


        /**
         * Draws the selected point
         * @param g2d
         */
        private void drawSelectedPoint(Graphics2D g2d)
        {
            if (m_selectedPoint >= 0 && m_selectedPoint < m_pointCount)
            {
                Vector2D p;
                if (m_selectedControlPoint >= 0 && m_selectedControlPoint < 2)
                {
                    // draw one of the control points as the selected point
                    p = m_controlPoints[m_selectedPoint].m_controlPoint[m_selectedControlPoint];
                }
                else
                {
                    p = m_points[m_selectedPoint];
                }

                // draw it
                selectedPoint.x = p.m_x - 3;
                selectedPoint.y = p.m_y - 3;
                g2d.setColor(Color.RED);
                g2d.fill(selectedPoint);
                g2d.setColor(m_navyColor);
            }
        }

    } // end DrawPanel


    /**
     * The control points class encapsulates a pair of control points used in Bezier
     * representation of a spline.
     */
    public class ControlPoints
    {
        // the control points
        public Vector2D[] m_controlPoint = new Vector2D[2];

        /**
         * default ctor
         */
        public ControlPoints()
        {
            m_controlPoint[0] = new Vector2D();
            m_controlPoint[1] = new Vector2D();
        }


        /**
         * Ctor from points
         * @param pa
         * @param pb
         */
        public ControlPoints(Vector2D pa, Vector2D pb)
        {
            m_controlPoint[0] = new Vector2D(pa);
            m_controlPoint[1] = new Vector2D(pb);
        }


        /**
         * Copy ctor
         * @param rhs
         */
        public ControlPoints(final ControlPoints rhs)
        {
            m_controlPoint[0] = new Vector2D(rhs.m_controlPoint[0]);
            m_controlPoint[1] = new Vector2D(rhs.m_controlPoint[1]);
        }

    }


    /**
     * A filter class for our file chooser
     */
    public static class SplineFileFilter extends FileFilter {

        private String extension;
        private String description;

        public SplineFileFilter(String extension, String description) {
            this.extension = extension;
            this.description = description;
        }

        public boolean accept(File file) {
            return (file.isDirectory()) ||
                    (file.getName().regionMatches(true, (file.getName().length()-extension.length()), extension, 0, extension.length()));
        }

        public String getDescription() {
            return description;
        }

        public String getExtension() {
            return extension;
        }
    }

}
