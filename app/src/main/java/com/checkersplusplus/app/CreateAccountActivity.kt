package com.checkersplusplus.app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
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
import org.json.JSONObject
import java.io.IOException

class CreateAccountActivity : AppCompatActivity() {

    private val client = OkHttpClient()

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

            // Asynchronously make the POST request
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val response = makePostRequest(email, username, password)
                    withContext(Dispatchers.Main) {
                        // Handle the response on the main thread
                        // Example: Toast.makeText(applicationContext, response, Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        // Handle error
                    }
                }
            }

            // If all validations pass
            Toast.makeText(this, "Account Created Successfully", Toast.LENGTH_LONG).show()
            // Proceed with account creation logic
        }
    }

    private suspend fun makePostRequest(email: String, username: String, password: String): String {
        val json = JSONObject()
        json.put("email", email)
        json.put("username", username)
        json.put("password", password)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(BuildConfig.BASE_URL + "/account/create")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

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
        val passwordPattern = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z]).{8,}$"
        return password.matches(passwordPattern.toRegex())
    }
}