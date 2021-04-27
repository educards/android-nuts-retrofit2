package com.educards.nuts.retrofit2;

import android.app.Activity;
import android.os.Build;
import android.util.Log;
import com.educards.nuts.AuthTimeService;
import com.educards.nuts.AuthToken;
import com.educards.nuts.AuthTokenProvider;
import com.educards.nuts.AuthTokenStorage;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.util.List;

public class DefaultAuthTokenProvider implements AuthTokenProvider {

    private static final String TAG = "DefAuthTokenProvider";

    private final String HTTP_COOKIE_SESSION_ID_KEY = "JSESSIONID";

    private AuthTokenStorage authTokenStorage;

    private AuthLauncher authLauncher;

    private AuthTimeService authTimeService;

    private ObjectMapper objectMapper;

    /**
     * Cookie stored used in underlying transport layer.
     */
    private PersistentHttpCookieStore cookieStore;

    public DefaultAuthTokenProvider(AuthTokenStorage authTokenStorage, AuthLauncher authLauncher, AuthTimeService authTimeService, ObjectMapper objectMapper, PersistentHttpCookieStore cookieStore) {
        this.authTokenStorage = authTokenStorage;
        this.authLauncher = authLauncher;
        this.authTimeService = authTimeService;
        this.objectMapper = objectMapper;
        this.cookieStore = cookieStore;
    }

    @Override
    public AuthToken getInstalledAuthToken(Activity activity) {

        try {

            AuthToken authToken = authTokenStorage.getAuthToken();
            boolean authTokenValid = isAuthTokenValid(activity, authToken, objectMapper, authTimeService.now());

            if (authTokenValid) {

                // User is already authenticated and the token is (according to client and it's time provider) still valid (session didn't time out).
                // Before each call we always pass the auth token down to transport layer in form of HTTP cookie.
                // This way we ensure that the HTTP cookie is always up to date.

                // TODO Instead of constructing the HttpCookie manually like it is done below
                //      rather update the AuthService (AIDL) with new method
                //      getHttpCookie() which would directly provide JSON serialized HttpCookie from auth provider.
                HttpCookie sessionHttpCookie = new HttpCookie(HTTP_COOKIE_SESSION_ID_KEY, getAuthTokenSessionId(authToken));
                sessionHttpCookie.setDomain(getAuthTokenUri(authToken).getPath());
                sessionHttpCookie.setPath("/");
                sessionHttpCookie.setVersion(1);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sessionHttpCookie.setHttpOnly(true);
                }

                cookieStore.add(getAuthTokenUri(authToken), sessionHttpCookie); // Update auth HTTP cookie

                return authToken;
            }

        } catch (Throwable t) {
            Log.w(TAG, "Failed to retrieve auth token", t);
//            AuthHelper.showGetAuthTokenFailedMessage(activity);
        }

        // Keep local cookie store updated.
        // For whatever reason we failed to acquire auth token from auth provider we now
        // have to sync underlying cookie store with this state.
        removeCookie(cookieStore, HTTP_COOKIE_SESSION_ID_KEY);

        // Start authentication activity to provide new valid AuthToken
        authLauncher.startAuthentication(activity);

        return null;
    }

    public static boolean isAuthTokenValid(Activity activity, AuthToken authToken, ObjectMapper objectMapper, long now) {
        if (authToken != null) {
//            try {
//                if (AuthHelper.isUserAuthenticated(authToken, now)) {
                    return true;
//                }
//            } catch (IOException e) {
//                Log.w(TAG, "Failed to deserialize auth token", e);
//                AuthHelper.showFailedToDeserializeJsonAuthToken(activity);
//            }
        }
        return false;
    }

    private URI getAuthTokenUri(AuthToken authToken) {
        if (authToken.getUri() == null) {
            throw new RuntimeException(String.format("Missing AuthToken.getUri() [authToken=%s]", authToken));
        }
        return authToken.getUri();
    }

    private String getAuthTokenSessionId(AuthToken authToken) {
        if (authToken.getAuthSessionId() == null) {
            throw new RuntimeException(String.format("Missing session ID [AuthToken.getAuthSessionId()=%s]", authToken));
        }
        return authToken.getAuthSessionId();
    }

    private void removeCookie(CookieStore cookieStore, String cookieName) {
        List<URI> uris = cookieStore.getURIs();
        if (uris != null) {
            for (URI uri : uris) {
                List<HttpCookie> httpCookies = cookieStore.get(uri);
                if (httpCookies != null) {
                    for (HttpCookie cookie : httpCookies) {
                        if (cookieName.equals(cookie.getName())) {
                            cookieStore.remove(uri, cookie);
                            break;
                        }
                    }
                }
            }
        }
    }

    public interface AuthLauncher {

        void startAuthentication(Activity parentActivity);

    }

}
