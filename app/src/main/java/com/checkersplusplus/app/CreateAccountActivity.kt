package com.checkersplusplus.app

import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException

class CreateAccountActivity : AppCompatActivity() {

    private val client = OkHttpClient()
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
            if (buttonPressed) {
                return@setOnClickListener
            }

            buttonPressed = true
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

            // Asynchronously make the POST request
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val responseBody = makePostRequest(email, username, password, confirmPassword)

                    withContext(Dispatchers.Main) {
                        if (responseBody == null || responseBody == "") {
                            showMessage("No response from server. Try again soon")
                        }

                        val createAccountResponse = ResponseUtil.parseJson(responseBody)

                        if (createAccountResponse == null) {
                            showMessage("Invalid response from server. Try again soon")
                        }

                        val message = createAccountResponse["message"]

                        if (message != null) {
                            showResponseDialog(message)
                        }
                    }

                    buttonPressed = false
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showMessage(e.toString())
                        buttonPressed = false
                    }
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
            .url("http://" + BuildConfig.BASE_URL + "/account/create")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            return response.body?.string() ?: ""
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