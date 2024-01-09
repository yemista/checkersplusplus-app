package com.checkersplusplus.app

import android.graphics.Bitmap

data class CheckersBitmapLocationInfo(
    var bitmap: Bitmap, var x: Float, var y: Float,
    var row: Int, var col: Int, var moving: Boolean,
    var isKing: Boolean, val isBlack: Boolean)
