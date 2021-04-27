package com.educards.nuts.retrofit2;

import android.content.Context;
import com.educards.nuts.AuthTokenProvider;
import com.educards.nuts.BuildConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class DefaultRetrofitBuilder {

    public static final String TAG = DefaultRetrofitBuilder.class.getSimpleName();

//    private final long DEBUG_NETWORK_LATENCY_MS = 2000;
    private final long DEBUG_NETWORK_LATENCY_MS = 0;

    private static int CACHE_SIZE = 10 * 1024 * 1024; // 10MB

    private Retrofit retrofit;

    private PersistentHttpCookieStore cookieStore;

    public DefaultRetrofitBuilder(Context context, String serverApiBaseUrl, ObjectMapper objectMapper, AuthTokenProvider authTokenProvider) {

        cookieStore = new PersistentHttpCookieStore(context);
        CookieManager cookieManager = new CookieManager(cookieStore, CookiePolicy.ACCEPT_ALL);
        CookieHandler cookieHandler = cookieManager; //new CookieManager(); // in memory

        OkHttpClient.Builder okHttpClientBuilder = new OkHttpClient.Builder()

            // workaround to problem: (Requests fail after losing and regaining internet connectivity #756) https://github.com/parse-community/Parse-SDK-Android/issues/756
            .connectionPool(new ConnectionPool(0, 1, TimeUnit.MILLISECONDS))

            .protocols(Collections.singletonList(Protocol.HTTP_1_1))

            .cookieJar(new JavaNetCookieJar(cookieHandler))

            // https://stackoverflow.com/questions/52916443/retrofit-okhttp-offline-caching-not-working
            //.addInterceptor(new OfflineInterceptor(this.app))

            //.addNetworkInterceptor(new OnlineInterceptor())
            .addNetworkInterceptor(new HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY));

            //.cache(new Cache(new File(this.app.getCacheDir(), "httpCache"), CACHE_SIZE))

            //.connectTimeout(10, TimeUnit.SECONDS)
            //.writeTimeout(10, TimeUnit.SECONDS)
            //.readTimeout(30, TimeUnit.SECONDS)

        if (BuildConfig.DEBUG && DEBUG_NETWORK_LATENCY_MS > 0) {
                okHttpClientBuilder.addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        try {
                            Thread.sleep(DEBUG_NETWORK_LATENCY_MS);
                        } catch (InterruptedException e) { }
                        Request request = chain.request();
                        return chain.proceed(request);
                    }
            });
        }

        final Retrofit.Builder builder = new Retrofit.Builder()
                .baseUrl(serverApiBaseUrl)
                .client(okHttpClientBuilder.build())
                .addCallAdapterFactory(TemplateCallAdapterFactory.create(authTokenProvider))
                .addConverterFactory(JacksonConverterFactory.create(objectMapper));

        this.retrofit = builder.build();
    }

    public Retrofit getRetrofit() {
        return retrofit;
    }

}
