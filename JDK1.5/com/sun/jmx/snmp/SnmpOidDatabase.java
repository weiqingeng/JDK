/*
 * @(#)file      SnmpOidDatabase.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   1.12
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */


package com.sun.jmx.snmp;


// java import
//
import java.util.Vector;

// jmx import
//
import com.sun.jmx.snmp.SnmpOidTable;
import com.sun.jmx.snmp.SnmpOidRecord;
import com.sun.jmx.snmp.SnmpStatusException;

/**
 * Defines the minimal functionality that should be provided by
 * a class containing a set of <CODE>SnmpOidTable</CODE> objects containing metadata definitions for MIB variables.
 * Each <CODE>SnmpOidTable</CODE> should contain information on variables of one MIB.
 * The <CODE>SnmpOidDatabase</CODE> is a "repository" of <CODE>SnmpOidTable</CODE>.
 * It extends the <CODE>SnmpOidTable</CODE> interface in order to provide resolution of the MIB variables.
 * <P>
 * <p><b>This API is a Sun Microsystems internal API  and is subject 
 * to change without notice.</b></p>
 * @see com.sun.jmx.snmp.SnmpOidTable
 */

public interface SnmpOidDatabase extends SnmpOidTable {

    /**
     * Adds an <CODE>SnmpOidTable</CODE> object in this <CODE>SnmpOidDatabase</CODE>.
     * @param table The table to add.
     */
    public void add(SnmpOidTable table);

    /**
     * Removes an <CODE>SnmpOidTable</CODE> object from this <CODE>SnmpOidDatabase</CODE>.
     * @param table The table to be removed.
     */
    public void remove(SnmpOidTable table) throws SnmpStatusException;

    /**
     * Removes all the <CODE>SnmpOidTable</CODE> objects from this <CODE>SnmpOidDatabase</CODE>.
     */
    public void removeAll();

    /**
     * Searches for a MIB variable given its logical name and returns an <CODE>SnmpOidRecord</CODE> 
     * object containing information on the variable.
     * @param name The name of the MIB variable.
     * @return The <CODE>SnmpOidRecord</CODE> object containing information on the variable.
     */
    public SnmpOidRecord resolveVarName(String name) throws SnmpStatusException ;

    /**
     * Searches for a MIB variable given its OID and returns an <CODE>SnmpOidRecord</CODE> object containing 
     * information on the variable.
     * @param oid The OID of the MIB variable.
     * @return The <CODE>SnmpOidRecord</CODE> object containing information on the variable.
     */
    public SnmpOidRecord resolveVarOid(String oid) throws SnmpStatusException;

    /**
     * Returns a list that can be used to traverse all the entries of the <CODE>SnmpOidTable</CODE> objects
     * of this <CODE>SnmpOidDatabase</CODE>.
     * @return A vector of <CODE>SnmpOidTable</CODE> objects containing all the elements of this <CODE>SnmpOidDatabase</CODE>.
     */
    public Vector getAllEntries() ;
}
