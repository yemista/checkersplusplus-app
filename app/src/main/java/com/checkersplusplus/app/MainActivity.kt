package com.checkersplusplus.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var createAccountButton: Button
    private lateinit var verifyAccountButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        StorageUtil.init(applicationContext)
        setContentView(R.layout.initial_screen)

        // Initialize views
        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        loginButton = findViewById(R.id.loginButton)
        createAccountButton = findViewById(R.id.createAccountButton)
        verifyAccountButton = findViewById(R.id.verifyAccountButton)

        // Set up the button click listeners
        loginButton.setOnClickListener {
            performLogin()
        }
        createAccountButton.setOnClickListener {
            val intent = Intent(this, CreateAccountActivity::class.java)
            startActivity(intent)
        }
        verifyAccountButton.setOnClickListener {
            val intent = Intent(this, VerifyActivity::class.java)
            startActivity(intent)
        }
    }

    private fun performLogin() {
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()

        if (username.isBlank() || password.isBlank()) {
            Toast.makeText(this, "Username or password cannot be empty", Toast.LENGTH_LONG).show()
            return
        }

        // Prepare JSON body
        val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val jsonBody = "{\"username\":\"$username\",\"password\":\"$password\"}"

        // Create request
        val client = OkHttpClient()
        val requestBody = jsonBody.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("http://" + BuildConfig.BASE_URL + "/account/login")
            .post(requestBody)
            .build()

        // Make asynchronous network call
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle failed network request
                runOnUiThread {
                    Toast.makeText(applicationContext, "Network error. Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()

                if (responseBody == null) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "No response from server. Try again soon", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                val loginResponse = ResponseUtil.parseJson(responseBody)

                if (loginResponse == null) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Invalid response from server. Try again soon", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                val message = loginResponse["message"]

                if (message != null) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
                    }
                }

                if (!response.isSuccessful) {
                    return
                }

                val sessionId = loginResponse["sessionId"]
                val gameId = loginResponse["gameId"]
                val accountId = loginResponse["accountId"]

                // Should never happen
                if (accountId == null || sessionId == null) {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Server response missing data. Try again soon", Toast.LENGTH_LONG).show()
                    }
                    return
                }

                if (sessionId != null) {
                    StorageUtil.saveData("sessionId", sessionId)
                }

                if (accountId != null) {
                    StorageUtil.saveData("accountId", accountId)
                }

                if (gameId != null) {
                    val intent = Intent(this@MainActivity, GameActivity::class.java)
                    intent.putExtra("gameId", gameId)
                    startActivity(intent)
                } else {
                    val intent = Intent(this@MainActivity, OpenGamesActivity::class.java)
                    startActivity(intent)
                }

            }
        })
    }

}