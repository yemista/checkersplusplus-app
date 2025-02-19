package com.checkersplusplus.app

import android.app.AlertDialog
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
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

class RequestVerificationActivity : AppCompatActivity() {
    private var buttonPressed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_request_verification)

        val sendVerificationButton: Button = findViewById(R.id.sendCodeButton)
        sendVerificationButton.setOnClickListener {
            if (buttonPressed) {
                return@setOnClickListener
            }

            sendVerificationCode()
        }
    }

    private fun sendVerificationCode() {
        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val username = usernameEditText.text.toString().trim()

        if (username.isEmpty()) {
            usernameEditText.error = getString(R.string.username_required)
            return
        }

        requestVerificationCode()
    }

    private fun requestVerificationCode() {
        val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
        val username = usernameEditText.text.toString()


        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val json = JSONObject()
        json.put("username", username)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/account/sendVerification")
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle failed network request
                showMessage(getString(R.string.network_error))
                buttonPressed = false
            }

            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string() ?: ""

                if (responseBody == null) {
                    showMessage(getString(R.string.no_server_response_error))
                    return
                }

                val createAccountResponse = ResponseUtil.parseJson(responseBody)

                if (createAccountResponse == null) {
                    showMessage(getString(R.string.invalid_server_response_error))
                    return
                }

                val message = createAccountResponse["message"]

                if (message != null) {
                    showEndGameDialog(message)
                }
            }
        })
    }

    private fun showEndGameDialog(message: String) {
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
                val usernameEditText = findViewById<EditText>(R.id.usernameEditText)
                val username = usernameEditText.text.toString()
                buttonPressed = false
                // Close the activity when the dialog is dismissed
                finish()
                val intent = Intent(this@RequestVerificationActivity, ResetPasswordActivity::class.java)
                intent.putExtra("username", username)
                startActivity(intent)
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