/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.assistant.viewmodel

import android.content.Intent
import android.net.Uri
import androidx.annotation.UiThread
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.launch
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.core.tools.Log
import org.linphone.utils.Event
import org.linphone.utils.FileUtils
import org.linphone.utils.TimestampUtils

class SingleSignOnViewModel : ViewModel() {
    companion object {
        private const val TAG = "[Single Sign On ViewModel]"

        private const val CLIENT_ID = "linphone"
        private const val REDIRECT_URI = "org.linphone:/openidcallback"
    }

    val singleSignOnProcessCompletedEvent = MutableLiveData<Event<Boolean>>()

    val singleSignOnUrl = MutableLiveData<String>()

    val startAuthIntentEvent: MutableLiveData<Event<Intent>> by lazy {
        MutableLiveData<Event<Intent>>()
    }

    val onErrorEvent: MutableLiveData<Event<String>> by lazy {
        MutableLiveData<Event<String>>()
    }

    private var preFilledUser: String = ""

    private lateinit var authState: AuthState
    private lateinit var authService: AuthorizationService

    @UiThread
    fun setUp() {
        viewModelScope.launch {
            Log.i("$TAG Setting up SSO environment, redirect URI is [$REDIRECT_URI]")
            authState = getAuthState()
            updateTokenInfo()
        }
    }

    @UiThread
    fun processAuthIntentResponse(resp: AuthorizationResponse?, ex: AuthorizationException?) {
        if (::authState.isInitialized) {
            Log.i("$TAG Updating AuthState object after authorization response")
            authState.update(resp, ex)
        }

        if (resp != null) {
            Log.i("$TAG Response isn't null, performing request token")
            performRequestToken(resp)
        } else {
            Log.e("$TAG Can't perform request token [$ex]")
            onErrorEvent.postValue(Event(ex?.errorDescription.orEmpty()))
        }
    }

    @UiThread
    private fun singleSignOn() {
        Log.i("$TAG Fetch from issuer")
        AuthorizationServiceConfiguration.fetchFromUrl(
            Uri.parse(singleSignOnUrl.value),
            AuthorizationServiceConfiguration.RetrieveConfigurationCallback { serviceConfiguration, ex ->
                if (ex != null) {
                    Log.e("$TAG Failed to fetch configuration")
                    onErrorEvent.postValue(Event("Failed to fetch configuration"))
                    return@RetrieveConfigurationCallback
                }
                if (serviceConfiguration == null) {
                    Log.e("$TAG Service configuration is null!")
                    onErrorEvent.postValue(Event("Service configuration is null"))
                    return@RetrieveConfigurationCallback
                }

                if (!::authState.isInitialized) {
                    Log.i("$TAG Initializing AuthState object")
                    authState = AuthState(serviceConfiguration)
                    storeAuthStateAsJsonFile()
                }

                val authRequestBuilder = AuthorizationRequest.Builder(
                    serviceConfiguration, // the authorization service configuration
                    CLIENT_ID, // the client ID, typically pre-registered and static
                    ResponseTypeValues.CODE, // the response_type value: we want a code
                    Uri.parse(REDIRECT_URI) // the redirect URI to which the auth response is sent
                )

                if (preFilledUser.isNotEmpty()) {
                    authRequestBuilder.setLoginHint(preFilledUser)
                }

                val authRequest = authRequestBuilder.build()
                authService = AuthorizationService(coreContext.context)
                val authIntent = authService.getAuthorizationRequestIntent(authRequest)
                startAuthIntentEvent.postValue(Event(authIntent))
            }
        )
    }

    @UiThread
    private fun performRefreshToken() {
        if (::authState.isInitialized) {
            if (!::authService.isInitialized) {
                authService = AuthorizationService(coreContext.context)
            }

            Log.i("$TAG Starting refresh token request")
            authService.performTokenRequest(
                authState.createTokenRefreshRequest()
            ) { resp, ex ->
                if (resp != null) {
                    Log.i("$TAG Token refresh succeeded!")

                    if (::authState.isInitialized) {
                        Log.i("$TAG Updating AuthState object after refresh token response")
                        authState.update(resp, ex)
                        storeAuthStateAsJsonFile()
                    }
                    updateTokenInfo()
                } else {
                    Log.e(
                        "$TAG Failed to perform token refresh [$ex], destroying auth_state.json file"
                    )
                    onErrorEvent.postValue(Event(ex?.errorDescription.orEmpty()))

                    val file = File(coreContext.context.filesDir.absolutePath, "auth_state.json")
                    viewModelScope.launch {
                        FileUtils.deleteFile(file.absolutePath)
                        Log.w(
                            "$TAG Previous auth_state.json file deleted, starting single sign on process from scratch"
                        )
                        singleSignOn()
                    }
                }
            }
        }
    }

    @UiThread
    private fun performRequestToken(response: AuthorizationResponse) {
        if (::authService.isInitialized) {
            Log.i("$TAG Starting perform token request")
            authService.performTokenRequest(
                response.createTokenExchangeRequest()
            ) { resp, ex ->
                if (resp != null) {
                    Log.i("$TAG Token exchange succeeded!")

                    if (::authState.isInitialized) {
                        Log.i("$TAG Updating AuthState object after token response")
                        authState.update(resp, ex)
                        storeAuthStateAsJsonFile()
                    }

                    useToken()
                } else {
                    Log.e("$TAG Failed to perform token request [$ex]")
                    onErrorEvent.postValue(Event(ex?.errorDescription.orEmpty()))
                }
            }
        }
    }

    @UiThread
    private fun useToken() {
        if (::authState.isInitialized && ::authService.isInitialized) {
            if (authState.needsTokenRefresh && authState.refreshToken.isNullOrEmpty()) {
                Log.e("$TAG Attempted to take an unauthorized action without a refresh token!")
                return
            }

            singleSignOnProcessCompletedEvent.postValue(Event(true))
            /*Log.i("$TAG Performing action with fresh token")
            authState.performActionWithFreshTokens(
                authService,
                AuthState.AuthStateAction { accessToken, idToken, ex ->
                    if (ex != null) {
                        Log.e("$TAG Failed to use token [$ex]")
                        return@AuthStateAction
                    }

                    Log.i("$$TAG Access & id tokens are now available")
                    Log.d("$TAG Access token [$accessToken], id token [$idToken]")

                    storeAuthStateAsJsonFile()
                }
            )*/
        }
    }

    @UiThread
    private suspend fun getAuthState(): AuthState {
        val file = File(coreContext.context.filesDir.absolutePath, "auth_state.json")
        if (file.exists()) {
            Log.i("$TAG Auth state file found, trying to read it")
            val content = FileUtils.readFile(file)
            if (content.isNotEmpty()) {
                Log.i("$TAG Initializing AuthState from local JSON file")
                Log.d("$TAG Local JSON file contains [$content]")
                try {
                    return AuthState.jsonDeserialize(content)
                } catch (exception: Exception) {
                    Log.e("$TAG Failed to use serialized AuthState [$exception]")
                    onErrorEvent.postValue(Event("Failed to read stored AuthState"))
                }
            }
        } else {
            Log.i("$TAG Auth state file not found yet...")
        }

        return AuthState()
    }

    @UiThread
    private fun storeAuthStateAsJsonFile() {
        Log.i("$TAG Trying to save serialized authState as JSON file")
        val data = authState.jsonSerializeString()
        Log.d("$TAG Date to save is [$data]")
        val file = File(coreContext.context.filesDir.absolutePath, "auth_state.json")
        viewModelScope.launch {
            if (FileUtils.dumpStringToFile(data, file)) {
                Log.i("$TAG Service configuration saved as JSON as [${file.absolutePath}]")
            } else {
                Log.i(
                    "$TAG Failed to save service configuration as JSON as [${file.absolutePath}]"
                )
            }
        }
    }

    @UiThread
    private fun updateTokenInfo() {
        Log.i("$TAG Updating token info")

        if (::authState.isInitialized) {
            if (authState.isAuthorized) {
                Log.i("$TAG User is already authenticated!")

                val expiration = authState.accessTokenExpirationTime
                if (expiration != null) {
                    if (expiration < System.currentTimeMillis()) {
                        Log.w("$TAG Access token is expired")
                        performRefreshToken()
                    } else {
                        val date = if (TimestampUtils.isToday(expiration, timestampInSecs = false)) {
                            "today"
                        } else {
                            TimestampUtils.toString(
                                expiration,
                                onlyDate = true,
                                timestampInSecs = false
                            )
                        }
                        val time = TimestampUtils.toString(expiration, timestampInSecs = false)
                        Log.i("$TAG Access token expires [$date] [$time]")
                        singleSignOnProcessCompletedEvent.postValue(Event(true))
                    }
                } else {
                    Log.w("$TAG Access token expiration info not available")
                    val file = File(coreContext.context.filesDir.absolutePath, "auth_state.json")
                    viewModelScope.launch {
                        FileUtils.deleteFile(file.absolutePath)
                        singleSignOn()
                    }
                }
            } else {
                Log.w("$TAG User isn't authenticated yet")
                singleSignOn()
            }
        } else {
            Log.i("$TAG Auth state hasn't been created yet")
            singleSignOn()
        }
    }
}
