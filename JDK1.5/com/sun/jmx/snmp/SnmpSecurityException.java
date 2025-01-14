/*
 * @(#)file      SnmpSecurityException.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   1.16
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */
package com.sun.jmx.snmp;

/**
 * This exception is thrown when an error occurs in an <CODE> SnmpSecurityModel </CODE>.
 * <p><b>This API is a Sun Microsystems internal API  and is subject 
 * to change without notice.</b></p>
 * @since 1.5
 */
public class SnmpSecurityException extends Exception {
    /**
     * The current request varbind list.
     */
    public SnmpVarBind[] list = null;
    /**
     * The status of the exception. See {@link com.sun.jmx.snmp.SnmpDefinitions} for possible values.
     */
    public int status = SnmpDefinitions.snmpReqUnknownError;
    /**
     * The current security model related security parameters.
     */
    public SnmpSecurityParameters params = null;
    /**
     * The current context engine Id.
     */
    public byte[] contextEngineId = null;
     /**
     * The current context name.
     */
    public byte[] contextName = null;
     /**
     * The current flags.
     */
    public byte flags = (byte) SnmpDefinitions.noAuthNoPriv;
    /**
     * Constructor.
     * @param msg The exception msg to display.
     */
    public SnmpSecurityException(String msg) {
	super(msg);
    }
}
