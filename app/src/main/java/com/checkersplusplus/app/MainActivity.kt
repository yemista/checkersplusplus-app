package com.checkersplusplus.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class MainActivity : AppCompatActivity() {

    private lateinit var usernameEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var loginButton: Button
    private lateinit var createAccountButton: Button
    private lateinit var verifyAccountButton: Button
    private lateinit var resetPasswordButton: Button
    private var buttonPressed: Boolean = false
    private val lock = Any()
    private val loginSuccessful: Boolean = false

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
        resetPasswordButton = findViewById(R.id.resetPasswordButton)

        // Set up the button click listeners
        loginButton.setOnClickListener {
            val networkScope = CoroutineScope(Dispatchers.IO)
            networkScope.launch {
                try {
                    performLogin()
                } catch (e: CancellationException) {
                    // Ignore cancellation
                } catch (e: Exception) {
                }
            }
        }
        val emailButton = findViewById<Button>(R.id.emailButton)
        emailButton.setOnClickListener {
            sendEmail()
        }
        createAccountButton.setOnClickListener {
            val intent = Intent(this, CreateAccountActivity::class.java)
            startActivity(intent)
        }
        verifyAccountButton.setOnClickListener {
            val intent = Intent(this, VerifyActivity::class.java)
            startActivity(intent)
        }
        resetPasswordButton.setOnClickListener {
            val intent = Intent(this, RequestVerificationActivity::class.java)
            startActivity(intent)
        }
        val textViewLink = findViewById<TextView>(R.id.forgotUsernameLink)

        textViewLink.setOnClickListener {
            showUsernameDialog()
        }

        val leaderboardButton: Button = findViewById(R.id.leaderboard)
        leaderboardButton.setOnClickListener {
            try {
                val url = "https://leaderboard.checkersplusplus.com"
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse(url)
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle the error or show a message to the user
            }
        }

        val checkboxSaveUsername = findViewById<CheckBox>(R.id.checkboxSaveUsername)
        val sharedPreferences = getSharedPreferences("CheckersPlusPlusAppPrefs", Context.MODE_PRIVATE)
        val savedUsername = sharedPreferences.getString("username", null)
        savedUsername?.let {
            usernameEditText.setText(it)
            checkboxSaveUsername.isChecked = true
        }

        val savedPassword = sharedPreferences.getString("password", null)
        savedPassword?.let {
            passwordEditText.setText(it)
            checkboxSaveUsername.isChecked = true
        }

        checkboxSaveUsername.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val builder = AlertDialog.Builder(this)
                builder.setTitle("Save login information")
                builder.setMessage("Do not check this box if this is a shared device.")

                // Setting the OK Button
                builder.setPositiveButton("Yes") { dialog, which ->
                    val username = usernameEditText.text.toString()
                    val password = passwordEditText.text.toString()
                    saveLoginInformation(username, password)
                }

                // Setting the Cancel Button
                builder.setNegativeButton("No") { dialog, which ->
                    dialog.dismiss() // Simply dismiss the dialog
                }

                // Create and show the dialog
                builder.create().show()

            } else {
                // Clear the saved username
                saveLoginInformation(null, null)
            }
        }

        // Configure Google Sign-In options
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Launch the sign-in intent when the button is clicked
//        val signInButton: com.google.android.gms.common.SignInButton = findViewById(R.id.sso_button)
//        signInButton.setOnClickListener {
//            val signInIntent = googleSignInClient.signInIntent
//            signInLauncher.launch(signInIntent)
//        }

        verifyVersion()
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.e("SSO", result.resultCode.toString())
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val email = account.email
                val networkScope = CoroutineScope(Dispatchers.IO)
                networkScope.launch {
                    try {
                        performSsoLogin(email!!)
                    } catch (e: CancellationException) {
                        // Ignore cancellation
                    } catch (e: Exception) {
                    }
                }
            } catch (e: ApiException) {
                showMessage(e.toString(), null)
            }
        }
    }

    private fun showUsernameDialog() {
        // Inflate the custom layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_username, null)
        val editTextDialog = dialogView.findViewById<EditText>(R.id.emailAddress)

        // Create the AlertDialog
        val dialog = AlertDialog.Builder(this)
            .setTitle("Request username")
            .setView(dialogView)
            .setPositiveButton("Submit") { dialog, which ->
                // Handle the OK button click event
                val userInput = editTextDialog.text.toString()
                resendUsername(userInput)
            }
            .setNegativeButton("Cancel", null)
            .create()

        // Show the dialog
        dialog.show()
    }

    private fun sendEmail() {
        val recipient = "admin@checkersplusplus.com"
        val subject = "Checkers++ Support Question"
        val body = "Please enter your question below"

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:") // Only email apps should handle this
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        // Verify that the intent will resolve to an activity
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            showMessage("Please send us an email at admin@checkersplusplus.com. We are more than happy to help!", null)
        }
    }


    private fun saveLoginInformation(username: String?, password: String?) {
        val sharedPreferences = getSharedPreferences("CheckersPlusPlusAppPrefs", Context.MODE_PRIVATE)

        if (username != null) {
            sharedPreferences.edit().putString("username", username).apply()
        } else {
            sharedPreferences.edit().remove("username").apply()
        }

        if (username != null) {
            sharedPreferences.edit().putString("password", password).apply()
        } else {
            sharedPreferences.edit().remove("password").apply()
        }
    }

    private fun showResponseDialog(message: String, shouldClose: Boolean) {
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
                if (shouldClose) {
                    finish()
                }
            }

            dialog.show()

            // Optionally, prevent the dialog from being canceled when touched outside
            dialog.setCanceledOnTouchOutside(false)
        }
    }

    private fun resendUsername(email: String) {
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/account/username?email=" + email)
            .get()
            .build()

        // Make asynchronous network call
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {

                showResponseDialog("Bad network connection. Try restarting the app.", false)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    showResponseDialog("Failed to communicate with the server. Please try again later", false)
                } else {
                    showResponseDialog("We sent you the username associated with that email address. Please check your spam folder if you did not receive it.", false)
                }
            }
        })
    }

    private fun verifyVersion() {
       val client = OkHttpClient.Builder()
           .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
           .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
           .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
           .build()
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/account/version")
            .get()
            .build()

        // Make asynchronous network call
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle failed network request
                showResponseDialog("Could not reach the server. Please check your internet connection.", true)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    showResponseDialog("Failed to get version from the server. Please try again later", true)
                } else {
                    val responseBody = response.body?.string()

                    if (responseBody != BuildConfig.APP_VERSION) {
                        showResponseDialog(
                            "You are running an old version. Please update the app from the playstore.",
                            true
                        )
                    }
                }
            }
        })
    }

    private suspend fun performSsoLogin(email: String): String {
        val trimmedUsername = email.trim()

        // Prepare JSON body
        val jsonMediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
        val jsonBody = "{\"ssoEmail\":\"$trimmedUsername\"}"

        // Create request
        val client = OkHttpClient.Builder()
            .connectTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(BuildConfig.NETWORK_TIMEOUT, TimeUnit.SECONDS)
            .build()
        val requestBody = jsonBody.toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("https://" + BuildConfig.BASE_URL + "/account/login/sso")
            .post(requestBody)
            .build()

        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    val responseBody = response.body?.string()

                    if (responseBody == null) {
                        showMessage("No response from server. Try again soon", null)
                        return
                    }

                    val loginResponse = ResponseUtil.parseJson(responseBody)

                    if (loginResponse == null) {
                        showMessage("Invalid response from server. Try again soon", null)
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
                            showMessage("Server response missing data. Try again soon", null)
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
                            intent = Intent(this@MainActivity, GameActivity::class.java)
                            intent.putExtra("gameId", gameId)
                        } else {
                            intent = Intent(this@MainActivity, OpenGamesActivity::class.java)
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

    private suspend fun performLogin(): String {
        val username = usernameEditText.text.toString()
        val password = passwordEditText.text.toString()

        if (username.isBlank() || password.isBlank()) {
            showMessage("Username or password cannot be empty", null)
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
                            showMessage("No response from server. Try again soon", null)
                            return
                        }

                        val loginResponse = ResponseUtil.parseJson(responseBody)

                        if (loginResponse == null) {
                            showMessage("Invalid response from server. Try again soon", null)
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
                                showMessage("Server response missing data. Try again soon", null)
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
                                intent = Intent(this@MainActivity, GameActivity::class.java)
                                intent.putExtra("gameId", gameId)
                            } else {
                                intent = Intent(this@MainActivity, OpenGamesActivity::class.java)
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
            builder.setPositiveButton("Close") { dialog, _ ->
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

            if (lifecycle.currentState == Lifecycle.State.RESUMED) {
                dialog.show()
            }

            // Optionally, prevent the dialog from being canceled when touched outside
            dialog.setCanceledOnTouchOutside(false)
        }
    }

}