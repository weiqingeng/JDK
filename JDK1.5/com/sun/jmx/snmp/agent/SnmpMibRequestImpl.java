/*
 * @(#)file      SnmpMibRequestImpl.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   1.14
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package com.sun.jmx.snmp.agent;

import java.util.Enumeration;
import java.util.Vector;


import com.sun.jmx.snmp.SnmpPdu;
import com.sun.jmx.snmp.SnmpVarBind;
import com.sun.jmx.snmp.SnmpEngine;

/**
 * This class implements the SnmpMibRequest interface.
 * It represents the part of a SNMP request that involves a specific
 * MIB. One instance of this class will be created for every MIB 
 * involved in a SNMP request, and will be passed to the SnmpMibAgent
 * in charge of handling that MIB.
 * 
 * Instances of this class are allocated by the SNMP engine. You will
 * never need to use this class directly. You will only access
 * instances of this class through their SnmpMibRequest interface.
 *
 */
final class SnmpMibRequestImpl implements SnmpMibRequest {

    /**
     * @param engine The local engine.
     * @param reqPdu The received pdu.
     * @param vblist The vector of SnmpVarBind objects in which the
     *        MIB concerned by this request is involved.
     * @param protocolVersion  The protocol version of the SNMP request.
     * @param userData     User allocated contextual data. This object must
     *        be allocated on a per SNMP request basis through the 
     *        SnmpUserDataFactory registered with the SnmpAdaptorServer,
     *        and is handed back to the user through SnmpMibRequest objects.
     */
    public SnmpMibRequestImpl(SnmpEngine engine,
			      SnmpPdu reqPdu,
			      Vector vblist, 
			      int protocolVersion, 
			      Object userData,
			      String principal,
			      int securityLevel,
			      int securityModel,
			      byte[] contextName,
			      byte[] accessContextName) {
	varbinds   = vblist;
	version    = protocolVersion;
	data       = userData;
	this.reqPdu = reqPdu;
	this.engine = engine;
	this.principal = principal;
	this.securityLevel = securityLevel;
	this.securityModel = securityModel;
	this.contextName = contextName;
	this.accessContextName = accessContextName;
    }
    // -------------------------------------------------------------------
    // PUBLIC METHODS from SnmpMibRequest
    // -------------------------------------------------------------------
    
    /**
     * Returns the local engine. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise null is returned.
     * @return the local engine.
     */
    public SnmpEngine getEngine() {
	return engine;
    }
    
    /**
     * Gets the incoming request principal. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise null is returned.
     * @return The request principal.
     **/
    public String getPrincipal() {
	return principal;
    }
    
    /**
     * Gets the incoming request security level. This level is defined in {@link com.sun.jmx.snmp.SnmpEngine SnmpEngine}. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise -1 is returned.
     * @return The security level.
     */
    public int getSecurityLevel() {
	return securityLevel;
    }
    /**
     * Gets the incoming request security model. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise -1 is returned.
     * @return The security model.
     */
    public int getSecurityModel() {
	return securityModel;
    }
    /**
     * Gets the incoming request context name. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise null is returned.
     * @return The context name.
     */
    public byte[] getContextName() {
	return contextName;
    }
    
    /**
     * Gets the incoming request context name used by Access Control Model in order to allow or deny the access to OIDs. This parameter is returned only if <CODE> SnmpV3AdaptorServer </CODE> is the adaptor receiving this request. Otherwise null is returned.
     * @return The checked context.
     */
    public byte[] getAccessContextName() {
	return accessContextName;
    }
    
    // -------------------------------------------------------------------
    // Implements the method defined in SnmpMibRequest interface.
    // See SnmpMibRequest for the java doc.
    // -------------------------------------------------------------------
    public final SnmpPdu getPdu() {
	return reqPdu;
    }

    // -------------------------------------------------------------------
    // Implements the method defined in SnmpMibRequest interface.
    // See SnmpMibRequest for the java doc.
    // -------------------------------------------------------------------
    public final Enumeration getElements()  {return varbinds.elements();}

    // -------------------------------------------------------------------
    // Implements the method defined in SnmpMibRequest interface.
    // See SnmpMibRequest for the java doc.
    // -------------------------------------------------------------------
    public final Vector getSubList()  {return varbinds;}

    // -------------------------------------------------------------------
    // Implements the method defined in SnmpMibRequest interface.
    // See SnmpMibRequest for the java doc.
    // -------------------------------------------------------------------
    public final int getSize()  {
	if (varbinds == null) return 0;
	return varbinds.size();
    }

    // -------------------------------------------------------------------
    // Implements the method defined in SnmpMibRequest interface.
    // See SnmpMibRequest for the java doc.
    // -------------------------------------------------------------------
    public final int         getVersion()  {return version;}

    // -------------------------------------------------------------------
    // Implements the method defined in SnmpMibRequest interface.
    // See SnmpMibRequest for the java doc.
    // -------------------------------------------------------------------
    public final int         getRequestPduVersion()  {return reqPdu.version;}

    // -------------------------------------------------------------------
    // Implements the method defined in SnmpMibRequest interface.
    // See SnmpMibRequest for the java doc.
    // -------------------------------------------------------------------
    public final Object      getUserData() {return data;}

    // -------------------------------------------------------------------
    // Implements the method defined in SnmpMibRequest interface.
    // See SnmpMibRequest for the java doc.
    // -------------------------------------------------------------------
    public final int getVarIndex(SnmpVarBind varbind) {
	return varbinds.indexOf(varbind);
    }

    // -------------------------------------------------------------------
    // Implements the method defined in SnmpMibRequest interface.
    // See SnmpMibRequest for the java doc.
    // -------------------------------------------------------------------
    public void addVarBind(SnmpVarBind varbind) {
	varbinds.addElement(varbind);
    }

    // -------------------------------------------------------------------
    // PACKAGE METHODS
    // -------------------------------------------------------------------

    // -------------------------------------------------------------------
    // Allow to pass the request tree built during the check() phase
    // to the set() method. Note: the if the tree is `null', then the
    // set() method will rebuild a new tree identical to the tree built
    // in the check() method.
    //
    // Passing this tree in the SnmpMibRequestImpl object allows to 
    // optimize the SET requests.
    //
    // -------------------------------------------------------------------
    final void setRequestTree(SnmpRequestTree tree) {this.tree = tree;}

    // -------------------------------------------------------------------
    // Returns the SnmpRequestTree object built in the first operation 
    // phase for two-phase SNMP requests (like SET).
    // -------------------------------------------------------------------
    final SnmpRequestTree getRequestTree() {return tree;}

    // -------------------------------------------------------------------
    // Returns the underlying vector of SNMP varbinds (used for algorithm
    // optimization).
    // -------------------------------------------------------------------
    final Vector getVarbinds() {return varbinds;}
 
    // -------------------------------------------------------------------
    // Private variables
    // -------------------------------------------------------------------

    // Ideally these variables should be declared final but it makes
    // the jdk1.1.x compiler complain (seems to be a compiler bug, jdk1.2
    // is OK).
    private Vector varbinds;
    private int    version;
    private Object data;
    private SnmpPdu reqPdu = null;
    // Non final variable.
    private SnmpRequestTree tree = null;
    private SnmpEngine engine = null;
    private String principal = null;
    private int securityLevel = -1;
    private int securityModel = -1;
    private byte[] contextName = null;
    private byte[] accessContextName = null;
}
