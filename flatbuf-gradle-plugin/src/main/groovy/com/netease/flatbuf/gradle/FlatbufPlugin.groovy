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

import com.google.common.collect.ImmutableList

import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.plugins.AppliedPlugin
import org.gradle.api.tasks.SourceSet

import javax.inject.Inject

class FlatbufPlugin implements Plugin<Project> {
    // any one of these plugins should be sufficient to proceed with applying this plugin
    private static final List<String> prerequisitePluginOptions = [
            'java',
            'com.android.application',
            'com.android.library',
            'android',
            'android-library']

    private final FileResolver fileResolver
    private Project project
    private boolean wasApplied = false;

    @Inject
    public FlatbufPlugin(FileResolver fileResolver) {
      this.fileResolver = fileResolver
    }

    void apply(final Project project) {
        println "FlatbufPlugin"
        this.project = project
        // At least one of the prerequisite plugins must by applied before this plugin can be applied, so
        // we will use the PluginManager.withPlugin() callback mechanism to delay applying this plugin until
        // after that has been achieved. If project evaluation completes before one of the prerequisite plugins
        // has been applied then we will assume that none of prerequisite plugins were specified and we will
        // throw an Exception to alert the user of this configuration issue.
        Action<? super AppliedPlugin> applyWithPrerequisitePlugin = { prerequisitePlugin ->
          if (wasApplied) {
            project.logger.warn('The com.netease.flatbuf plugin was already applied to the project: ' + project.path
                + ' and will not be applied again after plugin: ' + prerequisitePlugin.id)

          } else {
            wasApplied = true

            doApply()
          }
        }

        prerequisitePluginOptions.each { pluginName ->
          project.pluginManager.withPlugin(pluginName, applyWithPrerequisitePlugin)
        }

        project.afterEvaluate {
          if (!wasApplied) {
            throw new GradleException('The com.netease.flatbuf plugin could not be applied during project evaluation.'
                + ' The Java plugin or one of the Android plugins must be applied to the project first.')
          }
        }
    }

    private void doApply() {
        // Provides the osdetector extension
        project.apply plugin: 'com.google.osdetector'

        project.convention.plugins.flatbuf = new FlatbufConvention(project, fileResolver);

        addSourceSetExtensions()
        getSourceSets().all { sourceSet ->
          createConfiguration(sourceSet.name)
        }
        project.afterEvaluate {
          // The Android variants are only available at this point.
          addFlatTasks()
          project.flatbuf.runTaskConfigClosures()
          // Disallow user configuration outside the config closures, because
          // next in linkGenerateFlatTasksToJavaCompile() we add generated,
          // outputs to the inputs of javaCompile tasks, and any new codegen
          // plugin output added after this point won't be added to javaCompile
          // tasks.
          project.flatbuf.generateFlatTasks.all()*.doneConfig()
          linkGenerateFlatTasksToJavaCompile()
          // flatc and codegen plugin configuration may change through the flatbuf{}
          // block. Only at this point the configuration has been finalized.
          project.flatbuf.tools.registerTaskDependencies(project.flatbuf.getGenerateFlatTasks().all())
        }
    }

    /**
     * Creates a configuration if necessary for a source set so that the build
     * author can configure dependencies for it.
     */
    private createConfiguration(String sourceSetName) {
      String configName = Utils.getConfigName(sourceSetName, 'flatbuf')
      if (project.configurations.findByName(configName) == null) {
        project.configurations.create(configName) {
          visible = false
          transitive = false
          extendsFrom = []
        }
      }
    }

    /**
     * Adds the flat extension to all SourceSets, e.g., it creates
     * sourceSets.main.flat and sourceSets.test.flat.
     */
    private addSourceSetExtensions() {
      getSourceSets().all {  sourceSet ->
        sourceSet.extensions.create('flat', FlatbufSourceDirectorySet, sourceSet.name, fileResolver)
      }
    }

    /**
     * Returns the sourceSets container of a Java or an Android project.
     */
    private Object getSourceSets() {
      if (Utils.isAndroidProject(project)) {
        return project.android.sourceSets
      } else {
        return project.sourceSets
      }
    }

    private Object getNonTestVariants() {
      return project.android.hasProperty('libraryVariants') ?
          project.android.libraryVariants : project.android.applicationVariants
    }

    /**
     * Adds Flatbuf-related tasks to the project.
     */
    private addFlatTasks() {
      if (Utils.isAndroidProject(project)) {
        getNonTestVariants().each { variant ->
          addTasksForVariant(variant, false)
        }
        project.android.testVariants.each { testVariant ->
          addTasksForVariant(testVariant, true)
        }
      } else {
        getSourceSets().each { sourceSet ->
          addTasksForSourceSet(sourceSet)
        }
      }
    }

    /**
     * Creates Flatbuf tasks for a sourceSet in a Java project.
     */
    private addTasksForSourceSet(final SourceSet sourceSet) {
      def generateFlatTask = addGenerateFlatTask(sourceSet.name, [sourceSet])
      generateFlatTask.sourceSet = sourceSet
      generateFlatTask.doneInitializing()
      generateFlatTask.builtins {
        java {}
      }

      def extractFlatsTask = maybeAddExtractFlatsTask(sourceSet.name)
      generateFlatTask.dependsOn(extractFlatsTask)

      def extractIncludeFlatsTask = maybeAddExtractIncludeFlatsTask(sourceSet.name)
      generateFlatTask.dependsOn(extractIncludeFlatsTask)

      // Include source flat files in the compiled archive, so that flat files from
      // dependent projects can import them.
      def processResourcesTask =
          project.tasks.getByName(sourceSet.getTaskName('process', 'resources'))
      processResourcesTask.from(generateFlatTask.inputs.sourceFiles) {
        include '**/*.fbs'
      }
    }

    /**
     * Creates Flatbuf tasks for a variant in an Android project.
     */
    private addTasksForVariant(final Object variant, final boolean isTestVariant) {
      // The collection of sourceSets that will be compiled for this variant
      def sourceSetNames = new ArrayList()
      def sourceSets = new ArrayList()
      if (isTestVariant) {
        // All test variants will include the androidTest sourceSet
        sourceSetNames.add 'androidTest'
      } else {
        // All non-test variants will include the main sourceSet
        sourceSetNames.add 'main'
      }
      sourceSetNames.add variant.name
      sourceSetNames.add variant.buildType.name
      ImmutableList.Builder<String> flavorListBuilder = ImmutableList.builder()
      if (variant.hasProperty('productFlavors')) {
        variant.productFlavors.each { flavor ->
          sourceSetNames.add flavor.name
          flavorListBuilder.add flavor.name
        }
      }
      sourceSetNames.each { sourceSetName ->
        sourceSets.add project.android.sourceSets.maybeCreate(sourceSetName)
      }

      def generateFlatTask = addGenerateFlatTask(variant.name, sourceSets)
      generateFlatTask.setVariant(variant, isTestVariant)
      generateFlatTask.flavors = flavorListBuilder.build()
      generateFlatTask.buildType = variant.buildType.name
      generateFlatTask.doneInitializing()

      sourceSetNames.each { sourceSetName ->
        def extractFlatsTask = maybeAddExtractFlatsTask(sourceSetName)
        generateFlatTask.dependsOn(extractFlatsTask)

        def extractIncludeFlatsTask = maybeAddExtractIncludeFlatsTask(sourceSetName)
        generateFlatTask.dependsOn(extractIncludeFlatsTask)
      }

      // TODO(zhangkun83): Include source flat files in the compiled archive,
      // so that flat files from dependent projects can import them.
    }

    /**
     * Adds a task to run flatc and compile all flat source files for a sourceSet or variant.
     *
     * @param sourceSetOrVariantName the name of the sourceSet (Java) or
     * variant (Android) that this task will run for.
     *
     * @param sourceSets the sourceSets that contains the flat files to be
     * compiled. For Java it's the sourceSet that sourceSetOrVariantName stands
     * for; for Android it's the collection of sourceSets that the variant includes.
     */
    private Task addGenerateFlatTask(String sourceSetOrVariantName, Collection<Object> sourceSets) {
      def generateFlatTaskName = 'generate' +
          Utils.getSourceSetSubstringForTaskNames(sourceSetOrVariantName) + 'Flat'
      return project.tasks.create(generateFlatTaskName, GenerateFlatTask) {
        description = "Compiles Flat source for '${sourceSetOrVariantName}'"
        outputBaseDir = "${project.flatbuf.generatedFilesBaseDir}/${sourceSetOrVariantName}"
        sourceSets.each { sourceSet ->
          // Include sources
          Utils.addFilesToTaskInputs(project, inputs, sourceSet.flat)
          FlatbufSourceDirectorySet flatSrcDirSet = sourceSet.flat
            flatSrcDirSet.srcDirs.each { srcDir ->
            include srcDir
          }

          // Include extracted sources
          ConfigurableFileTree extractedFlatSources =
              project.fileTree(getExtractedFlatsDir(sourceSet.name)) {
                include "**/*.flat"
              }
          Utils.addFilesToTaskInputs(project, inputs, extractedFlatSources)
          include extractedFlatSources.dir

          // Register extracted include flats
          ConfigurableFileTree extractedIncludeFlatSources =
              project.fileTree(getExtractedIncludeFlatsDir(sourceSet.name)) {
                include "**/*.fbs"
              }
          // Register them as input, but not as "source".
          // Inputs are checked in incremental builds, but only "source" files are compiled.
          inputs.dir extractedIncludeFlatSources
          // Add the extracted include dir to the --flat_path include paths.
          include extractedIncludeFlatSources.dir
        }
      }
    }

    /**
     * Adds a task to extract flats from flatbuf dependencies. They are
     * treated as sources and will be compiled.
     *
     * <p>This task is per-sourceSet, for both Java and Android. In Android a
     * variant may have multiple sourceSets, each of these sourceSets will have
     * its own extraction task.
     */
    private Task maybeAddExtractFlatsTask(String sourceSetName) {
      def extractFlatsTaskName = 'extract' +
          Utils.getSourceSetSubstringForTaskNames(sourceSetName) + 'Flat'
      Task existingTask = project.tasks.findByName(extractFlatsTaskName)
      if (existingTask != null) {
        return existingTask
      }
      return project.tasks.create(extractFlatsTaskName, FlatbufExtract) {
        description = "Extracts flat files/dependencies specified by 'flatbuf' configuration"
        destDir = getExtractedFlatsDir(sourceSetName) as File
        inputs.files project.configurations[Utils.getConfigName(sourceSetName, 'flatbuf')]
      }
    }

    /**
     * Adds a task to extract flats from compile dependencies of a sourceSet,
     * if there isn't one. Those are needed for imports in flat files, but
     * they won't be compiled since they have already been compiled in their
     * own projects or artifacts.
     *
     * <p>This task is per-sourceSet, for both Java and Android. In Android a
     * variant may have multiple sourceSets, each of these sourceSets will have
     * its own extraction task.
     */
    private Task maybeAddExtractIncludeFlatsTask(String sourceSetName) {
      def extractIncludeFlatsTaskName = 'extractInclude' +
          Utils.getSourceSetSubstringForTaskNames(sourceSetName) + 'Flat'
      Task existingTask = project.tasks.findByName(extractIncludeFlatsTaskName)
      if (existingTask != null) {
        return existingTask
      }
      return project.tasks.create(extractIncludeFlatsTaskName, FlatbufExtract) {
        description = "Extracts flat files from compile dependencies for includes"
        destDir = getExtractedIncludeFlatsDir(sourceSetName) as File
        inputs.files project.configurations[Utils.getConfigName(sourceSetName, 'compile')]

        // TL; DR: Make flats in 'test' sourceSet able to import flats from the 'main' sourceSet.
        // Sub-configurations, e.g., 'testCompile' that extends 'compile', don't depend on the
        // their super configurations. As a result, 'testCompile' doesn't depend on 'compile' and
        // it cannot get the flat files from 'main' sourceSet through the configuration. However,
	if (Utils.isAndroidProject(project)) {
          // TODO(zhangkun83): Android sourceSet doesn't have compileClasspath. If it did, we
          // haven't figured out a way to put source flats in 'resources'. For now we use an ad-hoc
          // solution that manually includes the source flats of 'main' and its dependencies.
          if (sourceSetName == 'androidTest') {
            inputs.files getSourceSets()['main'].flat
            inputs.files project.configurations['compile']
          }
        } else {
          // In Java projects, the compileClasspath of the 'test' sourceSet includes all the
          // 'resources' of the output of 'main', in which the source flats are placed.
          // This is nicer than the ad-hoc solution that Android has, because it works for any
          // extended configuration, not just 'testCompile'.
          inputs.files getSourceSets()[sourceSetName].compileClasspath
	}
      }
    }

    private linkGenerateFlatTasksToJavaCompile() {
      if (Utils.isAndroidProject(project)) {
        (getNonTestVariants() + project.android.testVariants).each { variant ->
          project.flatbuf.generateFlatTasks.ofVariant(variant.name).each { generateFlatTask ->
            // This cannot be called once task execution has started
            variant.registerJavaGeneratingTask(generateFlatTask, generateFlatTask.getAllOutputDirs())
          }
        }
      } else {
        project.sourceSets.each { sourceSet ->
          def javaCompileTask = project.tasks.getByName(sourceSet.getCompileTaskName("java"))
          project.flatbuf.generateFlatTasks.ofSourceSet(sourceSet.name).each { generateFlatTask ->
            javaCompileTask.dependsOn(generateFlatTask)
            generateFlatTask.getAllOutputDirs().each { dir ->
              javaCompileTask.source project.fileTree(dir: dir)
            }
          }
        }
      }
    }

    private String getExtractedIncludeFlatsDir(String sourceSetName) {
      return "${project.buildDir}/extracted-include-flats/${sourceSetName}"
    }

    private String getExtractedFlatsDir(String sourceSetName) {
      return "${project.buildDir}/extracted-flats/${sourceSetName}"
    }

}
