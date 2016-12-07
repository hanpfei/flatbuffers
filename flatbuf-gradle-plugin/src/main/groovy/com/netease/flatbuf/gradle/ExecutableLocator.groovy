/*
 * Copyright (c) 2015, Google Inc. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.netease.flatbuf.gradle

import org.gradle.api.Named

/**
 * Locates an executable that can either be found locally or downloaded from
 * repositories.  If configured multiple times, the last call wins.  If never
 * configured, the plugin should try to run the executable from system search
 * path.
 */
public class ExecutableLocator implements Named {

  private final String name

  private String artifact
  private String path

  public ExecutableLocator(String name) {
    this.name = name
  }

  @Override
  public String getName() {
    return name
  }

  /**
   * Specifies an artifact spec for downloading the executable from
   * repositories. spec format: '<groupId>:<artifactId>:<version>'
   */
  public setArtifact(String spec) {
    this.artifact = spec
    this.path = null
  }

  /**
   * Specifies a local path.
   */
  public setPath(String path) {
    this.path = path
    this.artifact = null
  }

  public String getArtifact() {
    return artifact
  }

  public String getPath() {
    return path
  }
}
