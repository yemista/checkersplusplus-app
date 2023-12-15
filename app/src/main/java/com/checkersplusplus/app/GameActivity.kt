package com.checkersplusplus.app

import android.app.AlertDialog
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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.checkersplusplus.engine.Board
import com.checkersplusplus.engine.pieces.Checker
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException

class GameActivity : AppCompatActivity() {
    private lateinit var webSocketClient: OkHttpClient
    private var playersTurn : Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_game)
        setupActionListeners()

        val gameId = intent.getStringExtra("gameId")

        if (gameId == null) {
            restartApp()
        } else {
            lookupGame(gameId)
        }

        webSocketClient = OkHttpClient()
        startWebSocket()
        //MobileAds.initialize(this) {}
        //loadRewardedAd()
    }

    private fun updateCheckerBoard(board: Board, myTurn: Boolean, isBlack: Boolean) {
        var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
        checkersBoard.setLogicalGame(board)
        checkersBoard.setIsMyTurn(myTurn)
        checkersBoard.setIsBlack(isBlack)
        checkersBoard.requestLayout()
        checkersBoard.invalidate()
    }

    private fun setupActionListeners() {
        val clearButton: Button = findViewById(R.id.clearButton)

        clearButton.setOnClickListener {
            var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
            checkersBoard.clearSelected()
        }

        val moveButton: Button = findViewById(R.id.moveButton)

        moveButton.setOnClickListener {
            var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)

            if (checkersBoard.move()) {
               //postRequest()
            }
        }

        val resignButton: Button = findViewById(R.id.resignButton)

        resignButton.setOnClickListener {
            //forfeitGame()
        }

        val cancelButton: Button = findViewById(R.id.cancelButton)

        cancelButton.setOnClickListener {
            cancelGame()
        }
    }

    private fun startWebSocket() {
        val request = Request.Builder().url("ws://" + BuildConfig.BASE_URL + "/updates").build()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, message: String) {
                Log.e("MESSAGE", message)
                if (message.startsWith("MOVE")) {

                }  else if (message.startsWith("TIMEOUT")) {
                    showEndGameDialog("Opponent timed out. You win.")
                } else if (message.startsWith("FORFEIT")) {
                    showEndGameDialog("Opponent forfeits. You win.")
                } else if (message.startsWith("WIN")) {
                    showEndGameDialog("You win.")
                } else if (message.startsWith("LOSE")) {
                    showEndGameDialog("You lose.")
                } else if (message.startsWith("DRAW")) {
                    showEndGameDialog("Draw.")
                } else if (message.startsWith("BEGIN")) {
                    runOnUiThread {
                        val cancelButton: Button = findViewById(R.id.cancelButton)
                        cancelButton.visibility = View.GONE
                        cancelButton.invalidate()

                        val resignButton: Button = findViewById(R.id.resignButton)
                        resignButton.visibility = View.VISIBLE
                        resignButton.invalidate()

                        val status: TextView = findViewById(R.id.statusTextView)
                        if (playersTurn) {
                            status.text = "Your turn"
                        } else {
                            status.text = "Opponents turn"
                        }

                        status.invalidate()
                    }
                }
            }

            // Implement other WebSocketListener methods as necessary
        }

        val webSocket = webSocketClient.newWebSocket(request, listener)
        val sessionId = StorageUtil.getData("sessionId")
        webSocket.send(sessionId)
        // Ensure the client dispatcher is properly shut down on app exit
        webSocketClient.dispatcher.executorService.shutdown()
    }

    private fun lookupGame(gameId: String) {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url("http://" + BuildConfig.BASE_URL + "/game/" + gameId)
            .build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext,
                        "Network error. Failed to connect: ${e.message}",
                        Toast.LENGTH_LONG)
                        .show()
                }
                finish()
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

                if (response.code == 404 /*NOT_FOUND*/) {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "The game you were looking for no longer exists",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    finish()
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
                Log.e("STATE", game.toString())
                if (game["gameState"] != null) {

                    val logicalBoard = Board(game["gameState"])
                    val accountId = StorageUtil.getData("accountId")

                    if (accountId == null) {
                        restartApp()
                    }

                    playersTurn = accountId == game["currentTurnId"]
                    val isBlack = accountId == game["blackAccountId"]
                    val notStarted = game["blackAccountId"] == null || game["redAccountId"] == null

                    runOnUiThread {
                        val status: TextView = findViewById(R.id.statusTextView)

                        if (notStarted) {
                            status.text = "Waiting for opponent"
                        } else {
                            val cancelButton: Button = findViewById(R.id.cancelButton)
                            cancelButton.visibility = View.GONE
                            cancelButton.invalidate()

                            val resignButton: Button = findViewById(R.id.resignButton)
                            resignButton.visibility = View.VISIBLE
                            resignButton.invalidate()

                            if (playersTurn) {
                                status.text = "Your turn"
                            } else {
                                status.text = "Opponents turn"
                            }
                        }

                        updateCheckerBoard(logicalBoard, playersTurn, isBlack)
                    }
                }
            }
        })
    }

    private fun createMoveListForPost(): RequestBody {
        val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        var jsonBody = "["
        val checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
        val squares = checkersBoard.getSelectedSquares()
        var lastSquare = squares[0]

        for (square in squares) {
            jsonBody += "{\"startCol\":\"${lastSquare.col},\"startRow\":\"${lastSquare.row}\",\"endCol\":\"${square.col},\"endRow\":\"${square.row}\"}"
            lastSquare = square
        }

        jsonBody += "]"
        return jsonBody.toRequestBody(jsonMediaType)
    }

    fun postRequest() {
        val client = OkHttpClient()
        val requestBody = createMoveListForPost()
        val sessionId = StorageUtil.getData("sessionId")
        val gameId = intent.getStringExtra("gameId")
        val request = Request.Builder()
            .url("http://" + BuildConfig.BASE_URL + "/game/{$sessionId}/{$gameId}/move")
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

    private fun showEndGameDialog(message: String) {
        runOnUiThread {
            // Create an AlertDialog builder
            val builder = AlertDialog.Builder(this)

            // Set the message to show in the dialog
            builder.setMessage(message)

            // Add a button to close the dialog
            builder.setPositiveButton("Close") { dialog, _ ->
                // User clicked the "Close" button, so dismiss the dialog
                dialog.dismiss()
            }

            // Create and show the AlertDialog
            val dialog = builder.create()

            // Set a dismiss listener on the dialog
            dialog.setOnDismissListener {
                // Close the activity when the dialog is dismissed
                finish()
            }

            dialog.show()

            // Optionally, prevent the dialog from being canceled when touched outside
            dialog.setCanceledOnTouchOutside(false)
        }
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

    private fun cancelGame() {
        val client = OkHttpClient()
        val sessionId = StorageUtil.getData("sessionId")
        val json = JSONObject()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val gameId = intent.getStringExtra("gameId")
        val request = Request.Builder()
            .url("http://" + BuildConfig.BASE_URL + "/game/" + sessionId + "/" + gameId + "/cancel")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Network error. Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (responseBody == null) {
                    runOnUiThread {
                        Toast.makeText(
                            applicationContext,
                            "Invalid response from server. Try again soon",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    return
                }

                val game = ResponseUtil.parseJson(responseBody)

                if (response.isSuccessful) {
                    val intent = Intent(this@GameActivity, OpenGamesActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, game["message"], Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}