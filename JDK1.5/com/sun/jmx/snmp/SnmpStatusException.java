/*
 * @(#)file      SnmpStatusException.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   4.9
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */


package com.sun.jmx.snmp;


// "@(#)SnmpStatusException.java 3.2 98/11/05 SMI"



/**
 * Reports an error which occurred during a get/set operation on a mib node.
 *
 * This exception includes a status error code as defined in the SNMP protocol.
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject 
 * to change without notice.</b></p>
 * @version     3.2     11/05/98
 * @author      Sun Microsystems, Inc
 */

public class SnmpStatusException extends Exception implements SnmpDefinitions {


    /**
     * Error code as defined in RFC 1448 for: <CODE>noSuchName</CODE>.
     */
    public static final int noSuchName         = 2 ;
  
    /**
     * Error code as defined in RFC 1448 for: <CODE>badValue</CODE>.
     */
    public static final int badValue           = 3 ;
  
    /**
     * Error code as defined in RFC 1448 for: <CODE>readOnly</CODE>.
     */
    public static final int readOnly           = 4 ;
  
   
    /**
     * Error code as defined in RFC 1448 for: <CODE>noAccess</CODE>.
     */
    public static final int noAccess           = 6 ;
  
    /**
     * Error code for reporting a no such instance error.
     */
    public static final int noSuchInstance     = 0xE0;
  
    /**
     * Error code for reporting a no such object error.
     */
    public static final int noSuchObject     = 0xE1;
  
    /**
     * Constructs a new <CODE>SnmpStatusException</CODE> with the specified status error.
     * @param status The error status.
     */
    public SnmpStatusException(int status) {
	errorStatus = status ;
    }

    /**
     * Constructs a new <CODE>SnmpStatusException</CODE> with the specified status error and status index.
     * @param status The error status.
     * @param index The error index.
     */
    public SnmpStatusException(int status, int index) {
	errorStatus = status ;
	errorIndex = index ;
    }
  
    /**
     * Constructs a new <CODE>SnmpStatusException</CODE> with an error message.
     * The error status is set to 0 (noError) and the index to -1.
     * @param s The error message.
     */
    public SnmpStatusException(String s) {
	super(s);
    }
  
    /**
     * Constructs a new <CODE>SnmpStatusException</CODE> with an error index.
     * @param x The original <CODE>SnmpStatusException</CODE>.
     * @param index The error index.
     */
    public SnmpStatusException(SnmpStatusException x, int index) {
	super(x.getMessage());
	errorStatus= x.errorStatus;
	errorIndex= index;
    }

    /**
     * Return the error status.
     * @return The error status.
     */
    public int getStatus() {
	return errorStatus ;
    }
  
    /**
     * Returns the index of the error.
     * A value of -1 means that the index is not known/applicable.
     * @return The error index.
     */
    public int getErrorIndex() {
	return errorIndex;
    }

  
    // PRIVATE VARIABLES
    //--------------------
  
    /**
     * Status of the error.
     * @serial
     */
    private int errorStatus = 0 ;
  
    /**
     * Index of the error.
     * If different from -1, indicates the index where the error occurs.
     * @serial
     */
    private int errorIndex= -1;

}



