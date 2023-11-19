package com.checkersplusplus.app

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

class OpenGamesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_open_games)

        val recyclerView: RecyclerView = findViewById(R.id.myRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)

        fetchDataFromServer()
    }

    fun parseResponse(responseData: String?): List<OpenGameListItem> {
        val myList = listOf(
            OpenGameListItem("1", null, "31"),
            OpenGameListItem("1", null, "31"),
            OpenGameListItem("1", null, "31"),
            OpenGameListItem("1", null, "31"),
            OpenGameListItem("1", null, "31"),
            OpenGameListItem("1", null, "31"),
            OpenGameListItem("1", null, "31"),
            OpenGameListItem("1", null, "31"),
            OpenGameListItem("1", null, "31"),
            OpenGameListItem(null, "2", "31"),
            OpenGameListItem(null, "2", "31"),
            OpenGameListItem(null, "2", "31"),
            OpenGameListItem(null, "2", "31"),
            OpenGameListItem(null, "2", "31"),
            OpenGameListItem(null, "2", "31"),
            OpenGameListItem(null, "2", "31"),
            OpenGameListItem(null, "2", "31"),
            OpenGameListItem(null, "2", "31"))
        return myList
    }

    fun updateUI(listItems: List<OpenGameListItem>) {
        val adapter = OpenGameListAdapter(listItems) { joinId ->
            val intent = Intent(this, GameActivity::class.java)
            intent.putExtra("gameId", joinId)
            startActivity(intent)
        }
        val recyclerView: RecyclerView = findViewById(R.id.myRecyclerView)
        val dividerHeight = resources.getDimensionPixelSize(R.dimen.divider_height) // Define this in your dimens.xml
        val dividerColor = ContextCompat.getColor(this, R.color.divider_color) // Define this in your colors.xml
        recyclerView.addItemDecoration(DividerItemDecoration(dividerHeight, dividerColor))

        recyclerView.adapter = adapter
    }

    fun fetchDataFromServer() {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(BuildConfig.BASE_URL + "/game/open")
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle the error
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
}