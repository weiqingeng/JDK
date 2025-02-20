/*
 * @(#)file      JDMTrapInterestedHost.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   4.7
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */


/* Generated By:JJTree: Do not edit this line. JDMTrapInterestedHost.java */

package com.sun.jmx.snmp.IPAcl;

/** 
 * @version     4.7     12/19/03 
 * @author      Sun Microsystems, Inc. 
 */ 
class JDMTrapInterestedHost extends SimpleNode {
  JDMTrapInterestedHost(int id) {
    super(id);
  }

  JDMTrapInterestedHost(Parser p, int id) {
    super(p, id);
  }

  public static Node jjtCreate(int id) {
      return new JDMTrapInterestedHost(id);
  }

  public static Node jjtCreate(Parser p, int id) {
      return new JDMTrapInterestedHost(p, id);
  }
}
