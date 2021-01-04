package com.educards.nuts.ui;

import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;

/**
 * <ul>
 *     <li>
 *         An <i>observable</i> (or a <i>subject</i> by the naming convention of
 *         <a href="https://en.wikipedia.org/wiki/Observer_pattern">Observer pattern</a>)
 *         which is able to automatically handle it's subscription state according to registered
 *         {@link androidx.lifecycle.Lifecycle lifecycle aware owner} such as
 *         {@link android.app.Activity Activity} or {@link androidx.fragment.app.Fragment Fragment}.
 *     </li><li>
 *         In other words, this <code>Observable</code> respects the lifecycle state of components
 *         such as {@link android.app.Activity Activity} or {@link androidx.fragment.app.Fragment Fragment}
 *         and thus won't notify the listeners if lifecycle state is not <i>active</i>.
 *     </li><li>
 *         The definition of <i>active</i> state complies with {@link androidx.lifecycle.LiveData LiveData}.
 *     </li>
 * </ul>
 *
 * @see ObservableData
 * @see ObservableDispatcher
 */
public interface Observable<T> {

    void postValue(T value);

    void setValue(T value);

    void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer);

    void removeObserver(@NonNull final Observer<? super T> observer);

    void removeObservers(@NonNull LifecycleOwner owner);

    void observeForever(@NonNull Observer<? super T> observer);

    T getValue();

}
