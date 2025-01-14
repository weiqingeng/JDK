/*
 * @(#)file      SnmpPduRequest.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   4.17
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */


package com.sun.jmx.snmp;




/**
 * Is used to represent <CODE>get</CODE>, <CODE>get-next</CODE>, <CODE>set</CODE>, <CODE>response</CODE> and <CODE>SNMPv2-trap</CODE> PDUs.
 * <P>
 * You will not usually need to use this class, except if you
 * decide to implement your own 
 * {@link com.sun.jmx.snmp.SnmpPduFactory SnmpPduFactory} object.
 *
 * @version     4.17     12/19/03
 * @author      Sun Microsystems, Inc
 * <p><b>This API is a Sun Microsystems internal API  and is subject 
 * to change without notice.</b></p>
 */

public class SnmpPduRequest extends SnmpPduPacket 
    implements SnmpPduRequestType {

    /**
     * Error status. Statuses are defined in 
     * {@link com.sun.jmx.snmp.SnmpDefinitions SnmpDefinitions}.
     * @serial
     */
    public int errorStatus=0 ;


    /**
     * Error index. Remember that SNMP indices start from 1.
     * Thus the corresponding <CODE>SnmpVarBind</CODE> is 
     * <CODE>varBindList[errorIndex-1]</CODE>.
     * @serial
     */
    public int errorIndex=0 ;
    /**
     * Implements <CODE>SnmpPduRequestType</CODE> interface.
     *
     * @since 1.5
     */
    public void setErrorIndex(int i) {
	errorIndex = i;
    }
    /**
     * Implements <CODE>SnmpPduRequestType</CODE> interface.
     *
     * @since 1.5
     */
    public void setErrorStatus(int i) {
	errorStatus = i;
    }
    /**
     * Implements <CODE>SnmpPduRequestType</CODE> interface.
     *
     * @since 1.5
     */
    public int getErrorIndex() { return errorIndex; }
    /**
     * Implements <CODE>SnmpPduRequestType</CODE> interface.
     *
     * @since 1.5
     */
    public int getErrorStatus() { return errorStatus; }
    /**
     * Implements <CODE>SnmpAckPdu</CODE> interface.
     *
     * @since 1.5
     */
    public SnmpPdu getResponsePdu() {
	SnmpPduRequest result = new SnmpPduRequest();
	result.address = address;
	result.port = port;
	result.version = version;
	result.community = community;
	result.type = SnmpDefinitions.pduGetResponsePdu;
	result.requestId = requestId;
	result.errorStatus = SnmpDefinitions.snmpRspNoError;
	result.errorIndex = 0;
	
 	return result;
    }
}





