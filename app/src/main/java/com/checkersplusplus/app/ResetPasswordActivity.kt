package com.checkersplusplus.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import java.util.regex.Pattern

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var verificationCodeEditText: EditText
    private var buttonPressed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        usernameEditText = findViewById(R.id.usernameEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        verificationCodeEditText = findViewById(R.id.verificationCodeEditText)

        // Retrieve the username passed from the previous activity
        val username = intent.getStringExtra("username")
        usernameEditText.setText(username)

        val submitButton: Button = findViewById(R.id.changePasswordButton)
        submitButton.setOnClickListener {
            if (buttonPressed) {
                return@setOnClickListener
            }

            changePassword()
        }
    }

    private fun changePassword() {
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val verificationCode = verificationCodeEditText.text.toString()

        if (validateInput(password, confirmPassword, verificationCode)) {
            submitPasswordChange()
        }
    }

    private fun validateInput(password: String, confirmPassword: String, verificationCode: String): Boolean {
        if (password.isEmpty() || confirmPassword.isEmpty() || verificationCode.isEmpty()) {
            // Show error messages for empty fields
            if (password.isEmpty()) passwordEditText.error = "Password is required"
            if (confirmPassword.isEmpty()) confirmPasswordEditText.error = "Confirm Password is required"
            if (verificationCode.isEmpty()) verificationCodeEditText.error = "Verification Code is required"
            return false
        }

        if (password != confirmPassword) {
            confirmPasswordEditText.error = "Passwords do not match"
            return false
        }

        if (!isValidPassword(password)) {
            passwordEditText.error = "Password must be at least 8 characters and include a mix of upper and lower case letters and numbers"
            return false
        }

        return true
    }

    private fun isValidPassword(password: String): Boolean {
        val passwordPattern = "^(?=.*[0-9])(?=.*[a-zA-Z]).{8,}$"
        val pattern = Pattern.compile(passwordPattern)
        val matcher = pattern.matcher(password)
        return matcher.matches()
    }

    private fun submitPasswordChange() {
        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()
        val verificationCode = verificationCodeEditText.text.toString()

        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val json = JSONObject()
        json.put("username", username)
        json.put("password", password)
        json.put("confirmPassword", confirmPassword)
        json.put("verificationCode", verificationCode)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/account/resetPassword")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle failed network request
                showMessage("Network error. Failed to connect: ${e.message}")
                buttonPressed = false
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""

                if (responseBody == null) {
                    showMessage("No response from server. Try again soon")
                    return
                }

                val createAccountResponse = ResponseUtil.parseJson(responseBody)

                if (createAccountResponse == null) {
                    showMessage("Invalid response from server. Try again soon")
                    return
                }

                val message = createAccountResponse["message"]

                if (message != null) {
                    runOnUiThread {
                        if (response.isSuccessful) {
                            showEndGameDialog(message, true)
                        } else {
                            showEndGameDialog(message, false)
                        }
                        buttonPressed = false
                    }
                }
            }
        })
    }

    private fun showEndGameDialog(message: String, complete: Boolean) {
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
                if (complete) {
                    finishAffinity() // Finish all other activities in the task
                    startActivity(Intent(this@ResetPasswordActivity, MainActivity::class.java))
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
