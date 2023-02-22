package de.markusfisch.android.binaryeye.activity

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import de.markusfisch.android.binaryeye.R
import de.markusfisch.android.binaryeye.app.applyLocale
import de.markusfisch.android.binaryeye.app.prefs
import de.markusfisch.android.binaryeye.graphics.crop
import de.markusfisch.android.binaryeye.graphics.fixTransparency
import de.markusfisch.android.binaryeye.graphics.loadImageUri
import de.markusfisch.android.binaryeye.graphics.mapPosition
import de.markusfisch.android.binaryeye.media.releaseToneGenerators
import de.markusfisch.android.binaryeye.view.*
import de.markusfisch.android.binaryeye.widget.CropImageView
import de.markusfisch.android.binaryeye.widget.DetectorView
import de.markusfisch.android.binaryeye.widget.toast
import de.markusfisch.android.zxingcpp.ZxingCpp
import de.markusfisch.android.zxingcpp.ZxingCpp.Binarizer
import de.markusfisch.android.zxingcpp.ZxingCpp.DecodeHints
import de.markusfisch.android.zxingcpp.ZxingCpp.Result
import kotlinx.coroutines.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun getNormalizedRoi(imageRect: RectF, roi: Rect): RectF {
	val w = imageRect.width()
	val h = imageRect.height()
	return RectF(
		(roi.left - imageRect.left) / w,
		(roi.top - imageRect.top) / h,
		(roi.right - imageRect.left) / w,
		(roi.bottom - imageRect.top) / h
	)
}
