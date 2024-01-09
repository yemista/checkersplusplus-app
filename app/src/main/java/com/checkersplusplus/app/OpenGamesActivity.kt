package com.checkersplusplus.app

import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.InputFilter
import android.text.Spanned
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class OpenGamesActivity : AppCompatActivity() {
    private var buttonPressed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_games)

        val recyclerView: RecyclerView = findViewById(R.id.myRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val createGameButton: Button = findViewById(R.id.createGameButton)
        createGameButton.setOnClickListener {
            if (buttonPressed) {
                return@setOnClickListener
            }

            showMoveFirstDialog()
        }

        val refreshGamesButton: Button = findViewById(R.id.refreshGamesButton)
        refreshGamesButton.setOnClickListener {
            fetchDataFromServer()
        }

        val filterGamesButton: Button = findViewById(R.id.filterGamesButton)
        filterGamesButton.setOnClickListener {
            val layoutInputs = findViewById<LinearLayout>(R.id.layoutInputs)

            if (layoutInputs.visibility == View.GONE) {
                layoutInputs.visibility = View.VISIBLE
            } else {
                layoutInputs.visibility = View.GONE
                fetchDataFromServer()
            }
        }

        // Set up Spinners
        setupSpinner(findViewById(R.id.spinnerSortBy), arrayOf("Creation date", "Opponent Rating"))
        setupSpinner(findViewById(R.id.spinnerSortDirection), arrayOf("Ascending", "Descending"))

        // Set input filters
        findViewById<EditText>(R.id.editTextLowestRating).filters = arrayOf(InputFilterMinMax(0, 10000))
        findViewById<EditText>(R.id.editTextHighestRating).filters = arrayOf(InputFilterMinMax(0, 10000))
        fetchDataFromServer()
    }

    private fun setupSpinner(spinner: Spinner, items: Array<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        spinner.adapter = adapter
    }

    class InputFilterMinMax(private val min: Int, private val max: Int) : InputFilter {
        override fun filter(source: CharSequence, start: Int, end: Int, dest: Spanned, dstart: Int, dend: Int): CharSequence? {
            try {
                val input = (dest.subSequence(0, dstart).toString() + source + dest.subSequence(dend, dest.length)).toInt()
                if (isInRange(min, max, input))
                    return null
            } catch (nfe: NumberFormatException) { }
            return ""
        }

        private fun isInRange(a: Int, b: Int, c: Int): Boolean {
            return if (b > a) c in a..b else c in b..a
        }
    }


    override fun onResume() {
        super.onResume()
        fetchDataFromServer()
    }


    fun parseResponse(responseData: String?): List<OpenGameListItem> {
        if (responseData == null || responseData == "") {
            showMessage("No response from server. Try again soon")
            return emptyList()
        }

        val openGameResponse = responseData?.let { ResponseUtil.parseJsonArray(it) }

        if (openGameResponse == null) {
            showMessage("Invalid response from server. Try again soon")
            return emptyList()
        }

        if (openGameResponse != null) {
            val retList = mutableListOf<OpenGameListItem>()

            for (i in 0 until openGameResponse.length()) {
                val jsonObject = openGameResponse.getJSONObject(i)
                var redUsername = "open"

                if (jsonObject.has("redUsername")) {
                    redUsername = jsonObject.getString("redUsername")
                }

                var blackUsername = "open"

                if (jsonObject.has("blackUsername")) {
                    blackUsername = jsonObject.getString("blackUsername")
                }

                val gameId = jsonObject.getString("gameId")
                retList.add(OpenGameListItem(blackUsername, redUsername, gameId))
            }

            if (retList.size == 0) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "No games available. Try again soon", Toast.LENGTH_LONG).show()
                }
            }

            return retList
        } else {
            runOnUiThread {
                Toast.makeText(applicationContext, "No games available. Try again soon", Toast.LENGTH_LONG).show()
            }
        }

        return listOf<OpenGameListItem>()
    }

    private fun joinGame(gameId: String) {
        val client = OkHttpClient()
        val sessionId = StorageUtil.getData("sessionId")
        val json = JSONObject()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("http://" + BuildConfig.BASE_URL + "/game/" + sessionId + "/" + gameId + "/join")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showMessage("Network error. Failed to connect: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (responseBody == null) {
                    showMessage("Invalid response from server. Try again soon")

                    return
                }

                val game = ResponseUtil.parseJson(responseBody)

                if (response.isSuccessful) {
                    val intent = Intent(this@OpenGamesActivity, GameActivity::class.java)
                    intent.putExtra("gameId", gameId)
                    startActivity(intent)
                } else {
                    showMessage(game["message"].toString())
                }
            }
        })
    }

    fun updateUI(listItems: List<OpenGameListItem>) {
        val adapter = OpenGameListAdapter(listItems) { joinId ->
            joinGame(joinId)
        }
        val recyclerView: RecyclerView = findViewById(R.id.myRecyclerView)
        val dividerHeight = resources.getDimensionPixelSize(R.dimen.divider_height) // Define this in your dimens.xml
        val dividerColor = ContextCompat.getColor(this, R.color.divider_color) // Define this in your colors.xml
        recyclerView.addItemDecoration(DividerItemDecoration(dividerHeight, dividerColor))

        recyclerView.adapter = adapter
    }

    private fun fetchDataFromServer() {
        val client = OkHttpClient()
        var openGamesUrl = "http://" + BuildConfig.BASE_URL + "/game/open?"
        val spinnerSortBy: Spinner = findViewById(R.id.spinnerSortBy)

        openGamesUrl += if (spinnerSortBy.selectedItem == "Opponent Rating") {
            "sortBy=creatorRating&"
        } else {
            "sortBy=created&"
        }

        val spinnerSortDirection: Spinner = findViewById(R.id.spinnerSortDirection)

        openGamesUrl += if (spinnerSortDirection.selectedItem == "Descending") {
            "sortDirection=desc&"
        } else {
            "sortDirection=asc&"
        }

        val editTextLowestRating: EditText = findViewById(R.id.editTextLowestRating)

        if (editTextLowestRating.text != null && editTextLowestRating.text.length > 0) {
            openGamesUrl += "ratingLow=" + editTextLowestRating.text + "&"
        }

        val editTextHighestRating: EditText = findViewById(R.id.editTextHighestRating)

        if (editTextHighestRating.text != null && editTextHighestRating.text.length > 0) {
            openGamesUrl += "ratingHigh=" + editTextHighestRating.text + "&"
        }

        val editTextNumberOfGames: EditText = findViewById(R.id.editTextNumberOfGames)

        if (editTextNumberOfGames.text != null && editTextNumberOfGames.text.length > 0) {
            openGamesUrl += "pageSize=" + editTextNumberOfGames.text + "&"
        }

        Log.e("URL", openGamesUrl)

        val request = Request.Builder()
            .url(openGamesUrl)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showMessage("Network error. Failed to connect: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    val responseData = response.body?.string()
                    val listItems = parseResponse(responseData)
                    runOnUiThread {
                        updateUI(listItems)
                    }
                }
            }
        })
    }

    private fun createGame(moveFirst: Boolean) {
        val client = OkHttpClient()
        val sessionId = StorageUtil.getData("sessionId")

        val json = JSONObject()
        json.put("moveFirst", moveFirst.toString())

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("http://" + BuildConfig.BASE_URL + "/game/" + sessionId +"/create")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                showMessage("Network error. Failed to connect: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (responseBody == null) {
                    showMessage("Invalid response from server. Try again soon")

                    return
                }

                val game = ResponseUtil.parseJson(responseBody)

                if (response.isSuccessful) {
                    val intent = Intent(this@OpenGamesActivity, GameActivity::class.java)
                    intent.putExtra("gameId", game["gameId"])
                    startActivity(intent)
                } else {
                    showMessage(game["message"].toString())
                }
            }
        })
    }

    private fun showMoveFirstDialog() {
        runOnUiThread {
            // Create an AlertDialog builder
            val builder = AlertDialog.Builder(this)

            // Set the message to show in the dialog
            builder.setMessage("Do you want to go first?")

            // Add a button to close the dialog
            builder.setPositiveButton("Yes") { dialog, _ ->
                dialog.dismiss()
                createGame(true)
            }

            builder.setNegativeButton("No") { dialog, _ ->
                // User clicked the "Close" button, so dismiss the dialog
                dialog.dismiss()
                createGame(false)
            }

            // Create and show the AlertDialog
            val dialog = builder.create()

//            // Set a dismiss listener on the dialog
            dialog.setOnDismissListener {
                buttonPressed = false
            }

            dialog.show()

            // Optionally, prevent the dialog from being canceled when touched outside
            dialog.setCanceledOnTouchOutside(false)
        }
    }

    private fun showMessage(message: String) {
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

            }

            dialog.show()

            // Optionally, prevent the dialog from being canceled when touched outside
            dialog.setCanceledOnTouchOutside(false)
        }
    }
}