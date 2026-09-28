package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.rotator.FailureType
import com.example.data.rotator.LLMRotator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Neox Admin", appName)
  }

  @Test
  fun `rotator classifies quota and dead errors correctly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val rotator = LLMRotator.getInstance(context)

    assertEquals(FailureType.QUOTA_TRANSIENT, rotator.classifyFailure("HTTP 429 Too Many Requests: Rate limit exceeded"))
    assertEquals(FailureType.MODEL_UNAVAILABLE_DEAD, rotator.classifyFailure("HTTP 404: model not found or does not exist"))
    assertEquals(FailureType.QUOTA_TRANSIENT, rotator.classifyFailure("429 and 404 in same string rate limit quota"))
  }

  @Test
  fun `database instance initializes and executes query without schema exception`() = kotlinx.coroutines.test.runTest {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.db.AppDatabase.getInstance(context)
    val jobCount = db.jobProjectDao().getJobCount()
    assertTrue(jobCount >= 0)
  }
}
