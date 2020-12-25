package com.educards.nuts.retrofit2

import android.util.Log
import com.educards.nuts.RequestFailData
import com.educards.nuts.RequestFailReason
import com.educards.nuts.Template
import retrofit2.Response

open class TemplateCallback<S> {

    var template: Template<S>? = null

    fun onResponse(call: TemplateCall<S>, response: Response<S>) {
        if (isRequestSuccessful(call, response)) {
            template?.onRequestSucceeded(response.body())
        } else {
            if (isNetworkingDisabled(call, response)) {
                template?.onRequestFailed(fail(call, RequestFailReason.NETWORKING_DISABLED))
            } else if (isAuthError(call, response)) {
                template?.onRequestFailed(fail(call, RequestFailReason.AUTH_ERROR))
            } else if (isServerError(call, response)) {
                template?.onRequestFailed(fail(call, RequestFailReason.SERVER_ERROR))
            } else {
                template?.onRequestFailed(fail(call, RequestFailReason.OTHER))
            }
        }
    }

    private fun fail(call: TemplateCall<S>, requestFailReason: RequestFailReason): RequestFailData {
        return RequestFailData(call, requestFailReason)
    }

    fun onFailure(call: TemplateCall<S>, t: Throwable?, failReason: RequestFailReason) {
        Log.e(TAG, "Request failed [call=${call.call}]", t)
        template?.onRequestFailed(fail(call, failReason))
    }

    fun isRequestSuccessful(call: TemplateCall<S>?, response: Response<S>?): Boolean {
        return response != null && response.isSuccessful
    }

    fun isNetworkingDisabled(call: TemplateCall<S>?, response: Response<S>?): Boolean {
        // TODO Detect if networking is disabled on "settings level". Such as
        //      the WiFi is manually turned off.
        return false
    }

    fun isAuthError(call: TemplateCall<S>?, response: Response<S>?): Boolean {
        return response != null && (response.code() == 401 || response.code() == 403)
    }

    fun isServerError(call: TemplateCall<S>?, response: Response<S>?): Boolean {
        val serverError = response != null && response.code() >= 500 && response.code() <= 599
        if (serverError) {
            Log.e(TAG, "Server error occurred [call=${call?.call}, response=$response]")
        }
        return serverError
    }

    companion object {
        private const val TAG = "TemplateCallback"
    }

}
