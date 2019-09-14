/*
 * @(#)file      JDMTrapBlock.java
 * @(#)author    Sun Microsystems, Inc.
 * @(#)version   4.8
 * @(#)date      08/05/28
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 */


/* Generated By:JJTree: Do not edit this line. JDMTrapBlock.java */

package com.sun.jmx.snmp.IPAcl;

import java.util.Hashtable;

class JDMTrapBlock extends SimpleNode {
  JDMTrapBlock(int id) {
    super(id);
  }

  JDMTrapBlock(Parser p, int id) {
    super(p, id);
  }

  public static Node jjtCreate(int id) {
      return new JDMTrapBlock(id);
  }

  public static Node jjtCreate(Parser p, int id) {
      return new JDMTrapBlock(p, id);
  }

  /**
   * Do no need to go through this part of the tree for
   * building AclEntry.
   */
   public void buildAclEntries(PrincipalImpl owner, AclImpl acl) {}

  /**
   * Do no need to go through this part of the tree for
   * building InformEntry.
   */
   public void buildInformEntries(Hashtable dest) {}
}