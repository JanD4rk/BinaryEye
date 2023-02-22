package de.markusfisch.android.binaryeye.fragment

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.*
import android.widget.EditText
import de.markusfisch.android.binaryeye.R
import de.markusfisch.android.binaryeye.app.hasWritePermission
import de.markusfisch.android.binaryeye.content.copyToClipboard
import de.markusfisch.android.binaryeye.content.shareFile
import de.markusfisch.android.binaryeye.content.shareText
import de.markusfisch.android.binaryeye.graphics.COLOR_BLACK
import de.markusfisch.android.binaryeye.graphics.COLOR_WHITE
import de.markusfisch.android.binaryeye.io.addSuffixIfNotGiven
import de.markusfisch.android.binaryeye.io.toSaveResult
import de.markusfisch.android.binaryeye.io.writeExternalFile
import de.markusfisch.android.binaryeye.view.doOnApplyWindowInsets
import de.markusfisch.android.binaryeye.view.setPaddingFromWindowInsets
import de.markusfisch.android.binaryeye.widget.ConfinedScalingImageView
import de.markusfisch.android.binaryeye.widget.toast
import de.markusfisch.android.zxingcpp.ZxingCpp
import de.markusfisch.android.zxingcpp.ZxingCpp.Format
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.*
import kotlin.math.min

private data class Barcode(
	val content: String,
	val format: Format,
	val size: Int,
	val ecLevel: Int,
	val colors: Colors
) {
	private var _bitmap: Bitmap? = null
	fun bitmap(): Bitmap {
		val b = _bitmap ?: ZxingCpp.encodeAsBitmap(
			content, format, size, size, -1, ecLevel,
			setColor = colors.foregroundColor(),
			unsetColor = colors.backgroundColor()
		)
		_bitmap = b
		return b
	}

	private var _svg: String? = null
	fun svg(): String {
		val s = _svg ?: ZxingCpp.encodeAsSvg(
			content, format, -1, ecLevel
		)
		_svg = s
		return s
	}

	private var _text: String? = null
	fun text(): String {
		val t = _text ?: ZxingCpp.encodeAsText(
			content, format, -1, ecLevel,
			inverted = colors == Colors.BLACK_ON_WHITE
		)
		_text = t
		return t
	}
}

private enum class Colors {
	BLACK_ON_WHITE,
	WHITE_ON_BLACK,
	BLACK_ON_TRANSPARENT,
	WHITE_ON_TRANSPARENT;

	fun foregroundColor(): Int = when (this) {
		BLACK_ON_WHITE,
		BLACK_ON_TRANSPARENT -> COLOR_BLACK
		WHITE_ON_BLACK,
		WHITE_ON_TRANSPARENT -> COLOR_WHITE
	}

	fun backgroundColor(): Int = when (this) {
		BLACK_ON_WHITE -> COLOR_WHITE
		WHITE_ON_BLACK -> COLOR_BLACK
		BLACK_ON_TRANSPARENT,
		WHITE_ON_TRANSPARENT -> 0
	}
}

private fun Bitmap.saveAsPng(outputStream: OutputStream, quality: Int = 90) =
	compress(Bitmap.CompressFormat.PNG, quality, outputStream)

private val fileNameCharacters = "[^A-Za-z0-9]".toRegex()
private fun encodeFileName(name: String): String =
	fileNameCharacters.replace(name, "_").take(16).trim('_').lowercase(Locale.getDefault())
