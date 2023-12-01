package com.checkersplusplus.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
import java.io.IOException

class GameActivity : AppCompatActivity() {
    private lateinit var webSocketClient: OkHttpClient
    private var selectedSquares = ArrayList<View>()
    private var logicalBoard: Board = Board()
    private var playersTurn: Boolean = false
    private var imageSize: Int = 0

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

        val buttonsContainer: LinearLayout = findViewById(R.id.buttonsContainer)
        val clearButton: Button = findViewById(R.id.clearButton)

        setupActionListeners()

        val gameId = intent.getStringExtra("gameId")

        if (gameId == null) {
            restartApp()
        } else {
            //lookupGame(gameId)
            updateCheckerBoard(Board())
        }

        //MobileAds.initialize(this) {}
        //loadRewardedAd()
    }

    private fun updateCheckerBoard(board: Board) {
        var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
        checkersBoard.setLogicalGame(board)
        checkersBoard.requestLayout()
        checkersBoard.invalidate()
    }

    private fun setupActionListeners() {
        val clearButton: Button = findViewById(R.id.clearButton)

        clearButton.setOnClickListener {
//            selectedSquares.clear()
//            val checkersBoard: GridLayout = findViewById(R.id.checkersBoard)
//            createCheckersBoard(checkersBoard)
//            val board = logicalBoard.board
//            drawCheckers(board)
            move()
        }

        val moveButton: Button = findViewById(R.id.moveButton)

//        moveButton.setOnClickListener {
//            val board = logicalBoard.board
//            selectedSquares.clear()
//            val checkersBoard: GridLayout = findViewById(R.id.checkersBoard)
//            createCheckersBoard(checkersBoard)
//            drawCheckers(board)
//        }
    }

    private fun move() {
        var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
        checkersBoard.moveChecker(2, 2, 3, 3)
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
//                runOnUiThread {
//                    if (response.isSuccessful) {
//                        runOnUiThread {
//                            val checkersBoard: GridLayout = findViewById(R.id.checkersBoard)
//                            createCheckersBoard(checkersBoard)
//                        }
//                    } else {
//                        runOnUiThread {
//                            Toast.makeText(this@GameActivity, "Error", Toast.LENGTH_SHORT).show()
//                            finish() // Close the current activity
//                        }
//                    }
//                }
            }
        })
    }

    private fun restartApp() {
        val restartIntent = Intent(this, MainActivity::class.java)
        restartIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(restartIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
        checkersBoard.releaseResources()
    }
}