package com.checkersplusplus.app

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.checkersplusplus.engine.Coordinate
import com.checkersplusplus.engine.CoordinatePair
import com.checkersplusplus.engine.Game
import com.checkersplusplus.engine.pieces.Checker
import kotlin.math.pow
import kotlin.math.sqrt

class CheckerBoardView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val BITMAP_SCALE_FACTOR: Float = 0.9f
    private val BITMAP_OFFSET: Float = (1 - BITMAP_SCALE_FACTOR) / 2

    private val paint = Paint()
    var game: Game? = null
    private val bitmapInfo = ArrayList<CheckersBitmapLocationInfo>()
    private val removedBitmapInfo = ArrayList<CheckersBitmapLocationInfo>()
    private var checkersCreated: Boolean = false
    val checkerSquares = mutableListOf<CheckerSquare>()
    private val selectedSquares = mutableListOf<CheckerSquare>()
    var isBlack = true
    private var isMyTurn = true

    private val borderPaint = Paint().apply {
        color = Color.BLACK // Set border color
        style = Paint.Style.STROKE // Stroke style for the border
        strokeWidth = 4f // Set the width of the border
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCheckerboard(canvas)
        paintBorder(canvas)

        if (!checkersCreated && game != null) {
            createCheckers()
            checkersCreated = true
        }

        drawCheckers(canvas)
    }

    fun clearSelected() {
        selectedSquares.forEach { square ->
                square.isSelected = false
        }
        invalidate()
        selectedSquares.clear()
    }

    private fun doLogicalMove() {
        val coordinatesPairs = arrayListOf<CoordinatePair>()
        var lastSquare = selectedSquares[0]

        for (square in selectedSquares) {
            if (square == lastSquare) {
                continue;
            }

            val logicalFromRow = if (isBlack) translateNumber(lastSquare.row) else lastSquare.row
            val logicalFromCol = if (!isBlack) translateNumber(lastSquare.col) else lastSquare.col
            val logicalToRow = if (isBlack) translateNumber(square.row) else square.row
            val logicalToCol = if (!isBlack) translateNumber(square.col) else square.col

            coordinatesPairs.add(
                CoordinatePair(
                    Coordinate(logicalFromCol, logicalFromRow),
                    Coordinate(logicalToCol, logicalToRow)))
            lastSquare = square
        }

        game!!.doMove(coordinatesPairs)
    }

    fun move(): Boolean {
        if (selectedSquares.isEmpty() || selectedSquares.size < 2) {
            return false
        }

        val coordinatesPairs = arrayListOf<CoordinatePair>()
        var lastSquare = selectedSquares[0]

        for (square in selectedSquares) {
            if (square == lastSquare) {
                continue;
            }

            val logicalFromRow = if (isBlack) translateNumber(lastSquare.row) else lastSquare.row
            val logicalFromCol = if (!isBlack) translateNumber(lastSquare.col) else lastSquare.col
            val logicalToRow = if (isBlack) translateNumber(square.row) else square.row
            val logicalToCol = if (!isBlack) translateNumber(square.col) else square.col

            coordinatesPairs.add(
                CoordinatePair(
                    Coordinate(logicalFromCol, logicalFromRow),
                    Coordinate(logicalToCol, logicalToRow)))
            lastSquare = square
        }

        return game!!.isMoveLegal(coordinatesPairs)
    }

    private fun createCheckers() {
        val squareSize = width / 8f
        val whiteChecker: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.white_checker)
        val blackChecker: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.black_checker)

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val checker: Checker? = game?.board?.getPiece(Coordinate(col, row))

                if (checker != null) {
                    val positionRow = if (isBlack) translateNumber(row) else row
                    val positionCol = if (!isBlack) translateNumber(col) else col
                    val logicalRow = if (isBlack) translateNumber(row) else row
                    val logicalCol = if (!isBlack) translateNumber(col) else col
                    bitmapInfo.add(
                        CheckersBitmapLocationInfo(
                            Bitmap.createScaledBitmap(
                                if (checker?.color == com.checkersplusplus.engine.enums.Color.BLACK) blackChecker else whiteChecker,
                                (squareSize.toInt() * BITMAP_SCALE_FACTOR).toInt(),
                                (squareSize.toInt() * BITMAP_SCALE_FACTOR).toInt(),
                                false
                            ),
                            (positionCol * squareSize + (squareSize * BITMAP_OFFSET)).toFloat(),
                            (positionRow * squareSize + (squareSize * BITMAP_OFFSET)).toFloat(),
                            logicalRow, logicalCol, false
                        )
                    )
                }
            }
        }
    }

    private fun drawCheckers(canvas: Canvas) {

        for (info in bitmapInfo) {
            if (!info.moving) {
                canvas.drawBitmap(info.bitmap, info.x, info.y, null)
            }
        }

        for (info in bitmapInfo) {
            if (info.moving) {
                canvas.drawBitmap(info.bitmap, info.x, info.y, null)
            }
        }
    }

    fun doServerMove(squares: List<CheckerSquare>) {
        moveCheckerFromServer(0, 1, squares)
    }

    private fun moveCheckerFromServer(from: Int, to: Int, squares: List<CheckerSquare>) {
        if (to == squares.size) {
            return
        }

        var entry: CheckersBitmapLocationInfo? = null

        for (bitmapEntry in bitmapInfo) {
            if (bitmapEntry.row == squares[from].row && bitmapEntry.col == squares[from].col) {
                entry = bitmapEntry
                break
            }
        }

        if (entry == null) {
            return
        }

        var square: CheckerSquare? = null

        for (sq in checkerSquares) {
            if (sq.row == squares[to].row && sq.col == squares[to].col) {
                square = sq
                break
            }
        }

        if (square == null) {
            return
        }

        val duration = distance(squares[from].row, squares[from].col,
            squares[to].row, squares[to].col) * (1500 / 8)
        val squareSize = width / 8
        animateServerCheckerMove(entry,
            square.x + (squareSize * BITMAP_OFFSET), square.y + (squareSize * BITMAP_OFFSET),
            duration, from, to, squares[to].row, squares[to].col, squares)
    }

    private fun moveChecker(from: Int, to: Int) {
        if (to == selectedSquares.size) {
            doLogicalMove()
            clearSelected()
            invalidate()
            return
        }

        var entry: CheckersBitmapLocationInfo? = null
        val fromRow = selectedSquares[from].row
        val fromCol = selectedSquares[from].col
        val toRow = selectedSquares[to].row
        val toCol = selectedSquares[to].col

        for (bitmapEntry in bitmapInfo) {
            if (bitmapEntry.row == fromRow && bitmapEntry.col == fromCol) {
                entry = bitmapEntry
                break
            }
        }

        if (entry == null) {
            return
        }

        var square: CheckerSquare? = null

        for (sq in checkerSquares) {
            if (sq.row == toRow && sq.col == toCol) {
                square = sq
                break
            }
        }

        if (square == null) {
            return
        }

        val duration = distance(fromRow, fromCol, toRow, toCol) * (1500 / 8)
        val squareSize = width / 8
        animateCheckerMove(entry,
            square.x + (squareSize * BITMAP_OFFSET), square.y + (squareSize * BITMAP_OFFSET),
            duration, from, to, toRow, toCol)
    }

    override fun onSizeChanged(newWidth: Int, newHeight: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight)

        checkerSquares.clear()
        val squareSize = newWidth / 8f

        for (col in 0 until 8) {
            for (row in 0 until 8) {
                val positionRow = if (isBlack) translateNumber(row) else row
                val positionCol = if (!isBlack) translateNumber(col) else col
                val logicalRow = if (isBlack) translateNumber(row) else row
                val logicalCol = if (!isBlack) translateNumber(col) else col
                checkerSquares.add(CheckerSquare(positionCol * squareSize, positionRow * squareSize, logicalRow, logicalCol, squareSize))
            }
        }
    }

    private fun paintBorder(canvas: Canvas) {
        // Draw the border
        val left = 0f
        val top = 0f
        val right = width.toFloat()
        val bottom = height.toFloat()
        canvas.drawRect(left, top, right, bottom, borderPaint)
    }

    private fun distance(x1: Int, y1: Int, x2: Int, y2: Int) : Long {
        return sqrt(
            ((x2 - x1).toDouble()).pow(2.0) + ((y2 - y1).toDouble()).pow(2.0)
        ).toLong()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec) // Make view square based on width
    }

    private fun drawCheckerboard(canvas: Canvas) {
        val squareSize = width / 8f // Assuming an 8x8 checkerboard

        for (col in 0 until 8) {
            for (row in 0 until 8) {
                var square : CheckerSquare? = null

                for (sq in checkerSquares) {
                    val translatedRow = if (isBlack) row else translateNumber(row)
                    val translatedCol = if (isBlack) col else translateNumber(col)

                    if (row == sq.row && col == sq.col) {
                        square = sq
                        break
                    }
                }

                val remainder = 0
                if ((row + col) % 2 == remainder) {
                    paint.color = ContextCompat.getColor(context, R.color.tan);
                } else {
                    paint.color = Color.DKGRAY
                }

                if (square == null) {
                    return
                }

                canvas.drawRect(
                    square.x,
                    square.y,
                    square.x + squareSize, // right
                    square.y + squareSize, // bottom
                    paint
                )
            }
        }

        for (col in 0 until 8) {
            for (row in 0 until 8) {
                val checkerSquare = checkerSquares[row * 8 + col]

                if (checkerSquare.isSelected) {
                    paint.color = Color.YELLOW
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 10f // Adjust border width as needed
                    canvas.drawRect(
                        checkerSquare.x,
                        checkerSquare.y,
                        checkerSquare.x + checkerSquare.size,
                        checkerSquare.y + checkerSquare.size,
                        paint
                    )
                    paint.style = Paint.Style.FILL
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && isMyTurn) {
            val x = event.x
            val y = event.y

            checkerSquares.forEach { square ->
                if (x >= square.x && x <= square.x + square.size &&
                    y >= square.y && y <= square.y + square.size) {
                    square.isSelected = !square.isSelected
                    invalidate()
                    selectedSquares.add(square)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateServerCheckerMove(
        checker: CheckersBitmapLocationInfo, toX: Float, toY: Float,
        duration: Long, from: Int, to: Int,
        toRow: Int, toCol: Int, squares: List<CheckerSquare>
    ) {
        val animatorX = ObjectAnimator.ofFloat(checker, "x", toX)
        val animatorY = ObjectAnimator.ofFloat(checker, "y", toY)

        checker.moving = true

        animatorX.duration = duration
        animatorY.duration = duration

        animatorX.addUpdateListener {
            val iterator = bitmapInfo.iterator()

            while (iterator.hasNext()) {
                val entry = iterator.next()

                if (checker != entry && overlap(checker, entry)) {
                    val temp: CheckersBitmapLocationInfo = checker
                    iterator.remove()
                    removedBitmapInfo.add(temp)
                    break
                }
            }
            invalidate()
        }
        animatorX.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                checker.row = toRow
                checker.col = toCol
                moveCheckerFromServer(to, to + 1, squares)
            }
        })
        animatorY.addUpdateListener { invalidate() }

        animatorX.start()
        animatorY.start()
    }

    private fun animateCheckerMove(checker: CheckersBitmapLocationInfo, toX: Float, toY: Float,
                                   duration: Long, from: Int, to: Int,
                                   toRow: Int, toCol: Int) {
        val animatorX = ObjectAnimator.ofFloat(checker, "x", toX)
        val animatorY = ObjectAnimator.ofFloat(checker, "y", toY)

        checker.moving = true
        animatorX.duration = duration
        animatorY.duration = duration

        animatorX.addUpdateListener {
            val iterator = bitmapInfo.iterator()

            while (iterator.hasNext()) {
                val entry = iterator.next()

                if (checker != entry && overlap(checker, entry)) {
                    val temp: CheckersBitmapLocationInfo = checker
                    iterator.remove()
                    removedBitmapInfo.add(temp)
                    break
                }
            }
            invalidate()
        }
        animatorX.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                checker.row = toRow
                checker.col = toCol
                moveChecker(to, to + 1)
            }
        })
        animatorY.addUpdateListener { invalidate() }

        animatorX.start()
        animatorY.start()
    }

    private fun overlap(
        checker: CheckersBitmapLocationInfo,
        entry: CheckersBitmapLocationInfo
    ): Boolean {
        val size = (width / 8)
        return checker.x + size / 2 > entry.x &&
                checker.x + size / 2 < (entry.x + size) &&
                checker.y + size / 2 > entry.y &&
                checker.y + size / 2 < (entry.y + size)
    }

    fun releaseResources() {
        for (bitmapEntry in bitmapInfo) {
            bitmapEntry.bitmap?.recycle()
        }

        for (bitmapEntry in removedBitmapInfo) {
            bitmapEntry.bitmap?.recycle()
        }
    }

    fun setLogicalGame(game: Game) {
        this.game = game
    }

    private fun translateNumber(num: Int): Int {
        when (num) {
            0 -> return 7
            1 -> return 6
            2 -> return 5
            3 -> return 4
            4 -> return 3
            5 -> return 2
            6 -> return 1
            7 -> return 0
        }
        throw IllegalArgumentException()
    }

    fun setIsMyTurn(myTurn: Boolean) {
        isMyTurn = myTurn
    }

    fun setIsBlack(black: Boolean) {
        isBlack = black
    }

    fun getSelectedSquares(): List<CheckerSquare> {
        return selectedSquares.toList()
    }

    fun doMove() {
//        for (idx in 0 until selectedSquares.size - 1) {
//            moveChecker(idx, idx + 1)
//        }
        moveChecker(0, 1)
    }

}
