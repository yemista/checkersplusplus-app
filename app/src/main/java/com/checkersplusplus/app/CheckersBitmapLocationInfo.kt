package com.checkersplusplus.app

import android.graphics.Bitmap

data class CheckersBitmapLocationInfo(val bitmap: Bitmap, var x: Float, var y: Float, var row: Int, var col: Int, var moving: Boolean)
