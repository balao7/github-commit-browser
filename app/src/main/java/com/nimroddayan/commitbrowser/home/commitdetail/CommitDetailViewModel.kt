/*
 * Copyright 2020 Nimrod Dayan nimroddayan.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nimroddayan.commitbrowser.home.commitdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.nimroddayan.commitbrowser.api.GithubApi
import com.nimroddayan.commitbrowser.common.coroutines.withInternet
import com.nimroddayan.commitbrowser.common.network.InternetConnection
import com.nimroddayan.commitbrowser.common.ui.BaseViewModel
import com.nimroddayan.commitbrowser.di.CoroutinesDispatcherProvider
import com.nimroddayan.commitbrowser.model.CommitResponse
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

private const val STATE_RESPONSE = "state_response"

class CommitDetailViewModel(
    handle: SavedStateHandle,
    githubApi: GithubApi,
    dispatchers: CoroutinesDispatcherProvider,
    internetConnection: InternetConnection
) : BaseViewModel<CommitDetailViewState>(handle, githubApi, dispatchers, internetConnection) {

    fun loadDetail(sha: String) {
        notifyLoading(CommitDetailViewState())
        Timber.d("Request commit detail for sha: %s", sha)
        val storedResponse: CommitResponse? = handle[STATE_RESPONSE]
        val storedSha = storedResponse?.sha
        if (sha == storedSha) {
            Timber.d("Loaded from SavedState")
            notifyDataLoaded(
                CommitDetailViewState(
                    files = storedResponse.files
                )
            )
        } else {
            viewModelScope.launch {
                val response = withContext(io) {
                    withInternet(::waitForInternetAndNotifyLoading, ::reportErrorAndRetry) {
                        githubApi.getCommit(sha)
                    }
                }
                Timber.d("Saving to SavedState")
                handle[STATE_RESPONSE] = response
                notifyDataLoaded(
                    CommitDetailViewState(
                        files = response.files
                    )
                )
            }
        }
    }

    private suspend fun waitForInternetAndNotifyLoading() {
        waitForInternet()
        notifyLoading(CommitDetailViewState())
    }

    private fun reportErrorAndRetry(throwable: Throwable): Boolean {
        notifyError(throwable, CommitDetailViewState())
        return retryAfterError(throwable)
    }

    class Factory @Inject constructor(
        githubApi: GithubApi,
        dispatchers: CoroutinesDispatcherProvider,
        internetConnection: InternetConnection
    ) : BaseViewModel.Factory<CommitDetailViewModel>(
        githubApi,
        dispatchers,
        internetConnection
    ) {
        override fun create(handle: SavedStateHandle): CommitDetailViewModel {
            return CommitDetailViewModel(
                handle,
                githubApi,
                dispatchers,
                internetConnection
            )
        }
    }
}
