package com.netease.flatbuf.gradle.plugins

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification

class FlatbufAndroidPluginTest extends Specification {

  def "testProjectAndroid should be successfully executed"() {
    given: "project from testProject, testProjectLite & testProjectAndroid"
    def mainProjectDir = FlatbufPluginTestHelper.prepareTestTempDir('testProjectAndroid')
    FlatbufPluginTestHelper.copyTestProjects(mainProjectDir, 'testProject', 'testProjectLite', 'testProjectAndroid')

    when: "build is invoked"
    def result = GradleRunner.create()
      .withProjectDir(mainProjectDir)
      .withArguments('testProjectAndroid:build')
      .withGradleVersion(gradleVersion)
      .forwardStdOutput(new OutputStreamWriter(System.out))
      .forwardStdError(new OutputStreamWriter(System.err))
      .build()

    then: "it succeed"
    result.task(":testProjectAndroid:build").outcome == TaskOutcome.SUCCESS

    where:
    gradleVersion << ["2.14.1", "3.0"]
  }
}
