package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.FileRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("FileVault", appName)
  }

  @Test
  fun `create text file and verify repository persistence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = AppDatabase.getDatabase(context)
    val repository = FileRepository(context, database.fileDao())

    val created = repository.createTextFile(
      title = "TestNote",
      content = "Hello, FileVault unit test!",
      extension = "txt"
    )

    assertNotNull(created)
    assertEquals("TXT", created?.extension)
    assertTrue(File(created!!.internalPath).exists())

    val allFiles = repository.allFiles.first()
    assertTrue(allFiles.any { it.id == created.id })
  }
}
