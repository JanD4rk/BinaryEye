package de.markusfisch.android.binaryeye.fragment

import android.os.Build
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.Fragment
import android.text.Editable
import android.text.Html
import android.text.TextWatcher
import android.text.method.LinkMovementMethod
import android.view.*
import android.widget.*
import de.markusfisch.android.binaryeye.R
import de.markusfisch.android.binaryeye.actions.ActionRegistry
import de.markusfisch.android.binaryeye.actions.IAction
import de.markusfisch.android.binaryeye.actions.wifi.WifiAction
import de.markusfisch.android.binaryeye.actions.wifi.WifiConnector
import de.markusfisch.android.binaryeye.app.*
import de.markusfisch.android.binaryeye.content.copyToClipboard
import de.markusfisch.android.binaryeye.content.shareText
import de.markusfisch.android.binaryeye.content.toHexString
import de.markusfisch.android.binaryeye.database.Recreation
import de.markusfisch.android.binaryeye.database.Scan
import de.markusfisch.android.binaryeye.database.toRecreation
import de.markusfisch.android.binaryeye.io.askForFileName
import de.markusfisch.android.binaryeye.io.toSaveResult
import de.markusfisch.android.binaryeye.io.writeExternalFile
import de.markusfisch.android.binaryeye.view.setPaddingFromWindowInsets
import de.markusfisch.android.binaryeye.widget.toast
import kotlinx.coroutines.*
import kotlin.math.roundToInt

private inline fun <T : View> T.showIf(
	visible: Boolean,
	block: (T) -> Unit
) {
	visibility = if (visible) {
		block.invoke(this)
		View.VISIBLE
	} else {
		View.GONE
	}
}

private val nonAlNum = "[^a-zA-Z0-9]".toRegex()
private val multipleDots = "[…]+".toRegex()
private fun String.foldNonAlNum() = replace(nonAlNum, "…")
	.replace(multipleDots, "…")

private fun hexDump(bytes: ByteArray, charsPerLine: Int = 33): String {
	if (charsPerLine < 4 || bytes.isEmpty()) {
		return ""
	}
	val dump = StringBuilder()
	val hex = StringBuilder()
	val ascii = StringBuilder()
	val itemsPerLine = (charsPerLine - 1) / 4
	val len = bytes.size
	var i = 0
	while (true) {
		val ord = bytes[i]
		hex.append(String.format("%02X ", ord))
		ascii.append(if (ord > 31) ord.toInt().toChar() else " ")
		++i
		val posInLine = i % itemsPerLine
		val atEnd = i >= len
		var atLineEnd = posInLine == 0
		if (atEnd && !atLineEnd) {
			for (j in posInLine until itemsPerLine) {
				hex.append("   ")
			}
			atLineEnd = true
		}
		if (atLineEnd) {
			dump.append(hex.toString())
			dump.append(" ")
			dump.append(ascii.toString())
			dump.append("\n")
			hex.setLength(0)
			ascii.setLength(0)
		}
		if (atEnd) {
			break
		}
	}
	return dump.toString()
}

private fun Int.positiveToString() = if (this > -1) this.toString() else ""

private fun TextView.setTrackingLink(bytes: ByteArray, format: String) {
	val trackingLink = generateDpTrackingLink(bytes, format)
	if (trackingLink != null) {
		text = trackingLink.fromHtml()
		isClickable = true
		movementMethod = LinkMovementMethod.getInstance()
	} else {
		visibility = View.GONE
	}
}

private fun generateDpTrackingLink(bytes: ByteArray, format: String): String? {
	// Check for Deutsche Post Matrixcode stamp.
	var isStamp = false
	var rawData = bytes
	if (format == "DATA_MATRIX" &&
		bytes.toString(Charsets.ISO_8859_1).startsWith("DEA5")
	) {
		if (bytes.size == 47) {
			isStamp = true
		} else if (bytes.size > 47) {
			// Transform back to original data.
			rawData = bytes.toString(Charsets.UTF_8).toByteArray(
				Charsets.ISO_8859_1
			)
			if (rawData.size == 47) {
				isStamp = true
			}
		}
	}

	if (!isStamp) {
		return null
	}

	val hex = StringBuilder()
	hex.append(String.format("%02X", rawData[9]))
	hex.append(String.format("%02X", rawData[10]))
	hex.append(String.format("%02X", rawData[11]))
	hex.append(String.format("%02X", rawData[12]))
	hex.append(String.format("%02X", rawData[13]))
	hex.append(String.format("%X", (rawData[4].toInt() and 0x0f).toByte()))
	hex.append(String.format("%02X", rawData[5]))
	hex.append(String.format("%02X", rawData[6]))
	hex.append(String.format("%02X", rawData[7]))
	hex.append(String.format("%02X", rawData[8]))
	val hexString = hex.toString()
	val trackingNumber = hexString + String.format(
		"%X",
		crc4(hexString.toByteArray(Charsets.ISO_8859_1))
	)
	return "<a href=\"https://www.deutschepost.de/de/s/sendungsverfolgung/verfolgen.html?piececode=$trackingNumber\">Deutsche Post: $trackingNumber</a>"
}

// CRC-4 with polynomial x^4 + x + 1.
private fun crc4(input: ByteArray): Int {
	var crc = 0
	var i = 0
	while (i < input.size) {
		val c = input[i].toInt()
		var j = 0x80
		while (j != 0) {
			var bit = crc and 0x8
			crc = crc shl 1
			if (c and j != 0) {
				bit = bit xor 0x8
			}
			if (bit != 0) {
				crc = crc xor 0x3
			}
			j = j ushr 1
		}
		++i
	}
	crc = crc and 0xF
	return crc
}

private fun String.fromHtml() = if (
	Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
) {
	Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY)
} else {
	@Suppress("DEPRECATION")
	Html.fromHtml(this)
}
