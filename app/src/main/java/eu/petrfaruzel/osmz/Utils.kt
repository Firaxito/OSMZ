package eu.petrfaruzel.osmz

import android.os.Environment
import java.io.File

// Can be updated later with non-obsolete android storage API

fun getFileFromStorage(path: String): File {
    val prefix = if(path[0] == '/') "" else "/"
    return File("${Environment.getExternalStorageDirectory()}${prefix}${path}")
}