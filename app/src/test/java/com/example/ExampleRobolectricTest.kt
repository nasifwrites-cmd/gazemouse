package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.models.CalibrationMatrix
import com.example.data.models.CalibrationPoint
import com.example.vision.GazeCalibrationEngine
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
    assertEquals("GazeMouse", appName)
  }

  @Test
  fun `test calibration matrix mapping to screen`() {
    val matrix = CalibrationMatrix(
      minPupilX = 0.2f,
      maxPupilX = 0.8f,
      minPupilY = 0.2f,
      maxPupilY = 0.8f,
      isCalibrated = true
    )
    val (screenX, screenY) = matrix.mapToScreen(
      rawPupilX = 0.5f,
      rawPupilY = 0.5f,
      screenWidth = 1000f,
      screenHeight = 2000f
    )
    assertTrue("Screen X should be within bounds", screenX in 0f..1000f)
    assertTrue("Screen Y should be within bounds", screenY in 0f..2000f)
  }

  @Test
  fun `test serialization of calibration matrix and points`() {
    val matrix = CalibrationMatrix(a0 = 0.5f, a1 = 1.2f, isCalibrated = true)
    val json = GazeCalibrationEngine.matrixToJson(matrix)
    val restored = GazeCalibrationEngine.jsonToMatrix(json)
    assertEquals(matrix.a0, restored.a0, 0.001f)
    assertEquals(matrix.a1, restored.a1, 0.001f)
    assertTrue(restored.isCalibrated)

    val points = listOf(
      CalibrationPoint(0, 0.15f, 0.15f, 0.35f, 0.35f)
    )
    val pointsJson = GazeCalibrationEngine.pointsToJson(points)
    assertNotNull(pointsJson)
    assertTrue(pointsJson.contains("0.15"))
  }
}
