package com.educards.nuts.retrofit2;

import com.educards.nuts.AuthTokenProvider;
import retrofit2.Call;
import retrofit2.CallAdapter;

import java.lang.reflect.Type;

public class TemplateCallAdapter<R> implements CallAdapter<R, TemplateCall> {

    private boolean callIsSecured;

    private Type type;

    private AuthTokenProvider authTokenProvider;

    public TemplateCallAdapter(boolean callIsSecured, AuthTokenProvider authTokenProvider, Type type) {
        this.callIsSecured = callIsSecured;
        this.type = type;
        this.authTokenProvider = authTokenProvider;
    }

    @Override
    public Type responseType() {
        return type;
    }

    @Override
    public TemplateCall adapt(Call<R> call) {
        return new TemplateCall(callIsSecured, authTokenProvider, call);
    }

}
