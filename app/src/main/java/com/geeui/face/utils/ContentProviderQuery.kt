package com.geeui.face.utils

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.util.Log

object ContentProviderQuery {
   suspend  fun query(name: String, context: Context): String {
       Log.i("---", "query: $name")
        var path = ""
        val contentResolver: ContentResolver = context.getContentResolver()
        val uri: Uri = Uri.parse("content://com.letianpai.robot.resources.provider/expression")
        val cursor: Cursor? = contentResolver.query(
            uri,
            arrayOf("fileName", "filePath", "fileTag", "defaultPath"),
            "fileName",
            arrayOf(name),
            null
        )
        if (cursor != null) {
            while (cursor.moveToNext()) {
                val value1: String =
                    cursor.getString(cursor.getColumnIndexOrThrow("fileName"))
                val value2: String =
                    cursor.getString(cursor.getColumnIndexOrThrow("filePath"))
                path = value2
                val data: String =
                    cursor.getString(cursor.getColumnIndexOrThrow("fileTag"))
                val data2: String =
                    cursor.getString(cursor.getColumnIndexOrThrow("defaultPath"))
                Log.d(
                    "----",
                    "name $name value1--- ::$value1---value121::$value2---data::$data--data2::$data2"
                )
            }
            cursor.close()
        }
        return path
    }
}