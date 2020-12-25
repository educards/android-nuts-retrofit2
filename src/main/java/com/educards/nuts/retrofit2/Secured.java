package com.educards.nuts.retrofit2;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation type to mark Retrofit call as "secured". If so, the
 * {@link com.educards.nuts.AuthTokenProvider} is consulted before such
 * call to obtain a valid {@link com.educards.nuts.AuthToken authentication token}.
 * The call would not be executed in obtaining valid {@link com.educards.nuts.AuthToken}
 * fails.
 * <p>
 * Example usage:
 * <blockquote><pre>
 * &#64;Secured
 * &#64;GET("path/to/service/method")
 * TemplateCall&lt;String&gt; getServerItem(@Path("uuid") UUID itemUuid);
 * </pre></blockquote></p>
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface Secured {
}
