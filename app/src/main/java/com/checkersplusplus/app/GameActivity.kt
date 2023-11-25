package com.checkersplusplus.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.checkersplusplus.engine.Board
import com.checkersplusplus.engine.pieces.Checker
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException

class GameActivity : AppCompatActivity() {
    private lateinit var webSocketClient: OkHttpClient
    private var selectedSquares = ArrayList<View>()
    private var logicalBoard: Board = Board()
    private var playersTurn: Boolean = false

    private fun onSquareClicked(view: View) {
        if (!playersTurn) {
            return
        }

        val tag = view.tag as Pair<Int, Int>
        selectedSquares.add(view)
        // Remove border from previously selected square
        view?.setBackgroundColor(Color.DKGRAY)

        // Add border to the newly selected square
        view.setBackgroundResource(R.drawable.border_yellow)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)

        val checkersBoard: GridLayout = findViewById(R.id.checkersBoard)
        val overlayImageView: ImageView = findViewById(R.id.overlayImageView)

        checkersBoard.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                // Remove the listener to prevent being called multiple times
                checkersBoard.viewTreeObserver.removeOnGlobalLayoutListener(this)

                val params = FrameLayout.LayoutParams(checkersBoard.width, checkersBoard.height)
                overlayImageView.layoutParams = params

                // Optionally make the ImageView visible if required
                overlayImageView.visibility = View.VISIBLE
                
                positionButtons(checkersBoard.width, checkersBoard.height)
            }
        })

        createCheckersBoard(checkersBoard)
        setupActionListeners()

        val gameId = intent.getStringExtra("gameId")

        if (gameId == null) {
            //restartApp()
        } else {
            //lookupGame(gameId)
        }

        //MobileAds.initialize(this) {}
        //loadRewardedAd()
    }

    private fun positionButtons(width: Int, height: Int) {

    }

    private fun setupActionListeners() {
        val clearButton: Button = findViewById(R.id.clearButton)

        clearButton.setOnClickListener {
            selectedSquares.clear()
            val checkersBoard: GridLayout = findViewById(R.id.checkersBoard)
            createCheckersBoard(checkersBoard)
            val board = logicalBoard.board
            drawCheckers(board)
        }

        val moveButton: Button = findViewById(R.id.moveButton)

        moveButton.setOnClickListener {
            val board = logicalBoard.board
            selectedSquares.clear()
            val checkersBoard: GridLayout = findViewById(R.id.checkersBoard)
            createCheckersBoard(checkersBoard)
            drawCheckers(board)
        }
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

    private fun drawCheckers(board: Array<Array<Checker>>) {
        val gridSize = 8
        val checkersBoard: GridLayout = findViewById(R.id.checkersBoard)
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val position = row * checkersBoard.columnCount + col
                val view: ImageView = checkersBoard.getChildAt(position) as ImageView
                val checker: Checker = board[translateNumber(row)][col]

                if (checker != null) {
                    if (checker.color == com.checkersplusplus.engine.enums.Color.RED) {
                        view.setImageResource(R.drawable.white_checker)
                    }

                    if (checker.color == com.checkersplusplus.engine.enums.Color.BLACK) {
                        view.setImageResource(R.drawable.black_checker)
                    }
                }
            }
        }

        checkersBoard.invalidate()
    }

    private fun startWebSocket() {
        val request = Request.Builder().url("ws://yourwebsocketurl").build()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, message: String) {
                runOnUiThread {
                    if (message.startsWith("MOVE")) {

                    } else if (message.startsWith("FORFEIT")) {

                    }
                }
            }

            // Implement other WebSocketListener methods as necessary
        }

        webSocketClient.newWebSocket(request, listener)

        // Ensure the client dispatcher is properly shut down on app exit
        webSocketClient.dispatcher.executorService.shutdown()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            var gridLayout: GridLayout = findViewById(R.id.checkersBoard)
            createCheckersBoard(gridLayout)
            val board = logicalBoard.board
            drawCheckers(board)
        }
    }

    private fun createCheckersBoard(gridLayout: GridLayout) {
        val totalColumns = 8 // For an 8x8 board
        val size = Math.min(gridLayout.width, gridLayout.height) / totalColumns
        gridLayout.removeAllViews() // Clear existing views if any
        gridLayout.columnCount = totalColumns
        gridLayout.rowCount = totalColumns
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val gridSize = (screenWidth * 0.9).toInt()

        for (i in 0 until totalColumns * totalColumns) {
            val square = ImageView(this)
            val row = i / totalColumns
            val col = i % totalColumns
            square.tag = Pair(row, col)
            val layoutParams = GridLayout.LayoutParams()
            layoutParams.width = size
            layoutParams.height = size
            layoutParams.rowSpec = GridLayout.spec(row)
            layoutParams.columnSpec = GridLayout.spec(col)
            square.layoutParams = layoutParams
            square.setBackgroundColor(if ((i / totalColumns + i % totalColumns) % 2 == 1) Color.DKGRAY else Color.LTGRAY)
            square.setOnClickListener { view ->
                onSquareClicked(view)
            }
            gridLayout.addView(square)
        }

        // Set new layout parameters
        val layoutParams = FrameLayout.LayoutParams(gridSize, gridSize)
        gridLayout.layoutParams = layoutParams

        gridLayout.requestLayout()
    }

    private fun lookupGame(gameId: String) {
        val client = OkHttpClient()
        val requestBody = FormBody.Builder()
            .build()
        val request = Request.Builder()
            .url(BuildConfig.BASE_URL + "/" + gameId)
            .post(requestBody)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Network error. Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""

                if (responseBody == null) {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "No response from server. Try again soon",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                val game = ResponseUtil.parseJson(responseBody)

                if (game == null) {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Invalid response from server. Try again soon",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                if (game["gameState"] != null) {
                    logicalBoard = Board(game["gameState"])
                    val accountId = StorageUtil.getData("accountId")

                    if (accountId == null) {
                        restartApp()
                    }

                    playersTurn = accountId == game["currentTurnId"]
                }
            }
        })
    }

    fun postRequest(gameId: String) {
        val client = OkHttpClient()
        val requestBody = FormBody.Builder()
            .build()
        val sessionId = StorageUtil.getData("sessionId")
        val request = Request.Builder()
            .url("/game/{$sessionId}/{$gameId}/join")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Network error. Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                runOnUiThread {
                    if (response.isSuccessful) {
                        runOnUiThread {
                            val checkersBoard: GridLayout = findViewById(R.id.checkersBoard)
                            createCheckersBoard(checkersBoard)
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(this@GameActivity, "Error", Toast.LENGTH_SHORT).show()
                            finish() // Close the current activity
                        }
                    }
                }
            }
        })
    }

    private fun restartApp() {
        val restartIntent = Intent(this, MainActivity::class.java)
        restartIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(restartIntent)
        finish()
    }

}