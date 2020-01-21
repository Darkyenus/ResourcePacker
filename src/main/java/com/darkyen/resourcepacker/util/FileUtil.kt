package com.darkyen.resourcepacker.util

import java.io.File

/**
 *
 */
fun File.getExtension():String {
	val name = name
	val extensionSeparator = name.lastIndexOf('.')
	if (extensionSeparator <= 0 || extensionSeparator + 1 == name.length) {
		// No extension or the file is hidden
		return ""
	}
	return name.substring(extensionSeparator + 1)
}