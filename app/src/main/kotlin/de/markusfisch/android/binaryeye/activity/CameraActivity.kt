package de.markusfisch.android.binaryeye.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Matrix
import android.graphics.Rect
import android.hardware.Camera
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.MotionEvent
import android.view.View
import de.markusfisch.android.binaryeye.R
import de.markusfisch.android.binaryeye.app.*
import de.markusfisch.android.binaryeye.content.openUrl
import de.markusfisch.android.binaryeye.database.toScan
import de.markusfisch.android.binaryeye.graphics.FrameMetrics
import de.markusfisch.android.binaryeye.graphics.mapPosition
import de.markusfisch.android.binaryeye.graphics.setFrameRoi
import de.markusfisch.android.binaryeye.graphics.setFrameToView
import de.markusfisch.android.binaryeye.media.releaseToneGenerators
import de.markusfisch.android.binaryeye.net.sendAsync
import de.markusfisch.android.binaryeye.net.urlEncode
import de.markusfisch.android.binaryeye.view.errorFeedback
import de.markusfisch.android.binaryeye.view.initSystemBars
import de.markusfisch.android.binaryeye.view.scanFeedback
import de.markusfisch.android.binaryeye.view.setPaddingFromWindowInsets
import de.markusfisch.android.binaryeye.widget.DetectorView
import de.markusfisch.android.binaryeye.widget.toast
import de.markusfisch.android.cameraview.widget.CameraView
import de.markusfisch.android.zxingcpp.ZxingCpp
import de.markusfisch.android.zxingcpp.ZxingCpp.Binarizer
import de.markusfisch.android.zxingcpp.ZxingCpp.DecodeHints
import de.markusfisch.android.zxingcpp.ZxingCpp.Result
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CameraActivity : AppCompatActivity() {
	private val frameRoi = Rect()
	private val matrix = Matrix()

	private lateinit var cameraView: CameraView
	private lateinit var detectorView: DetectorView
	private lateinit var flashFab: FloatingActionButton

	private var formatsToRead = setOf<String>()
	private var frameMetrics = FrameMetrics()
	private var decoding = true
	private var returnResult = true
	private var finishAfterShowingResult = false
	private var frontFacing = false
	private var restrictFormat: String? = ZxingCpp.Format.QR_CODE.name
	private var ignoreNext: String? = null
	private var fallbackBuffer: IntArray? = null

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<String>,
		grantResults: IntArray
	) {
		when (requestCode) {
			PERMISSION_CAMERA -> if (grantResults.isNotEmpty() &&
				grantResults[0] != PackageManager.PERMISSION_GRANTED
			) {
				toast(R.string.no_camera_no_fun)
				finish()
			}
		}
	}

	override fun onCreate(state: Bundle?) {
		super.onCreate(state)
		setContentView(R.layout.activity_camera)

		// Necessary to get the right translation after setting a
		// custom locale.
		setTitle(R.string.scan_code)

		initSystemBars(this)
		setSupportActionBar(findViewById(R.id.toolbar) as Toolbar)

		cameraView = findViewById(R.id.camera_view) as CameraView
		detectorView = findViewById(R.id.detector_view) as DetectorView
		flashFab = findViewById(R.id.flash) as FloatingActionButton

		initCameraView()
		initDetectorView()


	}

	override fun onDestroy() {
		super.onDestroy()
		fallbackBuffer = null
		detectorView.saveCropHandlePos()
		releaseToneGenerators()
	}

	override fun onResume() {
		super.onResume()
		System.gc()
		updateHints()
		if (hasCameraPermission()) {
			openCamera()
		}
	}

	private fun updateHints() {
		val restriction = restrictFormat
		formatsToRead= setOf(restriction!!)
		setTitle(R.string.scan_code)
	}



	private fun openCamera() {
		cameraView.openAsync(
			CameraView.findCameraId(
				@Suppress("DEPRECATION")
				if (frontFacing) {
					Camera.CameraInfo.CAMERA_FACING_FRONT
				} else {
					Camera.CameraInfo.CAMERA_FACING_BACK
				}
			)
		)
	}

	override fun onPause() {
		super.onPause()
		closeCamera()
	}

	private fun closeCamera() {
		cameraView.close()
	}


	override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
		// Always give crop handle precedence over other controls
		// because it can easily overlap and would then be inaccessible.
		if (detectorView.onTouchEvent(ev)) {
			return true
		}
		return super.dispatchTouchEvent(ev)
	}

	private fun showRestrictionDialog() {
		val names = resources.getStringArray(
			R.array.barcode_formats_names
		).toMutableList()
		val formats = resources.getStringArray(
			R.array.barcode_formats_values
		).toMutableList()
		if (restrictFormat != null) {
			names.add(0, getString(R.string.remove_restriction))
			formats.add(0, null)
		}
		AlertDialog.Builder(this).apply {
			setTitle(R.string.restrict_format)
			setItems(names.toTypedArray()) { _, which ->
				restrictFormat = formats[which]
				updateHints()
			}
			show()
		}
	}

	private fun initCameraView() {
		cameraView.setUseOrientationListener(true)
		@Suppress("ClickableViewAccessibility")
		cameraView.setOnTouchListener(object : View.OnTouchListener {
			var focus = true
			var offset = -1f

			override fun onTouch(v: View?, event: MotionEvent?): Boolean {
				event ?: return false
				val pos = event.y
				when (event.actionMasked) {
					MotionEvent.ACTION_DOWN -> {
						offset = pos
						return true
					}
					MotionEvent.ACTION_MOVE -> {
						if (prefs.zoomBySwiping) {
							v ?: return false
							return true
						}
					}
					MotionEvent.ACTION_UP -> {
						if (focus) {
							focus = cameraView.focusTo(v, event.x, event.y)
							if (focus) {
								v?.performClick()
								return true
							}
						}
					}
				}
				return false
			}
		})
		@Suppress("DEPRECATION")
		cameraView.setOnCameraListener(object : CameraView.OnCameraListener {
			override fun onConfigureParameters(
				parameters: Camera.Parameters
			) {
				val sceneModes = parameters.supportedSceneModes
				sceneModes?.let {
					for (mode in sceneModes) {
						if (mode == Camera.Parameters.SCENE_MODE_BARCODE) {
							parameters.sceneMode = mode
							break
						}
					}
				}
				CameraView.setAutoFocus(parameters)
				flashFab.setImageResource(R.drawable.ic_action_flash)
				flashFab.setOnClickListener { toggleTorchMode() }
			}

			override fun onCameraError() {
				this@CameraActivity.toast(R.string.camera_error)
				finish()
			}

			override fun onCameraReady(camera: Camera) {
				frameMetrics = FrameMetrics(
					cameraView.frameWidth,
					cameraView.frameHeight,
					cameraView.frameOrientation
				)
				updateFrameRoiAndMappingMatrix()
				ignoreNext = null
				decoding = true
				// These settings can't change while the camera is open.
				val decodeHints = DecodeHints(
					tryHarder = prefs.tryHarder,
					tryRotate = prefs.autoRotate,
					tryInvert = true,
					tryDownscale = true
				)
				var useLocalAverage = false
				camera.setPreviewCallback { frameData, _ ->
					if (decoding) {
						useLocalAverage = useLocalAverage xor true
						ZxingCpp.readByteArray(
							frameData,
							frameMetrics.width,
							frameRoi.left, frameRoi.top,
							frameRoi.width(), frameRoi.height(),
							frameMetrics.orientation,
							decodeHints.apply {
								// By default, ZXing uses LOCAL_AVERAGE, but
								// this does not work well with inverted
								// barcodes on low-contrast backgrounds.
								binarizer = if (useLocalAverage) {
									Binarizer.LOCAL_AVERAGE
								} else {
									Binarizer.GLOBAL_HISTOGRAM
								}
								formats = formatsToRead.joinToString()
							}
						)?.let { result ->
							if (result.text != ignoreNext) {
								postResult(result)
								decoding = false
							}
						}
					}
				}
			}

			override fun onPreviewStarted(camera: Camera) {
			}

			override fun onCameraStopping(camera: Camera) {
				camera.setPreviewCallback(null)
			}
		})
	}

	private fun initDetectorView() {
		detectorView.onRoiChange = {
			decoding = false
		}
		detectorView.onRoiChanged = {
			decoding = true
			updateFrameRoiAndMappingMatrix()
		}
		detectorView.setPaddingFromWindowInsets()
		detectorView.restoreCropHandlePos()
	}

	private fun updateFrameRoiAndMappingMatrix() {
		val viewRect = cameraView.previewRect
		val viewRoi = if (detectorView.roi.width() < 1) {
			viewRect
		} else {
			detectorView.roi
		}
		frameRoi.setFrameRoi(frameMetrics, viewRect, viewRoi)
		matrix.setFrameToView(frameMetrics, viewRect, viewRoi)
	}



	@Suppress("DEPRECATION")
	private fun toggleTorchMode() {
		val camera = cameraView.camera ?: return
		val parameters = camera.parameters ?: return
		parameters.flashMode = if (
			parameters.flashMode != Camera.Parameters.FLASH_MODE_OFF
		) {
			Camera.Parameters.FLASH_MODE_OFF
		} else {
			Camera.Parameters.FLASH_MODE_TORCH
		}
		try {
			camera.parameters = parameters
		} catch (e: RuntimeException) {
			toast(e.message ?: getString(R.string.error_flash))
		}
	}

	private fun postResult(result: Result) {
		cameraView.post {
			detectorView.update(
				matrix.mapPosition(
					result.position,
					detectorView.coordinates
				)
			)
			scanFeedback()
			when {
				returnResult -> {
					setResult(Activity.RESULT_OK, getReturnIntent(result))
					finish()
				}
				else -> {
					showResult(
						this@CameraActivity,
						result,
						false
					)
					// If this app was invoked via a deep link but without
					// a return URI, we probably don't want to return to
					// the camera screen after scanning, but to the caller.
					if (finishAfterShowingResult) {
						finish()
					}
				}
			}
		}
	}

	companion object {
		private const val ZOOM_MAX = "zoom_max"
		private const val ZOOM_LEVEL = "zoom_level"
		private const val FRONT_FACING = "front_facing"
		private const val RESTRICT_FORMAT = "restrict_format"
	}
}

fun showResult(
	activity: Activity,
	result: Result,
	bulkMode: Boolean = false,
) {

	val scan = result.toScan()

	if (prefs.sendScanActive && prefs.sendScanUrl.isNotEmpty()) {
		if (prefs.sendScanType == "4") {
			activity.openUrl(
				prefs.sendScanUrl + scan.content.urlEncode()
			)
			return
		}
		scan.sendAsync(
			prefs.sendScanUrl,
			prefs.sendScanType
		) { code, body ->
			if (code == null || code < 200 || code > 299) {
				activity.errorFeedback()
			}
			if (body != null && body.isNotEmpty()) {
				activity.toast(body)
			} else if (code == null || code > 299) {
				activity.toast(R.string.background_request_failed)
			}
		}
	}

}

private fun getReturnIntent(result: Result) = Intent().apply {
	putExtra("SCAN_RESULT", result.text)
	putExtra("SCAN_RESULT_FORMAT", result.format)
	putExtra("SCAN_RESULT_ORIENTATION", result.orientation)
	putExtra("SCAN_RESULT_ERROR_CORRECTION_LEVEL", result.ecLevel)
	if (result.rawBytes.isNotEmpty()) {
		putExtra("SCAN_RESULT_BYTES", result.rawBytes)
	}
}



