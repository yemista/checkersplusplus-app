package com.checkersplusplus.app

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
import com.checkersplusplus.engine.Board
import com.checkersplusplus.engine.Coordinate
import com.checkersplusplus.engine.pieces.Checker
import kotlin.math.pow
import kotlin.math.sqrt

class CheckerBoardView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val BITMAP_SCALE_FACTOR: Float = 0.9f
    private val BITMAP_OFFSET: Float = (1 - BITMAP_SCALE_FACTOR) / 2

    private val paint = Paint()
    private var board: Board? = null
    private val bitmapInfo = ArrayList<CheckersBitmapLocationInfo>()
    private val removedBitmapInfo = ArrayList<CheckersBitmapLocationInfo>()
    private var checkersCreated: Boolean = false
    private val checkerSquares = mutableListOf<CheckerSquare>()
    private val selectedSquares = mutableListOf<CheckerSquare>()
    private var isBlack = true
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

        if (!checkersCreated && board != null) {
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

    fun move(): Boolean {
        if (selectedSquares.isEmpty() || selectedSquares.size < 2) {
            return false
        }

        val square1 = selectedSquares[0]
        val square2 = selectedSquares[1]
        moveChecker(square1.row, square1.col, square2.row, square2.col)
        return true
    }

    fun resetBoard(board: Board) {
        releaseResources()
        checkersCreated = false
        setLogicalGame(board)
        requestLayout()
        invalidate()
    }

    private fun createCheckers() {
        val squareSize = width / 8f
        val whiteChecker: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.white_checker)
        val blackChecker: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.black_checker)

        for (row in 0 until 8) {
            for (col in 0 until 8) {
                val checker: Checker? = board?.getPiece(Coordinate(col, row))

                if (checker != null) {
                    val positionRow = row
                    val locationRow = if (isBlack) translateNumber(row) else row
                    bitmapInfo.add(
                        CheckersBitmapLocationInfo(
                            Bitmap.createScaledBitmap(
                                if (checker?.color == com.checkersplusplus.engine.enums.Color.BLACK) blackChecker else whiteChecker,
                                (squareSize.toInt() * BITMAP_SCALE_FACTOR).toInt(),
                                (squareSize.toInt() * BITMAP_SCALE_FACTOR).toInt(),
                                false
                            ),
                            (col * squareSize + (squareSize * BITMAP_OFFSET)).toFloat(),
                            (locationRow * squareSize + (squareSize * BITMAP_OFFSET)).toFloat(),
                            positionRow, col, false
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

    private fun moveChecker(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int) {
        var entry: CheckersBitmapLocationInfo? = null
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
        animateCheckerMove(entry, entry.x, entry.y,
            square.x + (squareSize * BITMAP_OFFSET), square.y + (squareSize * BITMAP_OFFSET), duration)
        entry.row = toRow
        entry.col = toCol
    }

    override fun onSizeChanged(newWidth: Int, newHeight: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight)

        checkerSquares.clear()
        val squareSize = newWidth / 8f

        for (i in 7 downTo  0) {
            for (j in 7 downTo  0) {
                val positionRow = if (isBlack) i else translateNumber(i)
                val locationRow = if (isBlack) translateNumber(j) else j
                checkerSquares.add(CheckerSquare(i * squareSize, j * squareSize, locationRow, i, squareSize))
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
                    if (translateNumber(sq.row) == row && translateNumber(sq.col) == col) {
                        square = sq
                        break
                    }
                }

                val remainder = if (isBlack) 0 else 1
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
                    Log.e("COORD", square.row.toString() + "-" + square.col.toString())
                    square.isSelected = !square.isSelected
                    invalidate()
                    selectedSquares.add(square)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateCheckerMove(checker: CheckersBitmapLocationInfo, fromX: Float, fromY: Float, toX: Float, toY: Float, duration: Long) {
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

    fun setLogicalGame(board: Board) {
        this.board = board
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

}
