/*
 * @(#)file      SnmpPduTrap.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   4.10
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */


package com.sun.jmx.snmp;



/**
 * Represents an SNMPv1-trap PDU.
 * <P>
 * You will not usually need to use this class, except if you
 * decide to implement your own 
 * {@link com.sun.jmx.snmp.SnmpPduFactory SnmpPduFactory} object.
 * <P>
 * The <CODE>SnmpPduTrap</CODE> extends {@link com.sun.jmx.snmp.SnmpPduPacket SnmpPduPacket} 
 * and defines attributes specific to an SNMPv1 trap (see RFC1157).
 *
 * <p><b>This API is a Sun Microsystems internal API  and is subject 
 * to change without notice.</b></p>
 * @version     4.10     12/19/03
 * @author      Sun Microsystems, Inc
 */

public class SnmpPduTrap extends SnmpPduPacket {

    
    /**
     * Enterprise object identifier.
     * @serial
     */
    public SnmpOid        enterprise ;
  
    /**
     * Agent address. If the agent address source was not an IPv4 one (eg : IPv6), this field is null.
     * @serial
     */
    public SnmpIpAddress  agentAddr ;
  
    /**
     * Generic trap number.
     * <BR>
     * The possible values are defined in 
     * {@link com.sun.jmx.snmp.SnmpDefinitions#trapColdStart SnmpDefinitions}.
     * @serial
     */
    public int            genericTrap ;
  
    /**
     * Specific trap number.
     * @serial
     */
    public int            specificTrap ;
  
    /**
     * Time-stamp.
     * @serial
     */
    public long            timeStamp ;



    /**
     * Builds a new trap PDU.
     * <BR><CODE>type</CODE> and <CODE>version</CODE> fields are initialized with
     * {@link com.sun.jmx.snmp.SnmpDefinitions#pduV1TrapPdu pduV1TrapPdu}
     * and {@link com.sun.jmx.snmp.SnmpDefinitions#snmpVersionOne snmpVersionOne}.
     */
    public SnmpPduTrap() {
	type = pduV1TrapPdu ;
	version = snmpVersionOne ;
    }
}




