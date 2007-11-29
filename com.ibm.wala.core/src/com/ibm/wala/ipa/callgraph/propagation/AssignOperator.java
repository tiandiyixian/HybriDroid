/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.ipa.callgraph.propagation;

import com.ibm.wala.fixedpoint.impl.UnaryOperator;
import com.ibm.wala.fixedpoint.impl.UnaryStatement;
import com.ibm.wala.util.debug.Trace;

/**
 * Corresponds to: "is a superset of". Used for assignment.
 * 
 * Unary op: <lhs>:= Assign( <rhs>)
 * 
 * (Technically, it's a binary op, since it includes lhs as an implicit input;
 * this allows it to compose with other ops that define the same lhs, so long as
 * they're all Assign ops)
 */
class AssignOperator extends UnaryOperator<PointsToSetVariable> implements IPointerOperator {

  @Override
  public UnaryStatement<PointsToSetVariable> makeEquation(PointsToSetVariable lhs, PointsToSetVariable rhs) {
    return new AssignEquation(lhs, rhs);
  }

  @Override
  public byte evaluate(PointsToSetVariable lhs, PointsToSetVariable rhs) {

    if (PropagationCallGraphBuilder.DEBUG_ASSIGN) {
      String S = "EVAL Assign " + lhs.getPointerKey() + " " + rhs.getPointerKey();
      S = S + "\nEVAL " + lhs + " " + rhs;
      Trace.println(S);
    }
    boolean changed = lhs.addAll(rhs);
    if (PropagationCallGraphBuilder.DEBUG_ASSIGN) {
        Trace.println("RESULT " + lhs + (changed ? " (changed)" : ""));
    }

    if (PropagationCallGraphBuilder.DEBUG_TRACK_INSTANCE) {
      if (changed) {
        if (lhs.contains(PropagationCallGraphBuilder.DEBUG_INSTANCE_KEY)
            && rhs.contains(PropagationCallGraphBuilder.DEBUG_INSTANCE_KEY)) {
          Trace.println("Assign: FLOW FROM " + rhs.getPointerKey() + " TO " + lhs.getPointerKey());
        }
      }
    }

    return changed ? CHANGED : NOT_CHANGED;
  }

  @Override
  public String toString() {
    return "Assign";
  }

  @Override
  public int hashCode() {
    return 9883;
  }

  @Override
  public final boolean equals(Object o) {
    // this is a singleton
    return (this == o);
  }

  /* 
   * @see com.ibm.wala.ipa.callgraph.propagation.IPointerOperator#isComplex()
   */
  public boolean isComplex() {
    return false;
  }
}