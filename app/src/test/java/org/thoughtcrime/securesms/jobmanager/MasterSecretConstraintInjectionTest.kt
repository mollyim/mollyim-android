/*
 * Copyright 2026 Molly Instant Messenger
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.thoughtcrime.securesms.jobmanager

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import org.junit.Test
import org.thoughtcrime.securesms.jobmanager.impl.MasterSecretConstraint

class MasterSecretConstraintInjectionTest {

  @Test
  fun `every job gains the master secret constraint in its parameters`() {
    val job = TestJob(Job.Parameters.Builder().build())

    assertThat(job.parameters.constraintKeys).contains(MasterSecretConstraint.KEY)
  }

  @Test
  fun `jobs with their own constraints keep them and gain the master secret constraint`() {
    val job = TestJob(
      Job.Parameters.Builder()
        .addConstraint("SomeOtherConstraint")
        .build()
    )

    assertThat(job.parameters.constraintKeys).containsExactly("SomeOtherConstraint", MasterSecretConstraint.KEY)
  }

  @Test
  fun `a job restored from persisted parameters keeps its id and gains the master secret constraint`() {
    // Mirrors JobController.buildJobParameters -> factory -> Job constructor: the id and create time
    // of an already-persisted job must survive the injection.
    val parameters = Job.Parameters.Builder("persisted-id")
      .setCreateTime(123L)
      .addConstraint("NetworkConstraint")
      .build()

    val job = TestJob(parameters)

    assertThat(job.parameters.id).isEqualTo("persisted-id")
    assertThat(job.parameters.createTime).isEqualTo(123L)
    assertThat(job.parameters.constraintKeys).containsExactly("NetworkConstraint", MasterSecretConstraint.KEY)
  }

  @Test
  fun `parameters that already carry the master secret constraint are not duplicated`() {
    val parameters = Job.Parameters.Builder()
      .addConstraint(MasterSecretConstraint.KEY)
      .build()

    assertThat(parameters.withConstraint(MasterSecretConstraint.KEY)).isEqualTo(parameters)
    assertThat(parameters.constraintKeys).containsExactly(MasterSecretConstraint.KEY)
  }

  private class TestJob(parameters: Parameters) : Job(parameters) {
    override fun serialize(): ByteArray? = null
    override fun getFactoryKey(): String = "MasterSecretConstraintInjectionTest"
    override fun run(): Result = Result.success()
    override fun onFailure() = Unit
  }
}
