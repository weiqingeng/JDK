/*
 * @(#)UIDefaults.java	1.58 04/05/05
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package javax.swing;


import javax.swing.plaf.ComponentUI;
import javax.swing.border.*;
import javax.swing.event.SwingPropertyChangeSupport;

import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.ResourceBundle;
import java.util.Locale;
import java.util.Vector;
import java.util.MissingResourceException;
import java.awt.Font;
import java.awt.Color;
import java.awt.Insets;
import java.awt.Dimension;
import java.lang.reflect.Method;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;
import java.security.AccessController;
import java.security.AccessControlContext;
import java.security.PrivilegedAction;

import sun.reflect.misc.MethodUtil;

/**
 * A table of defaults for Swing components.  Applications can set/get
 * default values via the <code>UIManager</code>.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases. The current serialization support is
 * appropriate for short term storage or RMI between applications running
 * the same version of Swing.  As of 1.4, support for long term storage
 * of all JavaBeans<sup><font size="-2">TM</font></sup>
 * has been added to the <code>java.beans</code> package.
 * Please see {@link java.beans.XMLEncoder}.
 *
 * @see UIManager
 * @version 1.58 05/05/04
 * @author Hans Muller
 */
public class UIDefaults extends Hashtable<Object,Object>
{
    private static final Object PENDING = new String("Pending");

    private SwingPropertyChangeSupport changeSupport;

    private Vector resourceBundles;

    private Locale defaultLocale = Locale.getDefault();

    /**
     * Maps from a Locale to a cached Map of the ResourceBundle. This is done
     * so as to avoid an exception being thrown when a value is asked for.
     * Access to this should be done while holding a lock on the
     * UIDefaults, eg synchronized(this).
     */
    private Map resourceCache;

    /**
     * Create an empty defaults table.
     */
    public UIDefaults() {
        super(700, .75f);
        resourceCache = new HashMap();
    }


    /**
     * Create a defaults table initialized with the specified
     * key/value pairs.  For example:
     * <pre>
        Object[] uiDefaults = {
             "Font", new Font("Dialog", Font.BOLD, 12),
            "Color", Color.red,
             "five", new Integer(5)
        }
        UIDefaults myDefaults = new UIDefaults(uiDefaults);
     * </pre>
     * @param keyValueList  an array of objects containing the key/value
     *		pairs
     */
    public UIDefaults(Object[] keyValueList) {
        super(keyValueList.length / 2);
        for(int i = 0; i < keyValueList.length; i += 2) {
            super.put(keyValueList[i], keyValueList[i + 1]);
        }
    }


    /**
     * Returns the value for key.  If the value is a
     * <code>UIDefaults.LazyValue</code> then the real
     * value is computed with <code>LazyValue.createValue()</code>,
     * the table entry is replaced, and the real value is returned.
     * If the value is an <code>UIDefaults.ActiveValue</code>
     * the table entry is not replaced - the value is computed
     * with <code>ActiveValue.createValue()</code> for each
     * <code>get()</code> call.
     *
     * If the key is not found in the table then it is searched for in the list
     * of resource bundles maintained by this object.  The resource bundles are
     * searched most recently added first using the locale returned by
     * <code>getDefaultLocale</code>.  <code>LazyValues</code> and
     * <code>ActiveValues</code> are not supported in the resource bundles.

     *
     * @param key the desired key
     * @return the value for <code>key</code>
     * @see LazyValue
     * @see ActiveValue
     * @see java.util.Hashtable#get
     * @see #getDefaultLocale
     * @see #addResourceBundle
     * @since 1.4
     */
    public Object get(Object key) {
        Object value = getFromHashtable( key );
        return (value != null) ? value : getFromResourceBundle(key, null);
    }

    /**
     * Looks up up the given key in our Hashtable and resolves LazyValues
     * or ActiveValues.
     */
    private Object getFromHashtable(Object key) {
        /* Quickly handle the common case, without grabbing
         * a lock.
         */
        Object value = super.get(key);
        if ((value != PENDING) &&
            !(value instanceof ActiveValue) &&
            !(value instanceof LazyValue)) {
            return value;
        }

        /* If the LazyValue for key is being constructed by another
         * thread then wait and then return the new value, otherwise drop
         * the lock and construct the ActiveValue or the LazyValue.
         * We use the special value PENDING to mark LazyValues that
         * are being constructed.
         */
        synchronized(this) {
            value = super.get(key);
            if (value == PENDING) {
                do {
                    try {
                        this.wait();
                    }
                    catch (InterruptedException e) {
                    }
                    value = super.get(key);
                }
                while(value == PENDING);
                return value;
            }
            else if (value instanceof LazyValue) {
                super.put(key, PENDING);
            }
            else if (!(value instanceof ActiveValue)) {
                return value;
            }
        }

        /* At this point we know that the value of key was
         * a LazyValue or an ActiveValue.
         */
        if (value instanceof LazyValue) {
            try {
                /* If an exception is thrown we'll just put the LazyValue
                 * back in the table.
                 */
                value = ((LazyValue)value).createValue(this);
            }
            finally {
                synchronized(this) {
                    if (value == null) {
                        super.remove(key);
                    }
                    else {
                        super.put(key, value);
                    }
                    this.notifyAll();
                }
            }
        }
        else {
            value = ((ActiveValue)value).createValue(this);
        }

        return value;
    }


    /**
     * Returns the value for key associated with the given locale.
     * If the value is a <code>UIDefaults.LazyValue</code> then the real
     * value is computed with <code>LazyValue.createValue()</code>,
     * the table entry is replaced, and the real value is returned.
     * If the value is an <code>UIDefaults.ActiveValue</code>
     * the table entry is not replaced - the value is computed
     * with <code>ActiveValue.createValue()</code> for each
     * <code>get()</code> call.
     *
     * If the key is not found in the table then it is searched for in the list
     * of resource bundles maintained by this object.  The resource bundles are
     * searched most recently added first using the given locale.
     * <code>LazyValues</code> and <code>ActiveValues</code> are not supported
     * in the resource bundles.
     *
     * @param key the desired key
     * @param l the desired <code>locale</code>
     * @return the value for <code>key</code>
     * @see LazyValue
     * @see ActiveValue
     * @see java.util.Hashtable#get
     * @see #addResourceBundle
     * @since 1.4
     */
    public Object get(Object key, Locale l) {
        Object value = getFromHashtable( key );
        return (value != null) ? value : getFromResourceBundle(key, l);
    }

    /**
     * Looks up given key in our resource bundles.
     */
    private Object getFromResourceBundle(Object key, Locale l) {

        if( resourceBundles == null ||
            resourceBundles.isEmpty() ||
            !(key instanceof String) ) {
            return null;
        }

        // A null locale means use the default locale.
        if( l == null ) {
            if( defaultLocale == null )
                return null;
            else
                l = (Locale)defaultLocale;
        }

        synchronized(this) {
            return getResourceCache(l).get((String)key);
        }
    }

    /**
     * Returns a Map of the known resources for the given locale.
     */
    private Map getResourceCache(Locale l) {
        Map values = (Map)resourceCache.get(l);

        if (values == null) {
            values = new HashMap();
            for (int i=resourceBundles.size()-1; i >= 0; i--) {
                String bundleName = (String)resourceBundles.get(i);
                try {
                    ResourceBundle b = ResourceBundle.getBundle(bundleName, l);
                    Enumeration keys = b.getKeys();

                    while (keys.hasMoreElements()) {
                        String key = (String)keys.nextElement();

                        if (values.get(key) == null) {
                            Object value = b.getObject(key);

                            values.put(key, value);
                        }
                    }
                } catch( MissingResourceException mre ) {
                    // Keep looking
                }
            }
            resourceCache.put(l, values);
        }
        return values;
    }

    /**
     * Sets the value of <code>key</code> to <code>value</code> for all locales.
     * If <code>key</code> is a string and the new value isn't
     * equal to the old one, fire a <code>PropertyChangeEvent</code>.
     * If value is <code>null</code>, the key is removed from the table.
     *
     * @param key    the unique <code>Object</code> who's value will be used
     *          to retrieve the data value associated with it
     * @param value  the new <code>Object</code> to store as data under
     *		that key
     * @return the previous <code>Object</code> value, or <code>null</code>
     * @see #putDefaults
     * @see java.util.Hashtable#put
     */
    public Object put(Object key, Object value) {
        Object oldValue = (value == null) ? super.remove(key) : super.put(key, value);
        if (key instanceof String) {
            firePropertyChange((String)key, oldValue, value);
        }
        return oldValue;
    }


    /**
     * Puts all of the key/value pairs in the database and
     * unconditionally generates one <code>PropertyChangeEvent</code>.
     * The events oldValue and newValue will be <code>null</code> and its
     * <code>propertyName</code> will be "UIDefaults".  The key/value pairs are
     * added for all locales.
     *
     * @param keyValueList  an array of key/value pairs
     * @see #put
     * @see java.util.Hashtable#put
     */
    public void putDefaults(Object[] keyValueList) {
        for(int i = 0, max = keyValueList.length; i < max; i += 2) {
            Object value = keyValueList[i + 1];
            if (value == null) {
                super.remove(keyValueList[i]);
            }
            else {
                super.put(keyValueList[i], value);
            }
        }
        firePropertyChange("UIDefaults", null, null);
    }


    /**
     * If the value of <code>key</code> is a <code>Font</code> return it,
     * otherwise return <code>null</code>.
     * @param key the desired key
     * @return if the value for <code>key</code> is a <code>Font</code>,
     * 		return the <code>Font</code> object; otherwise return
     *		<code>null</code>
     */
    public Font getFont(Object key) {
        Object value = get(key);
        return (value instanceof Font) ? (Font)value : null;
    }


    /**
     * If the value of <code>key</code> for the given <code>Locale</code>
     * is a <code>Font</code> return it, otherwise return <code>null</code>.
     * @param key the desired key
     * @param l the desired locale      
     * @return if the value for <code>key</code> and <code>Locale</code>
     *          is a <code>Font</code>,
     * 		return the <code>Font</code> object; otherwise return
     *		<code>null</code>
     * @since 1.4
     */
    public Font getFont(Object key, Locale l) {
        Object value = get(key,l);
        return (value instanceof Font) ? (Font)value : null;
    }

    /**
     * If the value of <code>key</code> is a <code>Color</code> return it,
     * otherwise return <code>null</code>.
     * @param key the desired key
     * @return if the value for <code>key</code> is a <code>Color</code>,
     *		return the <code>Color</code> object; otherwise return
     *		<code>null</code>
     */
    public Color getColor(Object key) {
        Object value = get(key);
        return (value instanceof Color) ? (Color)value : null;
    }


    /**
     * If the value of <code>key</code> for the given <code>Locale</code>
     * is a <code>Color</code> return it, otherwise return <code>null</code>.
     * @param key the desired key
     * @param l the desired locale      
     * @return if the value for <code>key</code> and <code>Locale</code>
     *          is a <code>Color</code>,
     *		return the <code>Color</code> object; otherwise return
     *		<code>null</code>
     * @since 1.4
     */
    public Color getColor(Object key, Locale l) {
        Object value = get(key,l);
        return (value instanceof Color) ? (Color)value : null;
    }


    /**
     * If the value of <code>key</code> is an <code>Icon</code> return it,
     * otherwise return <code>null</code>.
     * @param key the desired key
     * @return if the value for <code>key</code> is an <code>Icon</code>,
     *		return the <code>Icon</code> object; otherwise return
     *		<code>null</code>
     */
    public Icon getIcon(Object key) {
        Object value = get(key);
        return (value instanceof Icon) ? (Icon)value : null;
    }


    /**
     * If the value of <code>key</code> for the given <code>Locale</code>
     * is an <code>Icon</code> return it, otherwise return <code>null</code>.
     * @param key the desired key
     * @param l the desired locale      
     * @return if the value for <code>key</code> and <code>Locale</code>
     *          is an <code>Icon</code>,
     *		return the <code>Icon</code> object; otherwise return
     *		<code>null</code>
     * @since 1.4
     */
    public Icon getIcon(Object key, Locale l) {
        Object value = get(key,l);
        return (value instanceof Icon) ? (Icon)value : null;
    }


    /**
     * If the value of <code>key</code> is a <code>Border</code> return it,
     * otherwise return <code>null</code>.
     * @param key the desired key
     * @return if the value for <code>key</code> is a <code>Border</code>,
     *		return the <code>Border</code> object; otherwise return
     *		<code>null</code>
     */
    public Border getBorder(Object key) {
        Object value = get(key);
        return (value instanceof Border) ? (Border)value : null;
    }


    /**
     * If the value of <code>key</code> for the given <code>Locale</code>
     * is a <code>Border</code> return it, otherwise return <code>null</code>.
     * @param key the desired key
     * @param l the desired locale      
     * @return if the value for <code>key</code> and <code>Locale</code>
     *          is a <code>Border</code>,
     *		return the <code>Border</code> object; otherwise return
     *		<code>null</code>
     * @since 1.4
     */
    public Border getBorder(Object key, Locale l)  {
        Object value = get(key,l);
        return (value instanceof Border) ? (Border)value : null;
    }


    /**
     * If the value of <code>key</code> is a <code>String</code> return it,
     * otherwise return <code>null</code>.
     * @param key the desired key
     * @return if the value for <code>key</code> is a <code>String</code>,
     *		return the <code>String</code> object; otherwise return
     *		<code>null</code>
     */
    public String getString(Object key) {
        Object value = get(key);
        return (value instanceof String) ? (String)value : null;
    }

    /**
     * If the value of <code>key</code> for the given <code>Locale</code>
     * is a <code>String</code> return it, otherwise return <code>null</code>.
     * @param key the desired key
     * @param l the desired <code>Locale</code>
     * @return if the value for <code>key</code> for the given
     *          <code>Locale</code> is a <code>String</code>,
     *		return the <code>String</code> object; otherwise return
     *		<code>null</code>
     * @since 1.4
     */
    public String getString(Object key, Locale l) {
        Object value = get(key,l);
        return (value instanceof String) ? (String)value : null;
    }

    /**
     * If the value of <code>key</code> is an <code>Integer</code> return its
     * integer value, otherwise return 0.
     * @param key the desired key
     * @return if the value for <code>key</code> is an <code>Integer</code>,
     *		return its value, otherwise return 0
     */
    public int getInt(Object key) {
        Object value = get(key);
        return (value instanceof Integer) ? ((Integer)value).intValue() : 0;
    }


    /**
     * If the value of <code>key</code> for the given <code>Locale</code>
     * is an <code>Integer</code> return its integer value, otherwise return 0.
     * @param key the desired key
     * @param l the desired locale      
     * @return if the value for <code>key</code> and <code>Locale</code>
     *          is an <code>Integer</code>,
     *		return its value, otherwise return 0
     * @since 1.4
     */
    public int getInt(Object key, Locale l) {
        Object value = get(key,l);
        return (value instanceof Integer) ? ((Integer)value).intValue() : 0;
    }


    /**
     * If the value of <code>key</code> is boolean, return the
     * boolean value, otherwise return false.
     *
     * @param key an <code>Object</code> specifying the key for the desired boolean value
     * @return if the value of <code>key</code> is boolean, return the
     *         boolean value, otherwise return false.
     * @since 1.4
     */
    public boolean getBoolean(Object key) {
        Object value = get(key);
        return (value instanceof Boolean) ? ((Boolean)value).booleanValue() : false;
    }


    /**
     * If the value of <code>key</code> for the given <code>Locale</code>
     * is boolean, return the boolean value, otherwise return false.
     *
     * @param key an <code>Object</code> specifying the key for the desired boolean value
     * @param l the desired locale      
     * @return if the value for <code>key</code> and <code>Locale</code>
     *         is boolean, return the
     *         boolean value, otherwise return false.
     * @since 1.4
     */
    public boolean getBoolean(Object key, Locale l) {
        Object value = get(key,l);
        return (value instanceof Boolean) ? ((Boolean)value).booleanValue() : false;
    }


    /**
     * If the value of <code>key</code> is an <code>Insets</code> return it,
     * otherwise return <code>null</code>.
     * @param key the desired key
     * @return if the value for <code>key</code> is an <code>Insets</code>,
     *		return the <code>Insets</code> object; otherwise return
     *		<code>null</code>
     */
    public Insets getInsets(Object key) {
        Object value = get(key);
        return (value instanceof Insets) ? (Insets)value : null;
    }


    /**
     * If the value of <code>key</code> for the given <code>Locale</code>
     * is an <code>Insets</code> return it, otherwise return <code>null</code>.
     * @param key the desired key
     * @param l the desired locale      
     * @return if the value for <code>key</code> and <code>Locale</code>
     *          is an <code>Insets</code>,
     *		return the <code>Insets</code> object; otherwise return
     *		<code>null</code>
     * @since 1.4
     */
    public Insets getInsets(Object key, Locale l) {
        Object value = get(key,l);
        return (value instanceof Insets) ? (Insets)value : null;
    }


    /**
     * If the value of <code>key</code> is a <code>Dimension</code> return it,
     * otherwise return <code>null</code>.
     * @param key the desired key
     * @return if the value for <code>key</code> is a <code>Dimension</code>,
     *		return the <code>Dimension</code> object; otherwise return
     *		<code>null</code>
     */
    public Dimension getDimension(Object key) {
        Object value = get(key);
        return (value instanceof Dimension) ? (Dimension)value : null;
    }


    /**
     * If the value of <code>key</code> for the given <code>Locale</code>
     * is a <code>Dimension</code> return it, otherwise return <code>null</code>.
     * @param key the desired key
     * @param l the desired locale      
     * @return if the value for <code>key</code> and <code>Locale</code>
     *          is a <code>Dimension</code>,
     *		return the <code>Dimension</code> object; otherwise return
     *		<code>null</code>
     * @since 1.4
     */
    public Dimension getDimension(Object key, Locale l) {
        Object value = get(key,l);
        return (value instanceof Dimension) ? (Dimension)value : null;
    }


    /**
     * The value of <code>get(uidClassID)</code> must be the
     * <code>String</code> name of a
     * class that implements the corresponding <code>ComponentUI</code>
     * class.  If the class hasn't been loaded before, this method looks 
     * up the class with <code>uiClassLoader.loadClass()</code> if a non 
     * <code>null</code>
     * class loader is provided, <code>classForName()</code> otherwise.
     * <p>
     * If a mapping for <code>uiClassID</code> exists or if the specified
     * class can't be found, return <code>null</code>.
     * <p>
     * This method is used by <code>getUI</code>, it's usually
     * not necessary to call it directly.
     *
     * @param uiClassID  a string containing the class ID
     * @param uiClassLoader the object which will load the class
     * @return the value of <code>Class.forName(get(uidClassID))</code>
     * @see #getUI
     */
    public Class<? extends ComponentUI>
	getUIClass(String uiClassID, ClassLoader uiClassLoader)
    {
        try {
            String className = (String)get(uiClassID);
            if (className != null) {
                Class cls = (Class)get(className);
                if (cls == null) {
                    if (uiClassLoader == null) {
                        cls = SwingUtilities.loadSystemClass(className);
                    }
                    else {
                        cls = uiClassLoader.loadClass(className);
                    }
                    if (cls != null) {
                        // Save lookup for future use, as forName is slow.
                        put(className, cls);
                    }
                }
                return cls;
            }
        } 
	catch (ClassNotFoundException e) {
            return null;
        } 
	catch (ClassCastException e) {
            return null;
        }
        return null;
    }


    /**
     * Returns the L&F class that renders this component.
     *
     * @param uiClassID a string containing the class ID
     * @return the Class object returned by
     *		<code>getUIClass(uiClassID, null)</code>
     */
    public Class<? extends ComponentUI> getUIClass(String uiClassID) {
	return getUIClass(uiClassID, null);
    }


    /**
     * If <code>getUI()</code> fails for any reason,
     * it calls this method before returning <code>null</code>.
     * Subclasses may choose to do more or less here.
     *
     * @param msg message string to print
     * @see #getUI
     */
    protected void getUIError(String msg) {
        System.err.println("UIDefaults.getUI() failed: " + msg);
        try {
            throw new Error();
        }
        catch (Throwable e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates an <code>ComponentUI</code> implementation for the
     * specified component.  In other words create the look
     * and feel specific delegate object for <code>target</code>.
     * This is done in two steps:
     * <ul>
     * <li> Look up the name of the <code>ComponentUI</code> implementation
     * class under the value returned by <code>target.getUIClassID()</code>.
     * <li> Use the implementation classes static <code>createUI()</code>
     * method to construct a look and feel delegate.
     * </ul>
     * @param target  the <code>JComponent</code> which needs a UI
     * @return the <code>ComponentUI</code> object
     */
    public ComponentUI getUI(JComponent target) {

        Object cl = get("ClassLoader");
	ClassLoader uiClassLoader = 
	    (cl != null) ? (ClassLoader)cl : target.getClass().getClassLoader();
        Class uiClass = getUIClass(target.getUIClassID(), uiClassLoader);
        Object uiObject = null;

        if (uiClass == null) {
            getUIError("no ComponentUI class for: " + target);
        }
        else {
            try {
		Method m = (Method)get(uiClass);
		if (m == null) {
		    Class acClass = javax.swing.JComponent.class;
		    m = uiClass.getMethod("createUI", new Class[]{acClass});
		    put(uiClass, m);
		}
		uiObject = MethodUtil.invoke(m, null, new Object[]{target});
            }
            catch (NoSuchMethodException e) {
                getUIError("static createUI() method not found in " + uiClass);
            }
            catch (Exception e) {
                getUIError("createUI() failed for " + target + " " + e);
            }
        }

        return (ComponentUI)uiObject;
    }

    /**
     * Adds a <code>PropertyChangeListener</code> to the listener list.
     * The listener is registered for all properties.
     * <p>
     * A <code>PropertyChangeEvent</code> will get fired whenever a default
     * is changed.
     *
     * @param listener  the <code>PropertyChangeListener</code> to be added
     * @see java.beans.PropertyChangeSupport
     */
    public synchronized void addPropertyChangeListener(PropertyChangeListener listener) {
        if (changeSupport == null) {
            changeSupport = new SwingPropertyChangeSupport(this);
        }
        changeSupport.addPropertyChangeListener(listener);
    }


    /**
     * Removes a <code>PropertyChangeListener</code> from the listener list.
     * This removes a <code>PropertyChangeListener</code> that was registered
     * for all properties.
     *
     * @param listener  the <code>PropertyChangeListener</code> to be removed
     * @see java.beans.PropertyChangeSupport
     */
    public synchronized void removePropertyChangeListener(PropertyChangeListener listener) {
        if (changeSupport != null) {
            changeSupport.removePropertyChangeListener(listener);
        }
    }


    /**
     * Returns an array of all the <code>PropertyChangeListener</code>s added
     * to this UIDefaults with addPropertyChangeListener().
     *
     * @return all of the <code>PropertyChangeListener</code>s added or an empty
     *         array if no listeners have been added
     * @since 1.4
     */
    public synchronized PropertyChangeListener[] getPropertyChangeListeners() {
        if (changeSupport == null) {
            return new PropertyChangeListener[0];
        }
        return changeSupport.getPropertyChangeListeners();
    }


    /**
     * Support for reporting bound property changes.  If oldValue and
     * newValue are not equal and the <code>PropertyChangeEvent</code>x
     * listener list isn't empty, then fire a 
     * <code>PropertyChange</code> event to each listener.
     *
     * @param propertyName  the programmatic name of the property
     *		that was changed
     * @param oldValue  the old value of the property
     * @param newValue  the new value of the property
     * @see java.beans.PropertyChangeSupport
     */
    protected void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
        if (changeSupport != null) {
            changeSupport.firePropertyChange(propertyName, oldValue, newValue);
        }
    }


    /**
     * Adds a resource bundle to the list of resource bundles that are
     * searched for localized values.  Resource bundles are searched in the
     * reverse order they were added.  In other words, the most recently added
     * bundle is searched first.
     *
     * @param bundleName  the base name of the resource bundle to be added
     * @see java.util.ResourceBundle
     * @see #removeResourceBundle
     * @since 1.4
     */
    public synchronized void addResourceBundle( String bundleName ) {
        if( bundleName == null ) {
            return;
        }
        if( resourceBundles == null ) {
            resourceBundles = new Vector(5);
        }
        if (!resourceBundles.contains(bundleName)) {
            resourceBundles.add( bundleName );
            resourceCache.clear();
        }
    }


    /**
     * Removes a resource bundle from the list of resource bundles that are
     * searched for localized defaults.
     *
     * @param bundleName  the base name of the resource bundle to be removed
     * @see java.util.ResourceBundle
     * @see #addResourceBundle
     * @since 1.4
     */
    public synchronized void removeResourceBundle( String bundleName ) {
        if( resourceBundles != null ) {
            resourceBundles.remove( bundleName );
        }
        resourceCache.clear();
    }

    /**
     * Sets the default locale.  The default locale is used in retrieving
     * localized values via <code>get</code> methods that do not take a
     * locale argument.  As of release 1.4, Swing UI objects should retrieve
     * localized values using the locale of their component rather than the
     * default locale.  The default locale exists to provide compatibility with
     * pre 1.4 behaviour.
     *
     * @param l the new default locale
     * @see #getDefaultLocale
     * @see #get(Object)
     * @see #get(Object,Locale)
     * @since 1.4
     */
    public void setDefaultLocale( Locale l ) {
        defaultLocale = l;
    }

    /**
     * Returns the default locale.  The default locale is used in retrieving
     * localized values via <code>get</code> methods that do not take a
     * locale argument.  As of release 1.4, Swing UI objects should retrieve
     * localized values using the locale of their component rather than the
     * default locale.  The default locale exists to provide compatibility with
     * pre 1.4 behaviour.
     *
     * @return the default locale
     * @see #setDefaultLocale
     * @see #get(Object)
     * @see #get(Object,Locale)
     * @since 1.4
     */
    public Locale getDefaultLocale() {
        return defaultLocale;
    }

    /**
     * This class enables one to store an entry in the defaults
     * table that isn't constructed until the first time it's
     * looked up with one of the <code>getXXX(key)</code> methods.
     * Lazy values are useful for defaults that are expensive
     * to construct or are seldom retrieved.  The first time
     * a <code>LazyValue</code> is retrieved its "real value" is computed
     * by calling <code>LazyValue.createValue()</code> and the real
     * value is used to replace the <code>LazyValue</code> in the
     * <code>UIDefaults</code>
     * table.  Subsequent lookups for the same key return
     * the real value.  Here's an example of a <code>LazyValue</code>
     * that constructs a <code>Border</code>:
     * <pre>
     *  Object borderLazyValue = new UIDefaults.LazyValue() {
     *      public Object createValue(UIDefaults table) {
     *          return new BorderFactory.createLoweredBevelBorder();
     *      }
     *  };
     *
     *  uiDefaultsTable.put("MyBorder", borderLazyValue);
     * </pre>
     *
     * @see UIDefaults#get
     */
    public interface LazyValue {
        /**
         * Creates the actual value retrieved from the <code>UIDefaults</code>
         * table. When an object that implements this interface is
         * retrieved from the table, this method is used to create
         * the real value, which is then stored in the table and
         * returned to the calling method.
         *
         * @param table  a <code>UIDefaults</code> table
         * @return the created <code>Object</code>
         */
        Object createValue(UIDefaults table);
    }


    /**
     * This class enables one to store an entry in the defaults
     * table that's constructed each time it's looked up with one of
     * the <code>getXXX(key)</code> methods. Here's an example of
     * an <code>ActiveValue</code> that constructs a
     * <code>DefaultListCellRenderer</code>:
     * <pre>
     *  Object cellRendererActiveValue = new UIDefaults.ActiveValue() {
     *      public Object createValue(UIDefaults table) {
     *          return new DefaultListCellRenderer();
     *      }
     *  };
     *
     *  uiDefaultsTable.put("MyRenderer", cellRendererActiveValue);
     * </pre>
     *
     * @see UIDefaults#get
     */
    public interface ActiveValue {
        /**
         * Creates the value retrieved from the <code>UIDefaults</code> table.
         * The object is created each time it is accessed.
         *
         * @param table  a <code>UIDefaults</code> table
         * @return the created <code>Object</code> 
         */
        Object createValue(UIDefaults table);
    }

    /**
     * This class provides an implementation of <code>LazyValue</code>
     * which can be
     * used to delay loading of the Class for the instance to be created.
     * It also avoids creation of an anonymous inner class for the
     * <code>LazyValue</code>
     * subclass.  Both of these improve performance at the time that a
     * a Look and Feel is loaded, at the cost of a slight performance
     * reduction the first time <code>createValue</code> is called
     * (since Reflection APIs are used).
     */
    public static class ProxyLazyValue implements LazyValue {
        private AccessControlContext acc;
	private String className;
	private String methodName;
	private Object[] args;

	/**
	 * Creates a <code>LazyValue</code> which will construct an instance
	 * when asked.
	 * 
	 * @param c    a <code>String</code> specifying the classname 
	 *             of the instance to be created on demand
	 */
	public ProxyLazyValue(String c) {
            this(c, (String)null);
	}
	/**
	 * Creates a <code>LazyValue</code> which will construct an instance
	 * when asked.
	 * 
	 * @param c    a <code>String</code> specifying the classname of
         *		the class
	 *             	containing a static method to be called for
	 *             	instance creation
	 * @param m    a <code>String</code> specifying the static 
         *		method to be called on class c
	 */
	public ProxyLazyValue(String c, String m) {
            this(c, m, null);
	}
	/**
	 * Creates a <code>LazyValue</code> which will construct an instance
	 * when asked.
	 * 
	 * @param c    a <code>String</code> specifying the classname
         *		of the instance to be created on demand
	 * @param o    an array of <code>Objects</code> to be passed as
         *		paramaters to the constructor in class c
	 */
	public ProxyLazyValue(String c, Object[] o) {
            this(c, null, o);
	}
	/**
	 * Creates a <code>LazyValue</code> which will construct an instance
	 * when asked.
	 * 
	 * @param c    a <code>String</code> specifying the classname
         *		of the class
	 *              containing a static method to be called for
	 *              instance creation.
	 * @param m    a <code>String</code> specifying the static method
         *		to be called on class c
	 * @param o    an array of <code>Objects</code> to be passed as
         *		paramaters to the static method in class c
	 */
	public ProxyLazyValue(String c, String m, Object[] o) {
            acc = AccessController.getContext();
	    className = c;
	    methodName = m;
            if (o != null) {
                args = (Object[])o.clone();
            }
	}

        /**
         * Creates the value retrieved from the <code>UIDefaults</code> table.
         * The object is created each time it is accessed.
         *
         * @param table  a <code>UIDefaults</code> table
         * @return the created <code>Object</code>
         */
	public Object createValue(final UIDefaults table) {
            // In order to pick up the security policy in effect at the
            // time of creation we use a doPrivileged with the
            // AccessControlContext that was in place when this was created.
	    return AccessController.doPrivileged(new PrivilegedAction() {
                public Object run() {
                    try {
                        Class c;
                        Object cl;
                        // See if we should use a separate ClassLoader
                        if (table == null || !((cl = table.get("ClassLoader"))
                                               instanceof ClassLoader)) {
                            cl = Thread.currentThread().
                                        getContextClassLoader();
                            if (cl == null) {
                                // Fallback to the system class loader.
                                cl = ClassLoader.getSystemClassLoader();
                            }
                        }
                        c = Class.forName(className, true, (ClassLoader)cl);
                        if (methodName != null) {
                            Class[] types = getClassArray(args);
                            Method m = c.getMethod(methodName, types);
                            return MethodUtil.invoke(m, c, args);
                        } else {
                            Class[] types = getClassArray(args);
                            Constructor constructor = c.getConstructor(types);
                            return constructor.newInstance(args);
                        }
                    } catch(Exception e) {
                        // Ideally we would throw an exception, unfortunately
                        // often times there are errors as an initial look and
                        // feel is loaded before one can be switched. Perhaps a
                        // flag should be added for debugging, so that if true
                        // the exception would be thrown.
                    }
                    return null;
                }
            }, acc);
	}

	/* 
	 * Coerce the array of class types provided into one which
	 * looks the way the Reflection APIs expect.  This is done
	 * by substituting primitive types for their Object counterparts,
	 * and superclasses for subclasses used to add the 
         * <code>UIResource</code> tag.
	 */
	private Class[] getClassArray(Object[] args) {
	    Class[] types = null;
	    if (args!=null) {
		types = new Class[args.length];
		for (int i = 0; i< args.length; i++) {
		    /* PENDING(ges): At present only the primitive types 
		       used are handled correctly; this should eventually
		       handle all primitive types */
		    if (args[i] instanceof java.lang.Integer) {
			types[i]=Integer.TYPE;
		    } else if (args[i] instanceof java.lang.Boolean) {
			types[i]=Boolean.TYPE;			
		    } else if (args[i] instanceof javax.swing.plaf.ColorUIResource) {
			/* PENDING(ges) Currently the Reflection APIs do not 
			   search superclasses of parameters supplied for
			   constructor/method lookup.  Since we only have
			   one case where this is needed, we substitute
			   directly instead of adding a massive amount
			   of mechanism for this.  Eventually this will
			   probably need to handle the general case as well.
			   */
			types[i]=java.awt.Color.class;
		    } else {
			types[i]=args[i].getClass();
		    }
		}
	    }
	    return types;
	}

	private String printArgs(Object[] array) {
	    String s = "{";	    
	    if (array !=null) {
		for (int i = 0 ; i < array.length-1; i++) {
		    s = s.concat(array[i] + ",");
		}
		s = s.concat(array[array.length-1] + "}");
	    } else {
		s = s.concat("}");
	    }
	    return s;
	}
    }


    /**
     * <code>LazyInputMap</code> will create a <code>InputMap</code>
     * in its <code>createValue</code>
     * method. The bindings are passed in in the constructor.
     * The bindings are an array with
     * the even number entries being string <code>KeyStrokes</code>
     * (eg "alt SPACE") and
     * the odd number entries being the value to use in the
     * <code>InputMap</code> (and the key in the <code>ActionMap</code>).
     */
    public static class LazyInputMap implements LazyValue {
	/** Key bindings are registered under. */
	private Object[] bindings;

	public LazyInputMap(Object[] bindings) {
	    this.bindings = bindings;
	}

        /**
         * Creates an <code>InputMap</code> with the bindings that are
         * passed in.
         *
         * @param table a <code>UIDefaults</code> table
         * @return the <code>InputMap</code>
         */
        public Object createValue(UIDefaults table) {
	    if (bindings != null) {
		InputMap km = LookAndFeel.makeInputMap(bindings);
		return km;
	    }
	    return null;
	}
    }
}
