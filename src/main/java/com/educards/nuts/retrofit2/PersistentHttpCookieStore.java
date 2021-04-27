package com.educards.nuts.retrofit2;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import java.io.*;
import java.net.CookieStore;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * A persistent cookie store for Android which implements the {@link CookieStore} interface.
 * Cookies are stored and will persist on the user's device between application sessions since
 * they are serialized and stored in {@link SharedPreferences}.
 */
public class PersistentHttpCookieStore implements CookieStore {
    private static final String LOG_TAG = "PersistentHttpCkieStr";
    private static final String COOKIE_PREFS = "CookiePrefsFile";
    private static final String COOKIE_NAME_PREFIX = "cookie_";
    private static final String COOKIE_DOMAIN_PREFIX = "domain_";
    private static final String COOKIE_DOMAIN_STORE = "domains";

    /**
     * this map may have null keys!
     */
    private final Map<URI, List<HttpCookie>> cookiesCache;

    private final SharedPreferences cookiePrefs;

    /**
     * Construct a persistent cookie store.
     *
     * @param context Context to attach cookie store to
     */
    public PersistentHttpCookieStore(Context context) {
        cookiePrefs = context.getSharedPreferences(COOKIE_PREFS, 0);
        cookiesCache = new HashMap<URI, List<HttpCookie>>();

        // Load any previously stored domains into the cache
        String storedCookieDomains = cookiePrefs.getString(COOKIE_DOMAIN_STORE, null);
        if (storedCookieDomains != null) {
            // Get all the domains
            String[] storedCookieDomainsArray = TextUtils.split(storedCookieDomains, ",");
            for (String domain : storedCookieDomainsArray) {
                // Get the cookie names under the domain
                String storedCookiesNames = cookiePrefs.getString(COOKIE_DOMAIN_PREFIX + domain,
                        null);
                if (storedCookiesNames != null) {
                    String[] storedCookieNamesArray = TextUtils.split(storedCookiesNames, ",");
                    if (storedCookieNamesArray != null) {
                        List<HttpCookie> cookies = new ArrayList<HttpCookie>();
                        for (String cookieName : storedCookieNamesArray) {
                            // Get the serialized cookies, deserialize it and add it to our cache
                            String encodedCookie = cookiePrefs.getString(COOKIE_NAME_PREFIX + domain
                                    + cookieName, null);
                            if (encodedCookie != null)
                                cookies.add(decodeCookie(encodedCookie));
                        }
                        cookiesCache.put(URI.create(domain), cookies);
                    }
                }
            }
        }
    }

    public synchronized void add(URI uri, HttpCookie cookie) {
        if (cookie == null) {
            throw new NullPointerException("cookie == null");
        }

        uri = cookiesUri(uri);
        List<HttpCookie> cookies = cookiesCache.get(uri);
        if (cookies == null) {
            cookies = new ArrayList<HttpCookie>();
            cookiesCache.put(uri, cookies);
        } else {
            cookies.remove(cookie);
        }
        cookies.add(cookie);

        // Save cookie into persistent store
        SharedPreferences.Editor prefsWriter = cookiePrefs.edit();
        prefsWriter.putString(COOKIE_DOMAIN_STORE, TextUtils.join(",", cookiesCache.keySet()));

        Set<String> cookieNames = new HashSet<String>();
        for (HttpCookie httpCookie : cookies) {
            cookieNames.add(httpCookie.getName());
            prefsWriter.putString(COOKIE_NAME_PREFIX + uri + httpCookie.getName(),
                    encodeCookie(new SerializableHttpCookie(httpCookie)));
        }
        prefsWriter.putString(COOKIE_DOMAIN_PREFIX + uri, TextUtils.join(",", cookieNames));
        prefsWriter.commit();
    }

    public synchronized List<HttpCookie> get(URI uri) {
        if (uri == null) {
            throw new NullPointerException("uri == null");
        }

        List<HttpCookie> result = new ArrayList<HttpCookie>();
        // get cookies associated with given URI. If none, returns an empty list
        List<HttpCookie> cookiesForUri = cookiesCache.get(uri);
        if (cookiesForUri != null) {
            for (Iterator<HttpCookie> i = cookiesForUri.iterator(); i.hasNext(); ) {
                HttpCookie cookie = i.next();
                if (cookie.hasExpired()) {
                    i.remove(); // remove expired cookies
                } else {
                    result.add(cookie);
                }
            }
        }

        // get all cookies that domain matches the URI
        for (Map.Entry<URI, List<HttpCookie>> entry : cookiesCache.entrySet()) {
            if (uri.equals(entry.getKey())) {
                continue; // skip the given URI; we've already handled it
            }
            List<HttpCookie> entryCookies = entry.getValue();
            for (Iterator<HttpCookie> i = entryCookies.iterator(); i.hasNext(); ) {
                HttpCookie cookie = i.next();
                if (!HttpCookie.domainMatches(cookie.getDomain(), uri.getHost())) {
                    continue;
                }
                if (cookie.hasExpired()) {
                    i.remove(); // remove expired cookies
                } else if (!result.contains(cookie)) {
                    result.add(cookie);
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    public synchronized List<HttpCookie> getCookies() {
        List<HttpCookie> result = new ArrayList<HttpCookie>();
        for (List<HttpCookie> list : cookiesCache.values()) {
            for (Iterator<HttpCookie> i = list.iterator(); i.hasNext(); ) {
                HttpCookie cookie = i.next();
                if (cookie.hasExpired()) {
                    i.remove(); // remove expired cookies
                } else if (!result.contains(cookie)) {
                    result.add(cookie);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized List<URI> getURIs() {
        List<URI> result = new ArrayList<URI>(cookiesCache.keySet());
        result.remove(null); // sigh
        return Collections.unmodifiableList(result);
    }

    public synchronized boolean remove(URI uri, HttpCookie cookie) {
        if (cookie == null) {
            throw new NullPointerException("cookie == null");
        }

        if (cookiesCache.containsKey(uri) && cookiesCache.get(uri).remove(cookie)) {
            SharedPreferences.Editor prefsWriter = cookiePrefs.edit();

            List<HttpCookie> cookies = cookiesCache.get(uri);
            Set<String> cookieNames = new HashSet<String>();
            for (HttpCookie httpCookie : cookies) {
                cookieNames.add(httpCookie.getName());
            }

            prefsWriter.putString(COOKIE_DOMAIN_PREFIX + uri,
                    TextUtils.join(",", cookieNames));
            prefsWriter.remove(COOKIE_NAME_PREFIX + uri + cookie.getName());
            prefsWriter.commit();
            return true;
        }

        return false;
    }

    public synchronized boolean removeAll() {
        // Clear cookies from persistent store
        cookiePrefs.edit().clear().commit();

        // Clear cookies from cache
        boolean result = !cookiesCache.isEmpty();
        cookiesCache.clear();
        return result;
    }

    /**
     * Serializes HttpCookie object into String
     *
     * @param cookie cookie to be encoded, can be null
     * @return cookie encoded as String
     */
    protected String encodeCookie(SerializableHttpCookie cookie) {
        if (cookie == null)
            return null;

        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            ObjectOutputStream outputStream = new ObjectOutputStream(os);
            outputStream.writeObject(cookie);
        } catch (IOException e) {
            Log.d(LOG_TAG, "IOException in encodeCookie", e);
            return null;
        }

        return byteArrayToHexString(os.toByteArray());
    }

    /**
     * Returns HttpCookie decoded from cookie string
     *
     * @param cookieString string of cookie as returned from http request
     * @return decoded cookie or null if exception occured
     */
    protected HttpCookie decodeCookie(String cookieString) {
        byte[] bytes = hexStringToByteArray(cookieString);
        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                bytes);

        HttpCookie cookie = null;
        try {
            ObjectInputStream objectInputStream = new ObjectInputStream(
                    byteArrayInputStream);
            cookie = ((SerializableHttpCookie) objectInputStream.readObject())
                    .getCookie();
        } catch (IOException e) {
            Log.d(LOG_TAG, "IOException in decodeCookie", e);
        } catch (ClassNotFoundException e) {
            Log.d(LOG_TAG, "ClassNotFoundException in decodeCookie", e);
        }

        return cookie;
    }

    /**
     * Using some super basic byte array &lt;-&gt; hex conversions so we don't
     * have to rely on any large Base64 libraries. Can be overridden if you
     * like!
     *
     * @param bytes byte array to be converted
     * @return string containing hex values
     */
    protected String byteArrayToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte element : bytes) {
            int v = element & 0xff;
            if (v < 16) {
                sb.append('0');
            }
            sb.append(Integer.toHexString(v));
        }
        return sb.toString().toUpperCase(Locale.US);
    }

    /**
     * Converts hex values from strings to byte arra
     *
     * @param hexString string of hex-encoded values
     * @return decoded byte array
     */
    protected byte[] hexStringToByteArray(String hexString) {
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4) + Character
                    .digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    private URI cookiesUri(URI uri) {
        if (uri == null) {
            return null;
        }

        try {
            return new URI("http", uri.getHost(), null, null);
        } catch (URISyntaxException e) {
            return uri; // probably a URI with no host
        }
    }
}
