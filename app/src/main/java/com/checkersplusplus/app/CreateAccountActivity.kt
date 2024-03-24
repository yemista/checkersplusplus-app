package com.checkersplusplus.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CreateAccountActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
        .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
        .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
        .build()

    private var buttonPressed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_account)

        val emailEditText: EditText = findViewById(R.id.emailEditTextCreateAccount)
        val usernameEditText: EditText = findViewById(R.id.usernameEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)
        val confirmPasswordEditText: EditText = findViewById(R.id.confirmPasswordEditTextCreateAccount)
        val createAccountButton: Button = findViewById(R.id.createAccountButton)

        createAccountButton.setOnClickListener {

            val email = emailEditText.text.toString()
            val username = usernameEditText.text.toString()
            val password = passwordEditText.text.toString()
            val confirmPassword = confirmPasswordEditText.text.toString()

            if (!isValidEmail(email)) {
                emailEditText.error = getString(R.string.invalid_email_error)
                return@setOnClickListener
            }

            if (!isValidUsername(username)) {
                usernameEditText.error = getString(R.string.invalid_username_error)
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                confirmPasswordEditText.error = getString(R.string.password_error)
                return@setOnClickListener
            }

            val networkScope = CoroutineScope(Dispatchers.IO)
            networkScope.launch {
                try {
                    val responseBody = async { makePostRequest(email, username, password, confirmPassword) }
                    responseBody.await()
                    val response = responseBody.getCompleted()

                    if (response == null || response == "") {
                        showMessage(getString(R.string.no_server_response_error), null)
                    }

                    val createAccountResponse = ResponseUtil.parseJson(response)

                    if (createAccountResponse == null) {
                        showMessage(getString(R.string.invalid_server_response_error), null)
                    }

                    val message = createAccountResponse["message"]

                    if (message != null) {
                        showResponseDialog(message)
                    }
                } catch (e: CancellationException) {
                    // Ignore cancellation
                } catch (e: Exception) {
                }
            }
        }
    }

    private suspend fun makePostRequest(email: String, username: String, password: String, confirmPassword: String): String {
        val json = JSONObject()
        json.put("email", email)
        json.put("username", username)
        json.put("password", password)
        json.put("confirmPassword", confirmPassword)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/account/create")
            .post(requestBody)
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    showMessage(getString(R.string.no_server_response_error), null)
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""
                    continuation.resume(responseBody)
                }
            })
        }
    }

    private fun isValidEmail(email: String): Boolean {
        return Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun isValidUsername(username: String): Boolean {
        return username.length in 3..30
    }

    private fun isValidPassword(password: String): Boolean {
//        val passwordPattern = "^(?=.*[0-9])(?=.*[a-zA-Z]).{8,}$"
//        return password.matches(passwordPattern.toRegex())
        return true
    }

    private fun showResponseDialog(message: String) {
        runOnUiThread {
            // Create an AlertDialog builder
            val builder = AlertDialog.Builder(this)

            if (message.startsWith("Account created successfully")) {
//                val translatedMessage = getString(R.string.create_accout_success)
//                builder.setMessage(translatedMessage)
                val networkScope = CoroutineScope(Dispatchers.IO)
                networkScope.launch {
                    try {
                        performLogin()
                    } catch (e: CancellationException) {
                        // Ignore cancellation
                    } catch (e: Exception) {
                    }
                }
            } else {
                if (message.startsWith("Email address is already in use")) {
                    val translatedMessage = getString(R.string.email_address_in_use_error)
                    builder.setMessage(translatedMessage)
                } else if (message.startsWith("Username is already in use")) {
                    val translatedMessage = getString(R.string.username_in_use_error)
                    builder.setMessage(translatedMessage)
                } else {
                    builder.setMessage(message)
                }

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

    private suspend fun performLogin(): String {
        val usernameEditText: EditText = findViewById(R.id.usernameEditText)
        val passwordEditText: EditText = findViewById(R.id.passwordEditText)
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()

        if (username.isBlank() || password.isBlank()) {
            showMessage(getString(R.string.username_password_blank_error), null)
            return ""
        }

        val trimmedUsername = username.trim()

        // Prepare JSON body
        val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val jsonBody = "{\"username\":\"$trimmedUsername\",\"password\":\"$password\"}"

        // Create request
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val requestBody = jsonBody.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/account/login")
            .post(requestBody)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()

                    if (responseBody == null) {
                        showMessage(getString(R.string.no_server_response_error), null)
                        return
                    }

                    val loginResponse = ResponseUtil.parseJson(responseBody)

                    if (loginResponse == null) {
                        showMessage(getString(R.string.invalid_server_response_error), null)
                        return
                    }

                    val message = loginResponse["message"]

                    if (response.isSuccessful) {
                        val sessionId = loginResponse["sessionId"]
                        val gameId = loginResponse["gameId"]
                        val accountId = loginResponse["accountId"]
                        val tutorial = loginResponse["tutorial"]

                        //Log.e("LOGIN", loginResponse.toString())

                        // Should never happen
                        if (accountId == null || sessionId == null) {
                            showMessage(getString(R.string.invalid_server_response_error), null)
                            return
                        }

                        if (tutorial != null) {
                            StorageUtil.saveData("tutorial", tutorial)
                        }

                        if (sessionId != null) {
                            StorageUtil.saveData("sessionId", sessionId)
                        }

                        if (accountId != null) {
                            StorageUtil.saveData("accountId", accountId)
                        }

                        buttonPressed = false
                        var intent: Intent

                        if (gameId != null) {
                            intent = Intent(this@CreateAccountActivity, GameActivity::class.java)
                            intent.putExtra("gameId", gameId)
                        } else {
                            intent = Intent(this@CreateAccountActivity, OpenGamesActivity::class.java)
                        }

                        if (message != null) {
                            if (message.contains("successful")) {
                                runOnUiThread {
                                    Toast.makeText(
                                        applicationContext,
                                        message,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    buttonPressed = false
                                }
                                startActivity(intent)
                            } else {
                                showMessage(message, intent)
                            }
                        } else {
                            startActivity(intent)
                        }
                        continuation.resume(responseBody)
                    } else {
                        if (message != null) {
                            showMessage(message, null)
                        }

                        continuation.resumeWithException(IOException("Failed to load data"))
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            })

            continuation.invokeOnCancellation {
                try {
                    call.cancel()
                } catch (ex: Throwable) {
                    // Ignore cancellation exception
                }
            }
        }
    }

    private fun showMessage(message: String, intent: Intent?) {
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
                if (intent != null) {
                    startActivity(intent)
                }
            }

            dialog.show()

            // Optionally, prevent the dialog from being canceled when touched outside
            dialog.setCanceledOnTouchOutside(false)
        }
    }
}