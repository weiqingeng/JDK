/*
 * @(#)file      SnmpOidTable.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   4.14
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */
// Copyright (c) 1995-96 by Cisco Systems, Inc.

package com.sun.jmx.snmp;


// java import
//
import java.util.Vector;


/**
 * Defines the minimum functionality that should be provided by
 * a class containing metadata definitions for variables of a MIB. 
 * A name can be resolved against a table of MIB variables.
 * Each entry in the table is an <CODE>SnmpOidRecord</CODE> object that contains a name, a dot-separated OID string, 
 * and the corresponding SMI type of the variable. 
 * <P>
 * If you need to load a specific <CODE>SnmpOidTable</CODE>, just call the static method
 * {@link com.sun.jmx.snmp.SnmpOid#setSnmpOidTable <CODE>SnmpOid.setSnmpOidTable(<I>myOidTable</I>)</CODE>}.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject 
 * to change without notice.</b></p>
 * @see com.sun.jmx.snmp.SnmpOidRecord
 * 
 * @version     4.14     12/19/03
 * @author      Sun Microsystems, Inc
 *
 */


public interface SnmpOidTable {

    /**
     * Searches for a MIB variable given its logical name and returns an {@link com.sun.jmx.snmp.SnmpOidRecord} object
     * containing information on the variable.
     *
     * @param name The name of the MIB variable.
     * @return The <CODE>SnmpOidRecord</CODE> object containing information on the variable.
     * @exception SnmpStatusException If the variable is not found.
     */
    public SnmpOidRecord resolveVarName(String name) 
	throws SnmpStatusException;


    /**
     * Searches for a MIB variable given its OID and returns an {@link com.sun.jmx.snmp.SnmpOidRecord} object
     * containing information on the variable.
     *
     * @param oid The OID of the MIB variable.
     * @return The <CODE>SnmpOidRecord</CODE> object containing information 
     *         on the variable.
     * @exception SnmpStatusException If the variable is not found.
     */
    public SnmpOidRecord resolveVarOid(String oid) 
	throws SnmpStatusException;

    /**
     * Returns a list that can be used to traverse all the entries this <CODE>SnmpOidTable</CODE>.
     * @return A Vector of {@link com.sun.jmx.snmp.SnmpOidRecord} objects.
     */
    public Vector getAllEntries();
}
