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
package com.ibm.wala.classLoader;

import java.io.File;

import org.eclipse.core.resources.IFile;

import com.ibm.wala.util.io.FileSuffixes;

/**
 * 
 * A module which is a wrapper around a .java file
 * 
 * @author sfink
 */
public class EclipseSourceFileModule extends SourceFileModule  {
  private IFile f;
  public EclipseSourceFileModule(IFile f) {
    super(new File(f.getLocation().toOSString()),f.getLocation().lastSegment());
    this.f = f;
  }
  public IFile getIFile() {
    return f;
  }
  @Override
  public String toString() {
    return "EclipseSourceFileModule:" + getFile().toString();
  }
}
