package com.educards.nuts.retrofit2;

import com.educards.nuts.AuthTokenProvider;
import retrofit2.CallAdapter;
import retrofit2.Retrofit;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public class TemplateCallAdapterFactory extends CallAdapter.Factory {

    private AuthTokenProvider authTokenProvider;

    private TemplateCallAdapterFactory(AuthTokenProvider authTokenProvider) {
        this.authTokenProvider = authTokenProvider;
    }

    @Override
    public CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {

        try {
            // get enclosing type
            ParameterizedType enclosingType = (ParameterizedType) returnType;

            // ensure enclosing type is TemplateCall
            if (enclosingType.getRawType() != TemplateCall.class) {
                return null;
            } else {
                Type actualTypeArgument = enclosingType.getActualTypeArguments()[0];
                return new TemplateCallAdapter<>(isCallSecured(annotations), authTokenProvider, actualTypeArgument);
            }

        } catch (ClassCastException e) {
            return null;
        }
    }

    private boolean isCallSecured(Annotation[] annotations) {
        if (annotations != null) {
            for (Annotation annotation : annotations) {
                if (annotation.annotationType() == Secured.class) {
                    return true;
                }
            }
        }
        return false;
    }

    public static final TemplateCallAdapterFactory create(AuthTokenProvider authTokenProvider) {
        return new TemplateCallAdapterFactory(authTokenProvider);
    }

}

