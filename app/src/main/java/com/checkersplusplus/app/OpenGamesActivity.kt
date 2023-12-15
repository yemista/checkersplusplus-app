package com.checkersplusplus.app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_games)

        val recyclerView: RecyclerView = findViewById(R.id.myRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        val createGameButton: Button = findViewById(R.id.createGameButton)
        createGameButton.setOnClickListener {
            createGame()
        }

        val refreshGamesButton: Button = findViewById(R.id.refreshGamesButton)
        refreshGamesButton.setOnClickListener {
            fetchDataFromServer()
        }

        fetchDataFromServer()
    }

    override fun onResume() {
        super.onResume()
        fetchDataFromServer()
    }


    fun parseResponse(responseData: String?): List<OpenGameListItem> {
        if (responseData == null || responseData == "") {
            runOnUiThread {
                Toast.makeText(
                    applicationContext,
                    "No response from server. Try again soon",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        val openGameResponse = responseData?.let { ResponseUtil.parseJsonArray(it) }

        if (openGameResponse == null) {
            runOnUiThread {
                Toast.makeText(applicationContext, "Invalid response from server. Try again soon", Toast.LENGTH_LONG).show()
            }
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
                    val intent = Intent(this@OpenGamesActivity, GameActivity::class.java)
                    intent.putExtra("gameId", gameId)
                    startActivity(intent)
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, game["message"], Toast.LENGTH_LONG).show()
                    }
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
        val request = Request.Builder()
            .url("http://" + BuildConfig.BASE_URL + "/game/open")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Network error. Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
                }
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

    private fun createGame() {
        val client = OkHttpClient()
        val sessionId = StorageUtil.getData("sessionId")
        val json = JSONObject()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("http://" + BuildConfig.BASE_URL + "/game/" + sessionId +"/create")
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
                    val intent = Intent(this@OpenGamesActivity, GameActivity::class.java)
                    intent.putExtra("gameId", game["gameId"])
                    startActivity(intent)
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, game["message"], Toast.LENGTH_LONG).show()
                    }
                }
            }
        })
    }
}