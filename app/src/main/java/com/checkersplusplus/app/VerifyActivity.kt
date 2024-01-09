package com.checkersplusplus.app

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class VerifyActivity : AppCompatActivity() {

    private lateinit var verificationCodeEditText: EditText
    private lateinit var usernameEditText: EditText
    private var buttonPressed: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify)

        verificationCodeEditText = findViewById(R.id.verificationCodeEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        val sendButton: Button = findViewById(R.id.sendVerificationCodeButton)
        val resendButton: Button = findViewById(R.id.resendVerificationCodeButton)

        sendButton.setOnClickListener {
            if (buttonPressed) {
                return@setOnClickListener
            }

            val verificationCode = verificationCodeEditText.text.toString()

            if (verificationCode == null) {
                verificationCodeEditText.error = "Please enter a verification code"
                return@setOnClickListener
            }

            val username = usernameEditText.text.toString()

            if (username == null) {
                usernameEditText.error = "Please enter a user name"
                return@setOnClickListener
            }

            val client = OkHttpClient()
            val json = JSONObject()
            json.put("verificationCode", verificationCode)
            json.put("username", username)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("http://" + BuildConfig.BASE_URL + "/account/verify")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Handle failed network request
                    showMessage("Network error. Failed to connect: ${e.message}", false)
                    buttonPressed = false
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""

                    if (responseBody == null) {
                        showMessage("No response from server. Try again soon", false)
                        return
                    }

                    val createAccountResponse = ResponseUtil.parseJson(responseBody)

                    if (createAccountResponse == null) {
                        showMessage("Invalid response from server. Try again soon", false)
                        return
                    }

                    val message = createAccountResponse["message"]

                    if (message != null) {
                        showMessage(message.toString(), false)
                    }

                    if (response.isSuccessful) {
                        showMessage(message.toString(), true)
                    }

                    buttonPressed = false
                }
            })
        }

        resendButton.setOnClickListener {
            val username = usernameEditText.text.toString()

            if (username == null) {
                usernameEditText.error = "Please enter a user name"
                return@setOnClickListener
            }

            val client = OkHttpClient()
            val json = JSONObject()
            json.put("username", username)

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.toString().toRequestBody(mediaType)
            val request = Request.Builder()
                .url("http://" + BuildConfig.BASE_URL + "/account/sendVerification")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    showMessage("Network error. Failed to connect: ${e.message}", false)
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""

                    if (responseBody == null) {
                        showMessage("No response from server. Try again soon", false)
                        return
                    }

                    val createAccountResponse = ResponseUtil.parseJson(responseBody)

                    if (createAccountResponse == null) {
                        showMessage("Invalid response from server. Try again soon", false)
                        return
                    }

                    val message = createAccountResponse["message"]

                    if (message != null) {
                        if (response.isSuccessful) {
                            showMessage(message.toString(), false)
                        } else {
                            showMessage(message.toString(), false)
                        }
                    }
                }
            })
        }
    }

    private fun showMessage(message: String, complete: Boolean) {
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
                    val intent = Intent(this@VerifyActivity, MainActivity::class.java)
                    startActivity(intent)
                }
            }

            dialog.show()

            // Optionally, prevent the dialog from being canceled when touched outside
            dialog.setCanceledOnTouchOutside(false)
        }
    }
}
