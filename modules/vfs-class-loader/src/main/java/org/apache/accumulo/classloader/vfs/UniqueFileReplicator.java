/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.accumulo.classloader.vfs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSelector;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.provider.FileReplicator;
import org.apache.commons.vfs2.provider.UriParser;
import org.apache.commons.vfs2.provider.VfsComponent;
import org.apache.commons.vfs2.provider.VfsComponentContext;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class UniqueFileReplicator implements VfsComponent, FileReplicator {

  private static final char[] TMP_RESERVED_CHARS =
      {'?', '/', '\\', ' ', '&', '"', '\'', '*', '#', ';', ':', '<', '>', '|'};

  private File tempDir;
  private VfsComponentContext context;
  private List<File> tmpFiles = Collections.synchronizedList(new ArrayList<>());

  public UniqueFileReplicator(File tempDir) {
    this.tempDir = tempDir;
    if (!tempDir.exists() && !tempDir.mkdirs())
      System.out.println("Unexpected error creating directory: " + tempDir.getAbsolutePath());
  }

  @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
      justification = "input files are specified by admin, not unchecked user input")
  @Override
  public File replicateFile(FileObject srcFile, FileSelector selector) throws FileSystemException {
    String baseName = srcFile.getName().getBaseName();

    try {
      if (!tempDir.exists()) {
        throw new IOException("Directory no longer exists: " + tempDir.getAbsolutePath());
      }
      String safeBasename = UriParser.encode(baseName, TMP_RESERVED_CHARS).replace('%', '_');
      File file = null;
      try {
        file = File.createTempFile("vfsr_", "_" + safeBasename, tempDir);
      } catch (IOException ioe) {
        throw new IOException("Error creating temp file in directory: " + tempDir, ioe);
      }
      file.deleteOnExit();

      final FileObject destFile = context.toFileObject(file);
      destFile.copyFrom(srcFile, selector);

      return file;
    } catch (IOException e) {
      throw new FileSystemException(e);
    }
  }

  @Override
  public void setLogger(Log logger) {
    // TODO Auto-generated method stub

  }

  @Override
  public void setContext(VfsComponentContext context) {
    this.context = context;
  }

  @Override
  public void init() throws FileSystemException {

  }

  @Override
  public void close() {
    synchronized (tmpFiles) {
      for (File tmpFile : tmpFiles) {
        if (!tmpFile.delete())
          System.out.println("File does not exist: " + tmpFile.getAbsolutePath());
      }
    }

    if (tempDir.exists()) {
      String[] list = tempDir.list();
      int numChildren = list == null ? 0 : list.length;
      if (numChildren == 0 && !tempDir.delete())
        System.out.println("Cannot delete empty directory: " + tempDir.getAbsolutePath());
    }
  }
}
