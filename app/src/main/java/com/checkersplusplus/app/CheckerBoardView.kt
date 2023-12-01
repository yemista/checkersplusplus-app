package com.checkersplusplus.app

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.checkersplusplus.engine.Board

class CheckerBoardView(context: Context, attrs: AttributeSet) : View(context, attrs) {
    private val paint = Paint()
    private var board: Board = Board()
    private val squares: Array<Pair<Float, Float>> = Array(64) { Pair(0f, 0f) }
    private val bitmapInfo = ArrayList<CheckersBitmapLocationInfo>()

    private val borderPaint = Paint().apply {
        color = Color.BLACK // Set border color
        style = Paint.Style.STROKE // Stroke style for the border
        strokeWidth = 4f // Set the width of the border
    }

    private val whiteChecker: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.white_checker)
    private val blackChecker: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.black_checker)
    private lateinit var resizedWhiteChecker: Bitmap
    private lateinit var resizedBlackChecker: Bitmap

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawCheckerboard(canvas)
        paintBorder(canvas)
        drawCheckers(canvas)
    }

    private fun drawCheckers(canvas: Canvas) {
        val squareSize = width / 8f

        if (!::resizedBlackChecker.isInitialized) {
            resizedBlackChecker = Bitmap.createScaledBitmap(blackChecker, squareSize.toInt(), squareSize.toInt(), false)
        }

        if (!::resizedWhiteChecker.isInitialized) {
            resizedWhiteChecker = Bitmap.createScaledBitmap(whiteChecker, squareSize.toInt(), squareSize.toInt(), false)
        }

        for (row in 0 until 3) {
            for (col in 0 until 8) {
                if ((row + col) % 2 == 0) {
                    bitmapInfo.add(CheckersBitmapLocationInfo(Bitmap.createScaledBitmap(blackChecker, squareSize.toInt(), squareSize.toInt(), false),
                        translateNumber(row) * squareSize, col * squareSize, row, col))
                }
            }
        }

        for (info in bitmapInfo) {
            canvas.drawBitmap(info.bitmap, info.y, info.x, null)
        }
    }

    fun moveChecker(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int) {
        for (entry in bitmapInfo) {
            if (entry.row == fromRow && entry.col == fromCol) {
                animateCheckerMove(entry, entry.x, entry.y, entry.x + 200, entry.y + 200)
                break;
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

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec) // Make view square based on width
    }

    private fun drawCheckerboard(canvas: Canvas) {
        val squareSize = width / 8f // Assuming an 8x8 checkerboard

        for (col in 0 until 8) {
            for (row in 0 until 8) {
                val left = col * squareSize
                val top = row * squareSize
                squares[row * 8 + col] = Pair(left, top)

                if ((row + col) % 2 == 0) {
                    paint.color = Color.LTGRAY // Set to white color for one set of squares
                } else {
                    paint.color = Color.DKGRAY // Set to black color for alternating squares
                }
                canvas.drawRect(
                    left, // left
                    top, // top
                    (col + 1) * squareSize, // right
                    (row + 1) * squareSize, // bottom
                    paint
                )
            }
        }
    }

    // Handle touch events if necessary
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle touch interactions
        return true
    }

    fun animateCheckerMove(checker: CheckersBitmapLocationInfo, fromX: Float, fromY: Float, toX: Float, toY: Float) {
        val animatorX = ObjectAnimator.ofFloat(checker, "x", fromX, toX)
        val animatorY = ObjectAnimator.ofFloat(checker, "y", fromY, toY)

        animatorX.duration = 1000 // Duration in milliseconds
        animatorY.duration = 1000

        animatorX.addUpdateListener { invalidate() }
        animatorY.addUpdateListener { invalidate() }

        animatorX.start()
        animatorY.start()
    }

    fun releaseResources() {
        resizedWhiteChecker?.recycle()
        resizedBlackChecker?.recycle()
        whiteChecker?.recycle()
        blackChecker?.recycle()
    }

    fun setLogicalGame(board: Board) {
        this.board = board
    }

    fun move(startRow: Int, startCol: Int, endRow: Int, endCol: Int) {

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

}
