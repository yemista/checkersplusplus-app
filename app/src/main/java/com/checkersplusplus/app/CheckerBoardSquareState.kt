package com.checkersplusplus.app

data class CheckerBoardSquareState(
    val row: Int,
    val col: Int,
    var isHighlighted: Boolean = false
)
