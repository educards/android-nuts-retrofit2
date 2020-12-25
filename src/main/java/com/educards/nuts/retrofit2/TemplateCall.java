package com.educards.nuts.retrofit2;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Looper;
import android.util.Log;
import com.educards.nuts.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.net.URI;

public class TemplateCall<T> extends Request {

    private static final String TAG = "TemplateCall";

    private boolean callIsSecured;

    private AuthTokenProvider authTokenProvider;

    private Call<T> call;

    public TemplateCall(boolean callIsSecured, AuthTokenProvider authTokenProvider, Call<T> call) {
        super(Protocol.HTTP, getUri(call));

        this.callIsSecured = callIsSecured;
        this.authTokenProvider = authTokenProvider;
        this.call = call;
    }

    private static URI getUri(Call call) {
        try {
            return call.request().url().uri();
        } catch (Throwable t) {
            throw new RuntimeException("Failed to parse request call URI", t);
        }
    }

    public Call<T> getCall() {
        return call;
    }

    /**
     * @param activity Currently activity. This is necessary for the case if authentication is needed
     *                 right before the network call. In such scenario an authentication process
     *                 might be started on a top of provided activity (@see {@link Activity#startActivity(Intent)}).
     * @return
     * <ul>
     *      <li><code>true</code> if the call was successfully enqueued</li>
     *      <li><code>false</code> if call has not been executed due to missing
     *      authentication token (@see {@link AuthTokenProvider#getAuthToken(Activity)})</li>
     * </ul>
     */
    public void enqueue(Activity activity, Template<T> template, final TemplateCallback<T> callback) {

        if (template == null) {
            throw new RuntimeException(String.format("A network call scenario must be defined [call=%s].", call));
        }

        callback.setTemplate(template); // just a dependency hitch

        // TODO Should this be called on UI thread in case !mainThread (see below)?
        template.onRequestInProgress(); // give UI a chance to start some form of progress bar

        if (callIsSecured) {

            // This call is secured therefore we have to ensure
            // that auth token is provided to the underlying transport layer.
            // However, the AuthTokenProvider uses IPC to acquire AuthToken from
            // another process (to support SSO) and IPC call can't be made in main (UI) thread.
            boolean mainThread = Thread.currentThread().equals(Looper.getMainLooper().getThread());
            if (mainThread) {
                ensureAuthTokenAndEnqueueAsync(activity, callback);
            } else {
                ensureAuthTokenAndEnqueue(activity, callback);
            }

        } else {
            // This call is not secured, don't care about authentication, just enqueue
            enqueueImpl(callback);
        }
    }

    private void ensureAuthTokenAndEnqueueAsync(final Activity activity, final TemplateCallback<T> callback) {
        new AsyncTask<Void, Void, AuthToken>() {

            @Override
            protected AuthToken doInBackground(Void... voids) {
                return authTokenProvider.getAuthToken(activity);
            }

            @Override
            protected void onPostExecute(AuthToken authToken) {
                if (authToken == null) {
                    onMissingAuthTokenFailure(callback);
                } else {
                    enqueueImpl(callback);
                }
            }
        }.execute();
    }

    private void ensureAuthTokenAndEnqueue(Activity activity, TemplateCallback<T> callback) {
        AuthToken authToken = authTokenProvider.getAuthToken(activity);
        if (authToken == null) {
            onMissingAuthTokenFailure(callback);
        } else {
            enqueueImpl(callback);
        }
    }

    private final void onMissingAuthTokenFailure(TemplateCallback<T> callback) {

        Log.i(TAG, String.format(
                "Call has not been enqueued due to missing auth token, " +
                "new token was requested [call=%s]", call));

        // Send a signal to UI that this call failed.
        // UI might stop progress bar or do any similar UI cleanup here.
        // We don't provide fail reason here since the getAndSyncAuthToken() already
        // handles the presentation layer by showing appropriate user message.
        callback.onFailure(TemplateCall.this, null, null);
    }

    private void enqueueImpl(final TemplateCallback<T> callback) {
        call.enqueue(new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                callback.onResponse(TemplateCall.this, response);
            }
            @Override
            public void onFailure(Call<T> call, Throwable t) {
                callback.onFailure(TemplateCall.this, t, RequestFailReason.OTHER);
            }
        });
    }

}
