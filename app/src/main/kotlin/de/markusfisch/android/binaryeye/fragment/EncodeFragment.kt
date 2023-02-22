package de.markusfisch.android.binaryeye.fragment

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import de.markusfisch.android.binaryeye.R
import de.markusfisch.android.binaryeye.app.addFragment
import de.markusfisch.android.binaryeye.app.prefs
import de.markusfisch.android.binaryeye.view.hideSoftKeyboard
import de.markusfisch.android.binaryeye.view.setPaddingFromWindowInsets
import de.markusfisch.android.binaryeye.widget.toast
import de.markusfisch.android.zxingcpp.ZxingCpp.Format

private fun Format.canBeInverted() = when (this) {
	Format.AZTEC,
	Format.DATA_MATRIX,
	Format.QR_CODE -> true
	else -> false
}

private fun String.toFormat(default: Format = Format.QR_CODE): Format = try {
	Format.valueOf(this)
} catch (_: IllegalArgumentException) {
	default
}
