package com.checkersplusplus.app

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verify)

        verificationCodeEditText = findViewById(R.id.verificationCodeEditText)
        usernameEditText = findViewById(R.id.usernameEditText)
        val sendButton: Button = findViewById(R.id.sendVerificationCodeButton)
        val resendButton: Button = findViewById(R.id.resendVerificationCodeButton)

        sendButton.setOnClickListener {
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
                .url(BuildConfig.BASE_URL + "/account/verify")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Handle failed network request
                    runOnUiThread {
                        Toast.makeText(applicationContext,
                            "Network error. Failed to connect: ${e.message}",
                            Toast.LENGTH_LONG)
                            .show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""

                    if (responseBody == null) {
                        runOnUiThread {
                            Toast.makeText(
                                applicationContext,
                                "No response from server. Try again soon",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    val createAccountResponse = ResponseUtil.parseJson(responseBody)

                    if (createAccountResponse == null) {
                        runOnUiThread {
                            Toast.makeText(
                                applicationContext,
                                "Invalid response from server. Try again soon",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    val message = createAccountResponse["message"]

                    if (message != null) {
                        runOnUiThread {
                            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                        }
                    }

                    if (response.isSuccessful) {
                        val intent = Intent(this@VerifyActivity, MainActivity::class.java)
                        startActivity(intent)
                    }
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
                .url(BuildConfig.BASE_URL + "/account/sendVerification")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Handle failed network request
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Network error. Failed to connect: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string() ?: ""

                    if (responseBody == null) {
                        runOnUiThread {
                            Toast.makeText(
                                applicationContext,
                                "No response from server. Try again soon",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    val createAccountResponse = ResponseUtil.parseJson(responseBody)

                    if (createAccountResponse == null) {
                        runOnUiThread {
                            Toast.makeText(
                                applicationContext,
                                "Invalid response from server. Try again soon",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }

                    val message = createAccountResponse["message"]

                    if (message != null) {
                        runOnUiThread {
                            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
            })
        }
    }
}
