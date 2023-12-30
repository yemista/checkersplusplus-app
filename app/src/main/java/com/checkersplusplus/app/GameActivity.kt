package com.checkersplusplus.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.checkersplusplus.engine.Coordinate
import com.checkersplusplus.engine.CoordinatePair
import com.checkersplusplus.engine.Game
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
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
    private lateinit var webSocket: WebSocket
    private var playersTurn : Boolean = false
    private var currentMove: Int = 0
    private var gameStarted : Boolean = false
    private val lock = Any()

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

    private fun updateCheckerBoard(game: Game, myTurn: Boolean, isBlack: Boolean) {
        var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
        checkersBoard.setLogicalGame(game)
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

            if (checkersBoard.shouldDoMove()) {
               sendMove()
               runOnUiThread {
                   checkersBoard.doMove()
                   checkersBoard.invalidate()
                   checkersBoard.requestLayout()
               }
            } else {
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Illegal move.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        val resignButton: Button = findViewById(R.id.resignButton)

        resignButton.setOnClickListener {
            forfeitGame()
            gameStarted = false
        }

        val cancelButton: Button = findViewById(R.id.cancelButton)

        cancelButton.setOnClickListener {
            cancelGame()
            gameStarted = false
        }
    }

    private fun forfeitGame() {
        val client = OkHttpClient()
        val sessionId = StorageUtil.getData("sessionId")
        val json = JSONObject()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val gameId = intent.getStringExtra("gameId")
        val request = Request.Builder()
            .url("http://" + BuildConfig.BASE_URL + "/game/" + sessionId + "/" + gameId + "/forfeit")
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
                    showEndGameDialog("You resigned")
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, game["message"], Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }

    private fun startWebSocket() {
        val request = Request.Builder().url("ws://" + BuildConfig.BASE_URL + "/updates").build()
        val listener = object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, message: String) {
                if (message.startsWith("MOVE")) {
                    val parts = message.split('|')
                    runOnUiThread {
                        processMoveFromServer(parts[1], parts[2])
                    }
                } else if (message.startsWith("TIMEOUT_LOSS")) {
                    showEndGameDialog("You timed out. You lose.")
                } else if (message.startsWith("TIMEOUT")) {
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
                    gameStarted = true

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

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e("WEBSOCKET_ERROR", t.toString())
            }

            // Implement other WebSocketListener methods as necessary
        }

        webSocket = webSocketClient.newWebSocket(request, listener)
        val sessionId = StorageUtil.getData("sessionId")
        webSocket.send(sessionId)

        lifecycleScope.launch(Dispatchers.IO) { // Starts a coroutine in the background thread
            // Code to run in background
            // For example, a network call or database operation
            val sessionId = StorageUtil.getData("sessionId")
            webSocket.send(sessionId)
            Thread.sleep(1000 * 60)

            withContext(Dispatchers.Main) {
                // Code to run on the main thread, like updating the UI
            }
        }

        // Ensure the client dispatcher is properly shut down on app exit
        webSocketClient.dispatcher.executorService.shutdown()
    }

    private fun processMoveFromServer(moveNum: String, moveList: String) {
        val num = moveNum.toIntOrNull()

        if (num == null) {
            restartApp()
        }

        var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
        val movesToAnimate = arrayListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
        val coordinatePairs = arrayListOf<CoordinatePair>()

        synchronized(lock) {
            if (num == currentMove + 1) {
                val moves = moveList.split('+')

                for (move in moves) {
                    if (move.isEmpty()) {
                        continue
                    }

                    // Log.e("MOVE", move)
                    val parts = move.split('-')
                    val start = parts[0]
                    // Log.e("START", start)
                    val starts = start.split(',')
                    val startRow = if (starts[0].startsWith("r")) parseOutNumber(starts[0]) else parseOutNumber(starts[1])
                    val startCol = if (starts[0].startsWith("c")) parseOutNumber(starts[0]) else parseOutNumber(starts[1])
                    val end = parts[1]
                    val ends = end.split(',')
                    val endRow = if (ends[0].startsWith("r")) parseOutNumber(ends[0]) else parseOutNumber(ends[1])
                    val endCol = if (ends[0].startsWith("c")) parseOutNumber(ends[0]) else parseOutNumber(ends[1])
                    coordinatePairs.add(CoordinatePair(Coordinate(startCol, startRow), Coordinate(endCol, endRow)))
                    val fromRow = if (checkersBoard.isBlack) translateNumber(startRow) else startRow
                    val fromCol = if (!checkersBoard.isBlack) translateNumber(startCol) else startCol
                    val toRow = if (checkersBoard.isBlack) translateNumber(endRow) else endRow
                    val toCol = if (!checkersBoard.isBlack) translateNumber(endCol) else endCol

                    movesToAnimate.add(Pair(Pair(fromRow, fromCol), Pair(toRow, toCol)))
                }

                currentMove++
                setTurn(true)
                checkersBoard.game!!.doMove(coordinatePairs)
            }
        }

        val squares = mutableListOf<CheckerSquare>()

        for (idx in 0 until movesToAnimate.size) {
            if (idx == 0) {
                squares.add(findSquareForCoordinates(movesToAnimate[idx].first.first,
                    movesToAnimate[idx].first.second))
                squares.add(findSquareForCoordinates(movesToAnimate[idx].second.first,
                    movesToAnimate[idx].second.second))
            } else {
                squares.add(findSquareForCoordinates(movesToAnimate[idx].second.first,
                    movesToAnimate[idx].second.second))
            }
        }

        checkersBoard.doServerMove(squares)
    }

    private fun findSquareForCoordinates(row: Int, col: Int): CheckerSquare {
        var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)

        for (sq in checkersBoard.checkerSquares) {
            if (sq.row == row && sq.col == col) {
                return sq
            }
        }

        restartApp()
        // never reached
        return CheckerSquare(0f, 0f, 0, 0, 0f)
    }

    private fun setTurn(myTurn: Boolean) {
        val checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
        checkersBoard.setIsMyTurn(myTurn)
        val status: TextView = findViewById(R.id.statusTextView)

        if (myTurn) {
            status.text = "Your turn"
        } else {
            status.text = "Opponents turn"
        }
    }

    private fun parseOutNumber(part: String): Int {
        val pieces = part.split(':')
        return pieces[1].toIntOrNull()!!
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

                if (game["gameState"] != null) {

                    val logicalBoard = Game(game["gameState"])
                    val accountId = StorageUtil.getData("accountId")

                    if (accountId == null) {
                        restartApp()
                    }

                    currentMove = parseCurrentMove(game["gameState"]!!)
                    playersTurn = accountId == game["currentTurnId"]
                    val isBlack = accountId == game["blackAccountId"]
                    val notStarted = game["blackAccountId"] == null || game["redAccountId"] == null

                    runOnUiThread {
                        val status: TextView = findViewById(R.id.statusTextView)

                        if (notStarted) {
                            status.text = "Waiting for opponent"
                        } else {
                            gameStarted = true
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

    private fun parseCurrentMove(state: String): Int {
        val parts = state.split('|')
        val num = parts[1].toIntOrNull()

        if (num == null) {
            restartApp()
        }

        return num!!
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

    private fun createMoveListForPost(): List<Move> {
        val checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
        val squares = checkersBoard.getSelectedSquares()
        var lastSquare = squares[0]
        val moveList = mutableListOf<Move>()

        for (square in squares) {
            if (square == lastSquare) {
                continue
            }

            val fromRow = if (checkersBoard.isBlack) translateNumber(lastSquare.row) else lastSquare.row
            val fromCol = if (!checkersBoard.isBlack) translateNumber(lastSquare.col) else lastSquare.col
            val toRow = if (checkersBoard.isBlack) translateNumber(square.row) else square.row
            val toCol = if (!checkersBoard.isBlack) translateNumber(square.col) else square.col
            moveList.add(Move(fromCol, fromRow, toCol, toRow))
            lastSquare = square
        }

        return moveList
    }

    private fun sendMove() {
        val client = OkHttpClient()
        val gson = Gson()
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = createMoveListForPost()
        val jsonString = gson.toJson(requestBody)
        val body = RequestBody.create(mediaType, jsonString)
        val sessionId = StorageUtil.getData("sessionId")
        val gameId = intent.getStringExtra("gameId")
        val request = Request.Builder()
            .url("http://" + BuildConfig.BASE_URL + "/game/" + sessionId + "/" + gameId + "/move")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Network error. Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
                    finish()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    currentMove++
                    val checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
                    checkersBoard.setIsMyTurn(false)
                } else {
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

                    runOnUiThread {
                        Toast.makeText(this@GameActivity, game["message"].toString(), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    private fun showEndGameDialog(message: String) {
        runOnUiThread {
            gameStarted = false

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

    override fun onBackPressed() {
        if (!gameStarted) {
            super.onBackPressed()
        } else {

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