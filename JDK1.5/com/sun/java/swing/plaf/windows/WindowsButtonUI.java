/*
 * @(#)WindowsButtonUI.java	1.36 06/03/22
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.java.swing.plaf.windows;

import javax.swing.plaf.basic.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import javax.swing.*;

import java.awt.*;

import com.sun.java.swing.plaf.windows.TMSchema.*;
import com.sun.java.swing.plaf.windows.XPStyle.Skin;

/**
 * Windows button.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases.  The current serialization support is appropriate
 * for short term storage or RMI between applications running the same
 * version of Swing.  A future release of Swing will provide support for
 * long term persistence.
 *
 * @version 1.36 03/22/06
 * @author Jeff Dinkins
 *
 */
public class WindowsButtonUI extends BasicButtonUI
{
    private final static WindowsButtonUI windowsButtonUI = new WindowsButtonUI();

    protected int dashedRectGapX;
    protected int dashedRectGapY;
    protected int dashedRectGapWidth;
    protected int dashedRectGapHeight;

    protected Color focusColor;

    private boolean defaults_initialized = false;
    

    // ********************************
    //          Create PLAF
    // ********************************
    public static ComponentUI createUI(JComponent c){
	return windowsButtonUI;
    }
    

    // ********************************
    //            Defaults
    // ********************************
    protected void installDefaults(AbstractButton b) {
	super.installDefaults(b);
	if(!defaults_initialized) {
	    String pp = getPropertyPrefix();
	    dashedRectGapX = UIManager.getInt(pp + "dashedRectGapX");
	    dashedRectGapY = UIManager.getInt(pp + "dashedRectGapY");
	    dashedRectGapWidth = UIManager.getInt(pp + "dashedRectGapWidth");
	    dashedRectGapHeight = UIManager.getInt(pp + "dashedRectGapHeight");
	    focusColor = UIManager.getColor(pp + "focus");
	    defaults_initialized = true;
	}

	XPStyle xp = XPStyle.getXP();
	if (xp != null) {
            b.setBorder(xp.getBorder(b, getXPButtonType(b)));
            LookAndFeel.installProperty(b, "rolloverEnabled", Boolean.TRUE);
	}
    }
    
    protected void uninstallDefaults(AbstractButton b) {
	super.uninstallDefaults(b);
	defaults_initialized = false;
    }

    protected Color getFocusColor() {
	return focusColor;
    }
    
    // ********************************
    //         Paint Methods
    // ********************************

    /**
     * Overridden method to render the text without the mnemonic
     */
    protected void paintText(Graphics g, AbstractButton b, Rectangle textRect, String text) {
	WindowsGraphicsUtils.paintText(g, b, textRect, text, getTextShiftOffset());
    } 
	
    protected void paintFocus(Graphics g, AbstractButton b, Rectangle viewRect, Rectangle textRect, Rectangle iconRect){
	if (b.getParent() instanceof JToolBar) {
	    // Windows doesn't draw the focus rect for buttons in a toolbar.
	    return;
	}
	    
	if (XPStyle.getXP() != null) {
	    return;
	}

	// focus painted same color as text on Basic??
	int width = b.getWidth();
	int height = b.getHeight();
	g.setColor(getFocusColor());
	BasicGraphicsUtils.drawDashedRect(g, dashedRectGapX, dashedRectGapY,
					  width - dashedRectGapWidth, height - dashedRectGapHeight);
    }
    
    protected void paintButtonPressed(Graphics g, AbstractButton b){
	setTextShiftOffset();
    }

    // ********************************
    //          Layout Methods
    // ********************************
    public Dimension getPreferredSize(JComponent c) {
	Dimension d = super.getPreferredSize(c);
	
	/* Ensure that the width and height of the button is odd,
	 * to allow for the focus line if focus is painted
	 */
        AbstractButton b = (AbstractButton)c;
	if (d != null && b.isFocusPainted()) {
	    if(d.width % 2 == 0) { d.width += 1; }
	    if(d.height % 2 == 0) { d.height += 1; }
	}
	return d;
    }


    /* These rectangles/insets are allocated once for all 
     * ButtonUI.paint() calls.  Re-using rectangles rather than 
     * allocating them in each paint call substantially reduced the time
     * it took paint to run.  Obviously, this method can't be re-entered.
     */
    private static Rectangle viewRect = new Rectangle();

    public void paint(Graphics g, JComponent c) {
	if (XPStyle.getXP() != null) {
	    WindowsButtonUI.paintXPButtonBackground(g, c);
	}
	super.paint(g, c);
    }

    static Part getXPButtonType(AbstractButton b) { 
        boolean toolbar = (b.getParent() instanceof JToolBar);
        return toolbar ? Part.TP_BUTTON : Part.BP_PUSHBUTTON; 
    }

    static void paintXPButtonBackground(Graphics g, JComponent c) {
	AbstractButton b = (AbstractButton)c;

	XPStyle xp = XPStyle.getXP();

	boolean toolbar = (b.getParent() instanceof JToolBar);
        Part part = getXPButtonType(b);

	if (b.isContentAreaFilled() && xp != null) {

	    ButtonModel model = b.getModel();
            Skin skin = xp.getSkin(b, part); 

	    // normal, rollover/activated/focus, pressed, disabled, default
            State state = State.NORMAL; 
	    if (toolbar) {
                if (model.isArmed() && model.isPressed()) {
                    state = State.PRESSED;
                } else if (!model.isEnabled()) {
                    state = State.DISABLED;
                } else if (model.isSelected() && model.isRollover()) {
                    state = State.HOTCHECKED;
                } else if (model.isSelected()) {
                    state = State.CHECKED;
                } else if (model.isRollover()) {
                    state = State.HOT;
                }
	    } else {
		if (model.isArmed() && model.isPressed() || model.isSelected()) {
                    state = State.PRESSED;
                } else if (!model.isEnabled()) {
                    state = State.DISABLED;
                } else if (model.isRollover() || model.isPressed()) {
                    state = State.HOT;
                } else if (b instanceof JButton && ((JButton)b).isDefaultButton()) {
                    state = State.DEFAULTED;
                } else if (c.hasFocus()) {
                    state = State.HOT;
                }
	    }
	    Dimension d = c.getSize();
	    int dx = 0;
	    int dy = 0;
	    int dw = d.width;
	    int dh = d.height;

	    Border border = c.getBorder();
	    Insets insets;
	    if (border != null) {
		// Note: The border may be compound, containing an outer
		// opaque border (supplied by the application), plus an
		// inner transparent margin border. We want to size the
		// background to fill the transparent part, but stay
		// inside the opaque part.
		insets = WindowsButtonUI.getOpaqueInsets(border, c);
	    } else {
		insets = c.getInsets();
	    }
	    if (insets != null) {
		dx += insets.left;
		dy += insets.top;
		dw -= (insets.left + insets.right);
		dh -= (insets.top + insets.bottom);
	    }
            skin.paintSkin(g, dx, dy, dw, dh, state);
	}
    }

    /**
     * returns - b.getBorderInsets(c) if border is opaque
     *         - null if border is completely non-opaque
     *         - somewhere inbetween if border is compound and
     *              outside border is opaque and inside isn't
     */
    private static Insets getOpaqueInsets(Border b, Component c) {
	if (b == null) {
	    return null;
	}
	if (b.isBorderOpaque()) {
	    return b.getBorderInsets(c);
	} else if (b instanceof CompoundBorder) {
	    CompoundBorder cb = (CompoundBorder)b;
	    Insets iOut = getOpaqueInsets(cb.getOutsideBorder(), c);
	    if (iOut != null && iOut.equals(cb.getOutsideBorder().getBorderInsets(c))) {
		// Outside border is opaque, keep looking
		Insets iIn = getOpaqueInsets(cb.getInsideBorder(), c);
		if (iIn == null) {
		    // Inside is non-opaque, use outside insets
		    return iOut;
		} else {
		    // Found non-opaque somewhere in the inside (which is
		    // also compound).
		    return new Insets(iOut.top + iIn.top, iOut.left + iIn.left,
				      iOut.bottom + iIn.bottom, iOut.right + iIn.right);
		}
	    } else {
		// Outside is either all non-opaque or has non-opaque
		// border inside another compound border
		return iOut;
	    }
	} else {
	    return null;
	}
    }
}

