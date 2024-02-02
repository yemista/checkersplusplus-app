package com.checkersplusplus.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
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
                emailEditText.error = "Invalid email address"
                return@setOnClickListener
            }

            if (!isValidUsername(username)) {
                usernameEditText.error = "Username must be 3-20 characters long"
                return@setOnClickListener
            }

            if (!isValidPassword(password)) {
                passwordEditText.error = "Password must be at least 8 characters and include a mix of upper and lower case letters, and numbers"
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                confirmPasswordEditText.error = "Passwords do not match"
                return@setOnClickListener
            }

            val networkScope = CoroutineScope(Dispatchers.IO)
            networkScope.launch {
                try {
                    val responseBody = async { makePostRequest(email, username, password, confirmPassword) }
                    responseBody.await()
                    val response = responseBody.getCompleted()

                    if (response == null || response == "") {
                        showMessage("No response from server. Try again soon")
                    }

                    val createAccountResponse = ResponseUtil.parseJson(response)

                    if (createAccountResponse == null) {
                        showMessage("Invalid response from server. Try again soon")
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
                    showMessage("Connection error. Please try again")
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
        return username.length in 3..20
    }

    private fun isValidPassword(password: String): Boolean {
        val passwordPattern = "^(?=.*[0-9])(?=.*[a-zA-Z]).{8,}$"
        return password.matches(passwordPattern.toRegex())
    }

    private fun showResponseDialog(message: String) {
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
                if (message.startsWith("Account created successfully")) {
                    val intent =
                        Intent(this@CreateAccountActivity, VerifyActivity::class.java)
                    startActivity(intent)
                }
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