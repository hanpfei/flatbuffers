/*
 * Original work copyright (c) 2015, Alex Antonov. All rights reserved.
 * Modified work copyright (c) 2015, Google Inc. All rights reserved.
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

import com.google.common.base.Preconditions
import com.google.common.collect.ImmutableList

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskAction
import org.gradle.util.ConfigureUtil

/**
 * The task that compiles flat files into Java files.
 */
// TODO(zhangkun83): add per-plugin output dir reconfiguraiton.
public class GenerateFlatTask extends DefaultTask {

  private final List includeDirs = new ArrayList()
  private final NamedDomainObjectContainer<PluginOptions> builtins
  private final NamedDomainObjectContainer<PluginOptions> plugins

  // These fields are set by the Protobuf plugin only when initializing the
  // task.  Ideally they should be final fields, but Gradle task cannot have
  // constructor arguments. We use the initializing flag to prevent users from
  // accidentally modifying them.
  private String outputBaseDir
  // Tags for selectors inside flatbuf.generateProtoTasks
  private SourceSet sourceSet
  private Object variant
  private ImmutableList<String> flavors
  private String buildType
  private boolean isTestVariant

  /**
   * If true, will set the flatc flag
   * --descriptor_set_out="${outputBaseDir}/descriptor_set.desc"
   *
   * Default: false
   */
  public boolean generateDescriptorSet

  /**
   * Configuration object for descriptor generation details.
   */
  public class DescriptorSetOptions {
    /**
     * If set, specifies an alternative location than the default for storing the descriptor
     * set.
     *
     * Default: null
     */
    public GString path

    /**
     * If true, source information (comments, locations) will be included in the descriptor set.
     *
     * Default: false
     */
    public boolean includeSourceInfo

    /**
     * If true, imports are included in the descriptor set, such that it is self-containing.
     *
     * Default: false
     */
    public boolean includeImports
  }

  public final DescriptorSetOptions descriptorSetOptions = new DescriptorSetOptions();

  private static enum State {
    INIT, CONFIG, FINALIZED
  }

  private State state = State.INIT


  private void checkInitializing() {
    Preconditions.checkState(state == State.INIT, 'Should not be called after initilization has finished')
  }

  private void checkCanConfig() {
    Preconditions.checkState(state == State.CONFIG || state == State.INIT,
        'Should not be called after configuration has finished')
  }

  void setOutputBaseDir(String outputBaseDir) {
    checkInitializing()
    Preconditions.checkState(this.outputBaseDir == null, 'outputBaseDir is already set')
    this.outputBaseDir = outputBaseDir
    outputs.dir outputBaseDir
  }

  void setSourceSet(SourceSet sourceSet) {
    checkInitializing()
    Preconditions.checkState(!Utils.isAndroidProject(project),
        'sourceSet should not be set in an Android project')
    this.sourceSet = sourceSet
  }

  void setVariant(Object variant, boolean isTestVariant) {
    checkInitializing()
    Preconditions.checkState(Utils.isAndroidProject(project),
        'variant should not be set in a Java project')
    this.variant = variant
    this.isTestVariant = isTestVariant
  }

  void setFlavors(ImmutableList<String> flavors) {
    checkInitializing()
    Preconditions.checkState(Utils.isAndroidProject(project),
        'flavors should not be set in a Java project')
    this.flavors = flavors
  }

  void setBuildType(String buildType) {
    checkInitializing()
    Preconditions.checkState(Utils.isAndroidProject(project),
        'buildType should not be set in a Java project')
    this.buildType = buildType
  }

  SourceSet getSourceSet() {
    Preconditions.checkState(!Utils.isAndroidProject(project),
        'sourceSet should not be used in an Android project')
    Preconditions.checkNotNull(sourceSet, 'sourceSet is not set')
    return sourceSet
  }

  Object getVariant() {
    Preconditions.checkState(Utils.isAndroidProject(project),
        'variant should not be used in a Java project')
    Preconditions.checkNotNull(variant, 'variant is not set')
    return variant
  }

  boolean getIsTestVariant() {
    Preconditions.checkState(Utils.isAndroidProject(project),
        'isTestVariant should not be used in a Java project')
    Preconditions.checkNotNull(variant, 'variant is not set')
    return isTestVariant
  }

  ImmutableList<String> getFlavors() {
    Preconditions.checkState(Utils.isAndroidProject(project),
        'flavors should not be used in a Java project')
    Preconditions.checkNotNull(flavors, 'flavors is not set')
    return flavors
  }

  String getBuildType() {
    Preconditions.checkState(Utils.isAndroidProject(project),
        'buildType should not be used in a Java project')
    Preconditions.checkNotNull(buildType, 'buildType is not set')
    return buildType
  }

  void doneInitializing() {
    Preconditions.checkState(state == State.INIT, "Invalid state: ${state}")
    state = State.CONFIG
  }

  void doneConfig() {
    Preconditions.checkState(state == State.CONFIG, "Invalid state: ${state}")
    state = State.FINALIZED
  }

  String getDescriptorPath() {
    if (!generateDescriptorSet) {
      throw new IllegalStateException(
          "requested descriptor path but descriptor generation is off")
    }
    return descriptorSetOptions.path != null
      ? descriptorSetOptions.path : "${outputBaseDir}/descriptor_set.desc"
  }

  public GenerateFlatTask() {
    builtins = project.container(PluginOptions)
    plugins = project.container(PluginOptions)
  }

  //===========================================================================
  //        Configuration methods
  //===========================================================================

  /**
   * Configures the flatc builtins in a closure, which will be maniuplating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  public void builtins(Closure configureClosure) {
    checkCanConfig()
    ConfigureUtil.configure(configureClosure, builtins)
  }

  /**
   * Returns the container of flatc builtins.
   */
  public NamedDomainObjectContainer<PluginOptions> getBuiltins() {
    checkCanConfig()
    return builtins
  }

  /**
   * Configures the flatc plugins in a closure, which will be maniuplating a
   * NamedDomainObjectContainer<PluginOptions>.
   */
  public void plugins(Closure configureClosure) {
    checkCanConfig()
    ConfigureUtil.configure(configureClosure, plugins)
  }

  /**
   * Returns the container of flatc plugins.
   */
  public NamedDomainObjectContainer<PluginOptions> getPlugins() {
    checkCanConfig()
    return plugins
  }

  /**
   * Returns true if the task has a plugin with the given name, false otherwise.
   */
  public boolean hasPlugin(String name) {
    return plugins.findByName(name) != null
  }

  /**
   * Add a directory to flatc's include path.
   */
  public void include(Object dir) {
    checkCanConfig()
    if (dir instanceof File) {
      includeDirs.add(dir)
    } else {
      includeDirs.add(project.file(dir))
    }
  }

  /**
   * The container of command-line options for a flatc plugin or a built-in output.
   */
  public static class PluginOptions implements Named {
    private final ArrayList<String> options = new ArrayList<String>()
    private final String name
    private String outputSubDir

    public PluginOptions(String name) {
      this.name = name
    }

    /**
     * Adds a plugin option.
     */
    public PluginOptions option(String option) {
      options.add(option)
      return this
    }

    public List<String> getOptions() {
      return options
    }

    /**
     * Returns the name of the plugin or builtin.
     */
    @Override
    public String getName() {
      return name
    }

    /**
     * Set the output directory for this plugin, relative to {@link GenerateFlatTask#outputBaseDir}.
     */
    public setOutputSubDir(String outputSubDir) {
      this.outputSubDir = outputSubDir
    }

    /**
     * Returns the relative outputDir for this plugin.  If outputDir is not specified, name is used.
     */
    public String getOutputSubDir() {
      if (outputSubDir != null) {
        return outputSubDir;
      }
      return name;
    }
  }

  //===========================================================================
  //    flatc invocation logic
  //===========================================================================

  String getOutputDir(PluginOptions plugin) {
    return "${outputBaseDir}/${plugin.outputSubDir}"
  }

  Collection<File> getAllOutputDirs() {
    ImmutableList.Builder<File> dirs = ImmutableList.builder()
    builtins.each { builtin ->
      dirs.add(new File(getOutputDir(builtin)))
    }
    plugins.each { plugin ->
      dirs.add(new File(getOutputDir(plugin)))
    }
    return dirs.build()
  }

  @TaskAction
  def compile() {
    Preconditions.checkState(state == State.FINALIZED, 'doneConfig() has not been called')

    ToolsLocator tools = project.flatbuf.tools
    Set<File> flatFiles = inputs.sourceFiles.files

    def cmd = [ tools.flatc.path ]

    if (!includeDirs.isEmpty()) {
      includeDirs.each { includeDir ->
        if (includeDir.length() > 0) {
          cmd.add("-I")
          cmd.add(includeDir)
        }
      }
    }

    // Handle code generation built-ins
    builtins.each { builtin ->
      cmd += "--${builtin.name}"
    }

    cmd += "-o"
    cmd += "${outputBaseDir}/java"

    cmd.addAll flatFiles
    logger.log(LogLevel.INFO, cmd.toString())
    def stdout = new StringBuffer()
    def stderr = new StringBuffer()
    Process result = cmd.execute()
    result.waitForProcessOutput(stdout, stderr)
    def output = "cmd: ${cmd}, flatc: stdout: ${stdout}. stderr: ${stderr}"

    if (result.exitValue() == 0) {
      logger.log(LogLevel.INFO, output)
    } else {
      throw new GradleException(output)
    }
  }

}
