package com.checkersplusplus.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.checkersplusplus.engine.Coordinate
import com.checkersplusplus.engine.CoordinatePair
import com.checkersplusplus.engine.Game
import com.checkersplusplus.engine.pieces.King
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
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
import java.lang.Integer.min
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class GameActivity : AppCompatActivity() {
    private var coroutineJob: Job? = null
    private lateinit var webSocketClient: OkHttpClient
    private lateinit var webSocket: WebSocket
    private var playersTurn : Boolean = false
    private var currentMove: Int = 0
    private var gameStarted : Boolean = false
    private var processingMove = AtomicBoolean(false)
    private var buttonPressed: Boolean = false
    private var isBlack: Boolean = false
    private var connected: Boolean = false
    private lateinit var logicalBoard: Game
    private var mInterstitialAd: InterstitialAd? = null
    private var opponentName: String = "Opponent"
    private val threeMinutesInMillis: Long = 3 * 60 * 1000
    private val countDownInterval: Long = 1000

    var countDownTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.getLogger(OkHttpClient::class.java.name).setLevel(Level.SEVERE)
        setContentView(R.layout.activity_game)

        val view = findViewById<View>(R.id.checkerBoardView) // Replace with your view ID

        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels * .5

        var minDimension = 0

        if (screenWidth > displayMetrics.heightPixels) {
            minDimension = min(screenWidth, screenHeight.toInt())
        } else {
            minDimension = screenWidth - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32.toFloat(), displayMetrics).toInt()
        }

        val layoutParams = view.layoutParams
        layoutParams.width = minDimension
        view.layoutParams = layoutParams
        view.invalidate()

        setupActionListeners()

        webSocketClient = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()

        opponentName = getString(R.string.opponent)
        val gameId = intent.getStringExtra("gameId")

        if (gameId == null) {
            restartApp()
        } else {
            val networkScope = CoroutineScope(Dispatchers.IO)
            networkScope.launch {
                try {
                    val deferred = async { lookupGame(gameId) }
                    deferred.await()

                    if (deferred.getCompleted()) {
                        val deferredServerIp = async { lookupWebSocketServer() }
                        deferredServerIp.await()
                        startWebSocket(deferredServerIp.getCompleted())
                    } else {

                    }
                } catch (e: CancellationException) {
                    // Ignore cancellation
                } catch (e: Exception) {
                    finish()
                }
            }
            lookupOpponentName(gameId)
        }

        MobileAds.initialize(this) {

        }

//        var mAdView = findViewById<AdView>(R.id.adView)
//        val adRequest1: AdRequest = AdRequest.Builder().build()
//        mAdView.loadAd(adRequest1)

        var mAdView2 = findViewById<AdView>(R.id.adView2)
        val adRequest2: AdRequest = AdRequest.Builder().build()
        mAdView2.loadAd(adRequest2)

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this,
            "ca-app-pub-7797105685801671/6364869814",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    // The mInterstitialAd reference will be null until
                    // an ad is loaded.
                    mInterstitialAd = interstitialAd
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    mInterstitialAd = null
                }
            })
        //MobileAds.initialize(this) {}
        //loadRewardedAd()
        coroutineJob = null
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
            if (buttonPressed) {
                return@setOnClickListener
            }

            var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
            checkersBoard.clearSelected()
        }

        val moveButton: Button = findViewById(R.id.moveButton)

        moveButton.setOnClickListener {
            if (buttonPressed) {
                Log.e("ERR", "BUTTON_PRESSED")
                return@setOnClickListener
            }

            if (coroutineJob?.isActive == true) {
                Log.e("ERR", "COROUTINE_ACTIVE")
                return@setOnClickListener
            }

            Log.e("MOVE", currentMove.toString())

            buttonPressed = true
            var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)

            if (checkersBoard.shouldDoMove()) {
                if (!playersTurn) {
                    Log.e("ERR", "OTHERS_TURN")
                    return@setOnClickListener
                }

                coroutineJob = CoroutineScope(Dispatchers.Main).launch {
                    if (isActive) {
                        onResume()
                    }

                    try {
                        val result = async { sendMove() }
                        result.await()

                        if (result.getCompleted()) {
                            try {
                                countDownTimer?.cancel()
                                val status: TextView = findViewById(R.id.timeLeftText)
                                status.text = ""
                                checkersBoard.doMove()
                                setTurn(false)
                                //checkersBoard.invalidate()
                                //checkersBoard.requestLayout()
                            } catch (e: Exception) {
                                restartApp()
                            }
                        }
                    } catch (e: Exception) {

                    } finally {
                        buttonPressed = false
                    }
                }
            } else {
                runOnUiThread {
                    Toast.makeText(
                        applicationContext,
                        "Illegal move.",
                        Toast.LENGTH_SHORT
                    ).show()
                    buttonPressed = false
                }
            }
        }

        val resignButton: Button = findViewById(R.id.resignButton)

        resignButton.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle(getString(R.string.forfeit))
            builder.setMessage(getString(R.string.forfeit_text))

            // Setting the OK Button
            builder.setPositiveButton(getString(R.string.yes)) { dialog, which ->
                forfeitGame()
            }

            // Setting the Cancel Button
            builder.setNegativeButton(getString(R.string.no)) { dialog, which ->
                dialog.dismiss() // Simply dismiss the dialog
            }

            // Create and show the dialog
            builder.create().show()
        }

        val cancelButton: Button = findViewById(R.id.cancelButton)

        cancelButton.setOnClickListener {
            cancelGame()
            gameStarted = false
        }
    }

    private fun forfeitGame() {
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val sessionId = StorageUtil.getData("sessionId")
        val json = JSONObject()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val gameId = intent.getStringExtra("gameId")
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/game/" + sessionId + "/" + gameId + "/forfeit")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showMessage(getString(R.string.network_error))
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (responseBody == null) {
                    showMessage(getString(R.string.no_server_response_error))
                    return
                }

                val game = ResponseUtil.parseJson(responseBody)

                if (response.isSuccessful) {
                    showEndGameDialog(game["message"].toString())
                } else {
                    showFailureDialog(game["message"].toString(), true)
                }
            }
        })
    }

    private fun startTimer() {
        countDownTimer = object : CountDownTimer(threeMinutesInMillis, countDownInterval) {
            override fun onTick(millisUntilFinished: Long) {
                val status: TextView = findViewById(R.id.timeLeftText)

                // Update UI every second
                val timeLeft = millisUntilFinished / 1000 // Convert to seconds
                val minutes = (timeLeft.toInt() / 60).toInt()
                val seconds = timeLeft - (minutes * 60)

                if (seconds < 10) {
                    status.text = minutes.toString() + ":0" + seconds
                } else {
                    status.text = minutes.toString() + ":" + seconds
                }
            }

            override fun onFinish() {
                val status: TextView = findViewById(R.id.timeLeftText)
                status.text = ""
            }
        }
        countDownTimer?.start()
    }

    private fun startWebSocket(serverIp: String) {

        val request = Request.Builder().url("wss://" + serverIp + ":8080/checkersplusplus/api/updates").build()
        val listener = object : WebSocketListener() {
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                //showFailureDialog("CLOSING" + reason + " " + code.toString(), false)
                connected = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                //restartApp()
                connected = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                showFailureDialog(getString(R.string.network_error), true)
//                webSocket.close(1000, null)
//                connected = false
            }

            override fun onMessage(webSocket: WebSocket, message: String) {
                Log.e("MSG", message)

                if (message.startsWith("MOVE")) {
                    val parts = message.split('|')
                    val moveNum = parts[1].toIntOrNull()

                    if (moveNum == currentMove) {
                        acknowledgeGameEventSync(parts[3])
                        return
                    }

                    if (processingMove.get()) {
                        return
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            processMoveFromServer(parts[1], parts[2]) {
                                acknowledgeGameEventSync(parts[3])
                                currentMove++
                                Log.e("PROCESSING_MOVE_CB", "3")
                                processingMove.set(false)
                                setTurn(true)
                                Log.e("PROCESSING_MOVE_CB", "4")
                            }
                        } catch (e: Exception) {
                            Log.e("EXC", e.toString())
                            e.printStackTrace()
                            processingMove.set(false)
                        }
                    }
                } else if (message.startsWith("TIMEOUT_LOSS")) {
                    val parts = message.split('|')
                    CoroutineScope(Dispatchers.Main).launch {
                        val deferred = async { acknowledgeGameEvent(parts[2]) }
                        deferred.await()

                        if (deferred.getCompleted()) {
                            showEndGameDialog(getString(R.string.player_timeout_error) + parts[1])
                        }

                    }
                } else if (message.startsWith("TIMEOUT")) {
                    val parts = message.split('|')
                    CoroutineScope(Dispatchers.Main).launch {
                        val deferred = async { acknowledgeGameEvent(parts[2]) }
                        deferred.await()

                        if (deferred.getCompleted()) {
                            showEndGameDialog(opponentName + getString(R.string.opponent_timeout_error) + parts[1])
                        }
                    }
                } else if (message.startsWith("FORFEIT")) {
                    val parts = message.split('|')
                    CoroutineScope(Dispatchers.Main).launch {
                        val deferred = async { acknowledgeGameEvent(parts[2]) }
                        deferred.await()

                        if (deferred.getCompleted()) {
                            showEndGameDialog(
                                opponentName + getString(R.string.opponent_forfeit) + message.split(
                                    '|'
                                )[1]
                            )
                        }
                    }
                } else if (message.startsWith("WIN")) {
                    val parts = message.split('|')
                    CoroutineScope(Dispatchers.Main).launch {
                        val deferred = async { acknowledgeGameEvent(parts[2]) }
                        deferred.await()

                        if (deferred.getCompleted()) {
                            val parts = message.split('|')
                            val newRating = parts[1]
                            showEndGameDialog(getString(R.string.win_message) + newRating.toString())
                        }
                    }
                } else if (message.startsWith("LOSE")) {
                    val parts = message.split('|')
                    CoroutineScope(Dispatchers.Main).launch {
                        val deferred = async { acknowledgeGameEvent(parts[4]) }
                        deferred.await()

                        if (deferred.getCompleted()) {
                            val parts = message.split('|')
                            val newRating = parts[1]
                            val moveNum = parts[2]
                            val finalMove = parts[3]
                            try {
                                processMoveFromServer(moveNum, finalMove) {
                                    showEndGameDialog(getString(R.string.lose_message) + newRating)
                                }
                            } catch (e: Exception) {
                                showEndGameDialog(getString(R.string.lose_message) + newRating)
                            }
                        }
                    }
                } else if (message.startsWith("DRAW")) {
                    CoroutineScope(Dispatchers.Main).launch {
                        val parts = message.split('|')
                        val eventId = if (parts.size == 4) parts[3] else parts[1]
                        val deferred = async { acknowledgeGameEvent(eventId) }
                        deferred.await()

                        if (deferred.getCompleted()) {
                            if (parts.size == 2) {
                                showEndGameDialog(getString(R.string.draw_message))
                            } else {
                                val moveNum = parts[1]
                                val finalMove = parts[2]
                                try {
                                    processMoveFromServer(moveNum, finalMove) {
                                        showEndGameDialog(getString(R.string.draw_message))
                                    }
                                } catch (e: Exception) {
                                    showEndGameDialog(getString(R.string.draw_message))
                                }
                            }
                        }
                    }
                } else if (message.startsWith("BEGIN")) {
                    val parts = message.split('|')
                    CoroutineScope(Dispatchers.Main).launch {
                        val deferred = async { acknowledgeGameEvent(parts[1]) }
                        deferred.await()

                        if (deferred.getCompleted()) {
                            startTimer()

                            if (gameStarted) {
                                return@launch;
                            }

                            gameStarted = true

                            runOnUiThread {
                                val cancelButton: Button =
                                    findViewById(R.id.cancelButton)
                                cancelButton.visibility = View.GONE
                                cancelButton.invalidate()

                                val resignButton: Button =
                                    findViewById(R.id.resignButton)
                                resignButton.visibility = View.VISIBLE
                                resignButton.invalidate()

                                var checkersBoard: CheckerBoardView =
                                    findViewById(R.id.checkerBoardView)
                                checkersBoard.gameStarted = true

                                val status: TextView =
                                    findViewById(R.id.statusTextView)
                                if (playersTurn) {
                                    status.text = getString(R.string.your_turn)
                                } else {
                                    status.text = opponentName + "'s " + getString(R.string.turn)
                                }

                                status.invalidate()
                            }
                        }
                    }
                    val gameId = intent.getStringExtra("gameId")

                    if (gameId != null) {
                        lookupOpponentName(gameId)
                    }
                }
            }

            // Implement other WebSocketListener methods as necessary
        }

        webSocket = webSocketClient.newWebSocket(request, listener)
        val sessionId = StorageUtil.getData("sessionId")
        webSocket.send(sessionId)
        connected = true

        runOnUiThread {
            updateCheckerBoard(logicalBoard, playersTurn, isBlack)
        }

        lifecycleScope.launch(Dispatchers.IO) { // Starts a coroutine in the background thread
            while (connected) {
                val sessionId = StorageUtil.getData("sessionId")
                webSocket.send(sessionId)
                delay(30_000)
            }
            withContext(Dispatchers.Main) {
                // Code to run on the main thread, like updating the UI
            }
        }
    }

    private fun acknowledgeGameEventSync(eventId: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/game/event/" + eventId)
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                //continuation.resume(false)
                Log.e("RESP-F", e.toString())
            }

            override fun onResponse(call: Call, response: Response) {
                Log.e("RESP-S", response.message.toString())
                if (response.isSuccessful) {
                    //setTurn(true)
                } else {
                    //continuation.resume(false)
                }
            }
        })
    }

    private suspend fun acknowledgeGameEvent(eventId: String): Boolean {
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/game/event/" + eventId)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resume(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        continuation.resume(true)
                    } else {
                        continuation.resume(false)
                    }
                }
            })
        }
    }

    private suspend fun processMoveFromServer(
        moveNum: String, moveList: String,
        callback: (() -> Unit)? = null
    ): Boolean {
        Log.e("PROCESSING_MOVE", processingMove.get().toString())
        if (!processingMove.getAndSet(true)) {
            val num = moveNum.toIntOrNull()

            if (num == null) {
                restartApp()
            }

            var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
            val movesToAnimate = arrayListOf<Pair<Pair<Int, Int>, Pair<Int, Int>>>()
            val coordinatePairs = arrayListOf<CoordinatePair>()
            var isKing: Boolean? = null

//            Log.e("NUM", num.toString())
//            Log.e("CUR", currentMove.toString())
            if (num == currentMove) {
                processingMove.set(false)
                return false
            }

            if (num == currentMove + 1) {
                val moves = moveList.split('+')
                var finalEndRow: Int = 0
                var finalEndCol: Int = 0

                for (move in moves) {
                    if (move.isEmpty()) {
                        continue
                    }

                    // Log.e("MOVE", move)
                    val parts = move.split('-')
                    val start = parts[0]
                    // Log.e("START", start)
                    val starts = start.split(',')
                    val startRow =
                        if (starts[0].startsWith("r")) parseOutNumber(starts[0]) else parseOutNumber(
                            starts[1]
                        )
                    val startCol =
                        if (starts[0].startsWith("c")) parseOutNumber(starts[0]) else parseOutNumber(
                            starts[1]
                        )
                    val end = parts[1]
                    val ends = end.split(',')
                    val endRow =
                        if (ends[0].startsWith("r")) parseOutNumber(ends[0]) else parseOutNumber(
                            ends[1]
                        )
                    val endCol =
                        if (ends[0].startsWith("c")) parseOutNumber(ends[0]) else parseOutNumber(
                            ends[1]
                        )
                    finalEndRow = endRow
                    finalEndCol = endCol
                    coordinatePairs.add(
                        CoordinatePair(
                            Coordinate(startCol, startRow),
                            Coordinate(endCol, endRow)
                        )
                    )
                    val fromRow = if (checkersBoard.isBlack) translateNumber(startRow) else startRow
                    val fromCol =
                        if (!checkersBoard.isBlack) translateNumber(startCol) else startCol
                    val toRow = if (checkersBoard.isBlack) translateNumber(endRow) else endRow
                    val toCol = if (!checkersBoard.isBlack) translateNumber(endCol) else endCol

                    movesToAnimate.add(Pair(Pair(fromRow, fromCol), Pair(toRow, toCol)))
                }

                checkersBoard.game!!.doMove(coordinatePairs)

                isKing = checkersBoard.game!!.board!!.getPiece(
                    Coordinate(
                        finalEndCol,
                        finalEndRow
                    )
                ) is King
            } else {
                processingMove.set(false)
                return false
            }

            val squares = mutableListOf<CheckerSquare>()

            for (idx in 0 until movesToAnimate.size) {
                if (idx == 0) {
                    squares.add(
                        findSquareForCoordinates(
                            movesToAnimate[idx].first.first,
                            movesToAnimate[idx].first.second
                        )
                    )
                    squares.add(
                        findSquareForCoordinates(
                            movesToAnimate[idx].second.first,
                            movesToAnimate[idx].second.second
                        )
                    )
                } else {
                    squares.add(
                        findSquareForCoordinates(
                            movesToAnimate[idx].second.first,
                            movesToAnimate[idx].second.second
                        )
                    )
                }
            }

            try {
                if (callback == null) {
                    checkersBoard.doServerMove(squares, isKing!!) {
                        Log.e("PROCESSING_MOVE_CB", "1")
                        setTurn(true)
                        Log.e("PROCESSING_MOVE_CB", "2")
                        startTimer();
                    }
                } else {
                    checkersBoard.doServerMove(squares, isKing!!, callback)
                }
            } catch (e: Exception) {
                Log.e("PROCESSING_MOVE_EX", e.toString())
            }

            return true
        } else {
            return false
        }
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

        Log.e("SET_TURN", myTurn.toString())

        if (myTurn) {
            status.text = "Your turn"
        } else {
            status.text = opponentName + "'s turn"
        }

        this.playersTurn = myTurn
    }

    private fun parseOutNumber(part: String): Int {
        val pieces = part.split(':')
        return pieces[1].toIntOrNull()!!
    }

    private fun lookupOpponentName(gameId: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/game/" + gameId)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {

            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                val accountId = StorageUtil.getData("accountId")

                if (responseBody != null && response.isSuccessful) {
                    val game = ResponseUtil.parseJson(responseBody)

                    if (game["blackAccountId"] == accountId) {
                        if (game["redUsername"] != null) {
                            opponentName = game["redUsername"].toString()
                        }
                    }

                    if (game["redAccountId"] == accountId) {
                        if (game["blackUsername"] != null) {
                            opponentName = game["blackUsername"].toString()
                        }
                    }
                }
            }
        })
    }

    private suspend fun lookupGame(gameId: String): Boolean {
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/game/" + gameId)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    showMessage(getString(R.string.network_error))
                    continuation.resumeWithException(e)
                    finish()
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""

                    if (responseBody == null) {
                        showMessage(getString(R.string.no_server_response_error))
                        finish()
                    }

                    if (response.code == 404 /*NOT_FOUND*/) {
                        showMessage(getString(R.string.game_no_longer_exists_error))
                        finish()
                    }

                    val game = ResponseUtil.parseJson(responseBody)

                    if (game == null || game.isEmpty()) {
                        showMessage(getString(R.string.invalid_server_response_error))
                        finish()
                    }

                    if (game["gameState"] != null) {

                        logicalBoard = Game(game["gameState"])
                        val accountId = StorageUtil.getData("accountId")

                        if (accountId == null) {
                            restartApp()
                        }

                        currentMove = parseCurrentMove(game["gameState"]!!)
                        playersTurn = accountId == game["currentTurnId"]
                        isBlack = accountId == game["blackAccountId"]
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
                                    status.text = getString(R.string.your_turn)
                                } else {
                                    status.text = opponentName + "'s " + getString(R.string.turn)
                                }

                                val checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
                                checkersBoard.gameStarted = true
                            }
                        }
                        response.close()
                        continuation.resume(true)
                    }
                }
            })
        }
    }

    private suspend fun lookupWebSocketServer(): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/websocketservers/server")
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    showMessage(getString(R.string.network_error))
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""
                    continuation.resume(responseBody)
                }
            })
        }
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

    private suspend fun sendMove(): Boolean {
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val gson = Gson()
        val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val requestBody = createMoveListForPost()
        val jsonString = gson.toJson(requestBody)
        val body = RequestBody.create(mediaType, jsonString)
        val sessionId = StorageUtil.getData("sessionId")
        val gameId = intent.getStringExtra("gameId")
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/game/" + sessionId + "/" + gameId + "/move")
            .post(body)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)

            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    showMessage(getString(R.string.network_error))
                    continuation.resume(false)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        if (response.isSuccessful) {
                            currentMove++
                            val checkersBoard: CheckerBoardView =
                                findViewById(R.id.checkerBoardView)
                            checkersBoard.setIsMyTurn(false)
                        } else {
                            val responseBody = response.body?.string()

                            if (responseBody == null) {
                                showMessage(getString(R.string.invalid_server_response_error))
                                continuation.resume(false)
                                return
                            }

                            val game = ResponseUtil.parseJson(responseBody)

                            showMessage(game["message"].toString())
                            continuation.resume(false)
                        }

                        response.close()
                        continuation.resume(true)
                    } catch (e: Exception) {

                    }
                }
            })
        }
    }

    private fun showFailureDialog(message: String, shouldExit: Boolean) {
        runOnUiThread {
            gameStarted = false

            // Create an AlertDialog builder
            val builder = AlertDialog.Builder(this)

            // Set the message to show in the dialog
            builder.setMessage(getString(R.string.network_error_log_back_in) + " " + message)

            // Add a button to close the dialog
            builder.setPositiveButton("Close") { dialog, _ ->
                // User clicked the "Close" button, so dismiss the dialog
                dialog.dismiss()
            }

            // Create and show the AlertDialog
            val dialog = builder.create()

            // Set a dismiss listener on the dialog
            dialog.setOnDismissListener {
                if (shouldExit) {
                    restartApp()
                }
            }

            dialog.show()

            // Optionally, prevent the dialog from being canceled when touched outside
            dialog.setCanceledOnTouchOutside(false)
        }
    }

    private fun showEndGameDialog(message: String) {
        runOnUiThread {
            if (!gameStarted) {
                return@runOnUiThread
            }

            gameStarted = false

            // Create an AlertDialog builder
            val builder = AlertDialog.Builder(this)

            // Set the message to show in the dialog
            builder.setMessage(message)

            // Add a button to close the dialog
            builder.setPositiveButton(getString(R.string.close_button)) { dialog, _ ->
                // User clicked the "Close" button, so dismiss the dialog
                dialog.dismiss()
            }

            // Create and show the AlertDialog
            val dialog = builder.create()

            // Set a dismiss listener on the dialog
            dialog.setOnDismissListener {
                if (mInterstitialAd != null && shouldShowAd()) {
                    mInterstitialAd?.show(this)
                }
                finish()
            }

            dialog.show()

            // Optionally, prevent the dialog from being canceled when touched outside
            dialog.setCanceledOnTouchOutside(false)
        }
    }

    private fun shouldShowAd(): Boolean {
        val sharedPreferences = getSharedPreferences("CheckersPlusPlusAppPrefs", Context.MODE_PRIVATE)
        var numGames = sharedPreferences.getInt("numGames", 3)
        numGames += 1

        if (numGames >= 3) {
            sharedPreferences.edit().putInt("numGames", 0).apply()
            return true
        } else {
            sharedPreferences.edit().putInt("numGames", numGames).apply()
            return false
        }
    }

    override fun onBackPressed() {

    }

    private fun restartApp() {
        val restartIntent = Intent(this, MainActivity::class.java)
        restartIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(restartIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        webSocket.close(1000, "Activity destroyed")
        // Ensure the client dispatcher is properly shut down on app exit
        webSocketClient.dispatcher.executorService.shutdown()
        var checkersBoard: CheckerBoardView = findViewById(R.id.checkerBoardView)
        checkersBoard.releaseResources()
    }

    private fun cancelGame() {
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val sessionId = StorageUtil.getData("sessionId")
        val json = JSONObject()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val gameId = intent.getStringExtra("gameId")
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/game/" + sessionId + "/" + gameId + "/cancel")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    showMessage(getString(R.string.network_error))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (responseBody == null) {
                    runOnUiThread {
                        showMessage(getString(R.string.invalid_server_response_error))
                    }

                    return
                }

                val game = ResponseUtil.parseJson(responseBody)

                if (response.isSuccessful) {
                    val intent = Intent(this@GameActivity, OpenGamesActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    showMessage(game["message"].toString())
                }
            }
        })
    }

    private fun showMessage(message: String) {
        runOnUiThread {
            // Create an AlertDialog builder
            val builder = AlertDialog.Builder(this)

            // Set the message to show in the dialog
            builder.setMessage(message)

            // Add a button to close the dialog
            builder.setPositiveButton(getString(R.string.close_button)) { dialog, _ ->
                // User clicked the "Close" button, so dismiss the dialog
                dialog.dismiss()
            }

            // Create and show the AlertDialog
            val dialog = builder.create()

            // Set a dismiss listener on the dialog
            dialog.setOnDismissListener {

            }

            dialog.show()

            // Optionally, prevent the dialog from being canceled when touched outside
            dialog.setCanceledOnTouchOutside(false)
        }
    }
}