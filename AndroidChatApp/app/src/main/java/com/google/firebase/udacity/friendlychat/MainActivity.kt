/**
 * Copyright Google Inc. All Rights Reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat

import ai.api.AIServiceException
import ai.api.android.AIConfiguration
import ai.api.android.AIDataService
import ai.api.android.AIService
import ai.api.model.AIRequest
import ai.api.model.AIResponse
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.*
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.database.FirebaseRecyclerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageReference
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var mMessageListView: RecyclerView
    private lateinit var mMessageAdapter: MessageAdapter
    private lateinit var mProgressBar: ProgressBar
    private lateinit var mPhotoPickerButton: ImageButton
    private lateinit var mMessageEditText: EditText
    private lateinit var mSendButton: Button
    private lateinit var mUsername: String

    // Realtime database
    private lateinit var database: FirebaseDatabase
    private lateinit var databaseReference: DatabaseReference

    // Auth
    private lateinit var authenticator: FirebaseAuth
    private lateinit var authListener: FirebaseAuth.AuthStateListener

    // Storage
    private lateinit var storage: FirebaseStorage
    private lateinit var photosStorageReference: StorageReference

    // Remote config
    private lateinit var remoteConfig: FirebaseRemoteConfig

    // Bot
    private lateinit var aiService: AIService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mUsername = ANONYMOUS

        database = FirebaseDatabase.getInstance()
        authenticator = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        databaseReference = database.reference.child("messages")
        photosStorageReference = storage.reference.child("chat_photos")
        remoteConfig = FirebaseRemoteConfig.getInstance()

        // Initialize references to views
        mProgressBar = findViewById(R.id.progressBar)
        mMessageListView = findViewById(R.id.messageListView)
        mPhotoPickerButton = findViewById(R.id.photoPickerButton)
        mMessageEditText = findViewById(R.id.messageEditText)
        mSendButton = findViewById(R.id.sendButton)

        // Initialize progress bar
        mProgressBar.visibility = ProgressBar.INVISIBLE

        // Bot
        val config = AIConfiguration("<TOKEN>",
                ai.api.AIConfiguration.SupportedLanguages.English, AIConfiguration.RecognitionEngine.System)

        aiService = AIService.getService(this, config)

        val aiDataService = AIDataService(this, config)

        val aiRequest = AIRequest()

        // ImagePickerButton shows an image picker to upload a image for a message
        mPhotoPickerButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/jpeg"
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            startActivityForResult(Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER)
        }

        // Enable Send button when there's text to send
        mMessageEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {}

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                mSendButton.isEnabled = charSequence.toString().trim { it <= ' ' }.isNotEmpty()
            }

            override fun afterTextChanged(editable: Editable) {}
        })
        mMessageEditText.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT))

        // Send button sends a message and clears the EditText
        mSendButton.setOnClickListener {

            val friendlyMessage = FriendlyMessage(mMessageEditText.text.toString(), mUsername, null)
            databaseReference.push().setValue(friendlyMessage)

            aiRequest.setQuery(mMessageEditText.text.toString())
            object : AsyncTask<AIRequest, Void, AIResponse>() {

                override fun doInBackground(vararg aiRequests: AIRequest): AIResponse? {
                    val request = aiRequests[0]
                    try {
                        return aiDataService.request(aiRequest)
                    } catch (e: AIServiceException) {
                    }

                    return null
                }

                override fun onPostExecute(response: AIResponse?) {
                    if (response != null) {

                        val result = response.result
                        val reply = result.fulfillment.speech
                        val chatMessage = FriendlyMessage(reply, "bot", null)
                        databaseReference.push().setValue(chatMessage)
                        mMessageListView.scrollToPosition(mMessageListView.adapter.itemCount - 1)
                    }
                }
            }.execute(aiRequest)


            // Clear input box
            mMessageEditText.setText("")

            // Check if no view has focus:
            val keyboardView = currentFocus
            if (keyboardView != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(keyboardView.windowToken, 0)
            }

            mMessageListView.scrollToPosition(mMessageListView.adapter.itemCount - 1)
        }


        authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                // User is signed in
                onSignedIn(user.displayName!!)
            } else {
                // User is signed out
                // Create and launch sign-in intent
                onSignedOut()
                startActivityForResult(
                        AuthUI.getInstance()
                                .createSignInIntentBuilder()
                                .setIsSmartLockEnabled(false)
                                .setAvailableProviders(AUTH_PROVIDERS_LIST)
                                .build(),
                        RC_SIGN_IN)
            }
        }

        val remoteSettings = FirebaseRemoteConfigSettings.Builder()
                .setDeveloperModeEnabled(BuildConfig.DEBUG).build()
        remoteConfig.setConfigSettings(remoteSettings)
        val remoteConfigs = HashMap<String, Any>()
        remoteConfigs.put(FRIENDLY_MSG_LIMIT_KEY, DEFAULT_MSG_LENGTH_LIMIT)
        remoteConfig.setDefaults(remoteConfigs)
        fetchConfig()

        val query = databaseReference.limitToLast(50)
        val options = FirebaseRecyclerOptions.Builder<FriendlyMessage>()
                .setQuery(query, FriendlyMessage::class.java)
                .build()
        mMessageAdapter = MessageAdapter(options)
        val layoutManager = LinearLayoutManager(this)
        mMessageListView.layoutManager = layoutManager
        mMessageListView.adapter = mMessageAdapter
    }

    private fun fetchConfig() {
        var cacheExpirationTime: Long = 3600
        if (remoteConfig.info.configSettings.isDeveloperModeEnabled) {
            cacheExpirationTime = 0
        }
        remoteConfig.fetch(cacheExpirationTime)
                .addOnSuccessListener {
                    remoteConfig.activateFetched()
                    applyRetrievedLimit()
                }.addOnFailureListener { applyRetrievedLimit() }
    }

    private fun applyRetrievedLimit() {
        val limit = remoteConfig.getLong(FRIENDLY_MSG_LIMIT_KEY)
        mMessageEditText.filters = arrayOf(InputFilter.LengthFilter(limit.toInt()))
    }

    private fun onSignedOut() {
        mUsername = ANONYMOUS
    }

    private fun onSignedIn(displayName: String) {
        mUsername = displayName
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sign_out_menu -> {
                AuthUI.getInstance().signOut(this)
                return true
            }
            else -> return super.onOptionsItemSelected(item)
        }
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            if (resultCode == Activity.RESULT_OK) {
                Toast.makeText(this, "Signed in!", Toast.LENGTH_LONG).show()
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this, "Sign in cancelled :(", Toast.LENGTH_LONG).show()
                finish()
            }
        } else if (requestCode == RC_PHOTO_PICKER && resultCode == Activity.RESULT_OK) {
            val imagePath = data.data
            val reference = photosStorageReference.child(imagePath.lastPathSegment)
            reference.putFile(imagePath).addOnSuccessListener { taskSnapshot ->
                val downloadUrl = taskSnapshot.downloadUrl
                val message = FriendlyMessage(null, mUsername, downloadUrl.toString())
                databaseReference.push().setValue(message)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mMessageAdapter.startListening()
        aiService.startListening()
    }

    public override fun onResume() {
        super.onResume()
        authenticator.addAuthStateListener(authListener)
        aiService.resume()
    }

    public override fun onPause() {
        super.onPause()
        authenticator.removeAuthStateListener(authListener)
        aiService.pause()
    }

    override fun onStop() {
        super.onStop()
        mMessageAdapter.stopListening()
        aiService.stopListening()
    }

    companion object {

        val ANONYMOUS = "anonymous"
        val DEFAULT_MSG_LENGTH_LIMIT = 1000
        private val RC_PHOTO_PICKER = 2
        private val RC_SIGN_IN = 123
        private val AUTH_PROVIDERS_LIST = Arrays.asList<AuthUI.IdpConfig>(
                AuthUI.IdpConfig.Builder(AuthUI.EMAIL_PROVIDER).build(),
                AuthUI.IdpConfig.Builder(AuthUI.GOOGLE_PROVIDER).build())
        private val FRIENDLY_MSG_LIMIT_KEY = "friendly_message_length"
    }
}
