/*
 * @(#)file      PrincipalImpl.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   4.15
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */


package com.sun.jmx.snmp.IPAcl;



import java.net.InetAddress;
import java.net.UnknownHostException;
import java.io.Serializable;


/**
 * Principal represents a host.
 *
 * @version     4.15     12/19/03
 * @author      Sun Microsystems, Inc
 */

class PrincipalImpl implements java.security.Principal, Serializable {
    private InetAddress[] add = null;
  
    /**
     * Constructs a principal with the local host.
     */
    public PrincipalImpl () throws UnknownHostException {
	add = new InetAddress[1];
        add[0] = java.net.InetAddress.getLocalHost();
    }
  
    /**
     * Construct a principal using the specified host.
     * <P>
     * The host can be either:
     * <UL>
     * <LI> a host name
     * <LI> an IP address
     * </UL>
     *
     * @param hostName the host used to make the principal.
     */
    public PrincipalImpl(String hostName) throws UnknownHostException {
        if ((hostName.equals("localhost")) || (hostName.equals("127.0.0.1"))) {
	    add = new InetAddress[1];
            add[0] = java.net.InetAddress.getByName(hostName);
	}
        else
            add = java.net.InetAddress.getAllByName( hostName );
    }
    
    /**
     * Constructs a principal using an Internet Protocol (IP) address.
     *
     * @param address the Internet Protocol (IP) address.
     */
    public PrincipalImpl(InetAddress address) {
        add = new InetAddress[1];
	add[0] = address;
    }
  
    /**
     * Returns the name of this principal.
     *
     * @return the name of this principal.
     */
    public String getName() {
        return add[0].toString();	
    }
  
    /**
     * Compares this principal to the specified object. Returns true if the
     * object passed in matches the principal
     * represented by the implementation of this interface. 
     *
     * @param a the principal to compare with.
     * @return true if the principal passed in is the same as that encapsulated by this principal, false otherwise. 
     */
    public boolean equals(Object a) {
        if (a instanceof PrincipalImpl){
	    for(int i = 0; i < add.length; i++) {
		if(add[i].equals ((InetAddress)((PrincipalImpl) a).getAddress()))
		    return true;
	    }
	    return false;
        } else {
            return false;
        }
    }
    
    /**
     * Returns a hashcode for this principal. 
     *
     * @return a hashcode for this principal. 
     */
    public int hashCode(){
        return add[0].hashCode();
    }
  
    /**
     * Returns a string representation of this principal. In case of multiple address, the first one is returned.
     *
     * @return a string representation of this principal.
     */
    public String toString() {
        return ("PrincipalImpl :"+add[0].toString());
    }
  
    /**
     * Returns the Internet Protocol (IP) address for this principal. In case of multiple address, the first one is returned.
     *
     * @return the Internet Protocol (IP) address for this principal.
     */
    public InetAddress getAddress(){
        return add[0];
    }

    /**
     * Returns the Internet Protocol (IP) address for this principal. In case of multiple address, the first one is returned.
     *
     * @return the array of Internet Protocol (IP) addresses for this principal.
     */
    public InetAddress[] getAddresses(){
        return add;
    }
}

