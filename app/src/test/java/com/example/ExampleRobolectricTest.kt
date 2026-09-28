package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.database.DownloadEntity
import com.example.data.database.TorrentEntity
import com.example.storage.FileCategory
import com.example.storage.StorageManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
    assertEquals("TV File Hub", appName)
  }

  @Test
  fun `test storage formatting`() {
    assertEquals("0 B", StorageManager.formatSize(0))
    assertEquals("1024 B", StorageManager.formatSize(1024))
    assertTrue(StorageManager.formatSize(1024 * 1024).contains("MB"))
    assertTrue(StorageManager.formatSize(1024L * 1024L * 1024L * 5L).contains("GB"))
    assertEquals(FileCategory.VIDEO, StorageManager.determineCategory("movie.mkv"))
    assertEquals(FileCategory.VIDEO, StorageManager.determineCategory("clip.mp4"))
    assertEquals(FileCategory.AUDIO, StorageManager.determineCategory("song.mp3"))
  }

  @Test
  fun `test database download persistence`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val dao = db.downloadDao()

    val download = DownloadEntity(
      id = "test-dl-1",
      url = "https://example.com/video.mkv",
      fileName = "video.mkv",
      status = "QUEUED",
      totalBytes = 1000L
    )

    dao.insertOrUpdate(download)
    val retrieved = dao.getDownloadById("test-dl-1")
    assertNotNull(retrieved)
    assertEquals("video.mkv", retrieved?.fileName)

    dao.updateStatus("test-dl-1", "DOWNLOADING")
    val updated = dao.getDownloadById("test-dl-1")
    assertEquals("DOWNLOADING", updated?.status)

    dao.deleteById("test-dl-1")
    val deleted = dao.getDownloadById("test-dl-1")
    assertEquals(null, deleted)
  }

  @Test
  fun `test database torrent persistence and seeding`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val dao = db.torrentDao()

    val torrent = TorrentEntity(
      id = "hash123",
      name = "Movie 4K",
      magnetUri = "magnet:?xt=urn:btih:hash123&dn=Movie+4K",
      status = "DOWNLOADING",
      progress = 0.5f,
      totalBytes = 2_000_000_000L
    )

    dao.insertOrUpdate(torrent)
    val retrieved = dao.getTorrentById("hash123")
    assertNotNull(retrieved)
    assertEquals("Movie 4K", retrieved?.name)

    // Simulate transition to seeding
    dao.updateStatus("hash123", "SEEDING")
    val seeding = dao.getTorrentById("hash123")
    assertEquals("SEEDING", seeding?.status)

    dao.deleteById("hash123")
  }
}
