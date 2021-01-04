package com.educards.nuts.ui;

import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.*;
import com.educards.nuts.retrofit2.BuildConfig;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import static androidx.lifecycle.Lifecycle.State.DESTROYED;
import static androidx.lifecycle.Lifecycle.State.STARTED;

/**
 * A stateless {@link androidx.lifecycle.Lifecycle lifecycle aware} observable.
 * <ul>
 *     <li>
 *         Behaves exactly the same way as {@link ObservableData}/{@link MutableLiveData}
 *         with exception that it never remembers the state.
 *     </li><li>
 *         Used in cases when to dispatch the data is sufficient. E.g. when large chunks of
 *         data are fetched from server and stored in local DB.
 *     </li><li>
 *         {@link ObservableDispatcher#getValue()} always throws {@link UnsupportedOperationException}.
 *     </li><li>
 *         For stateful version see {@link ObservableData}.
 *     </li>
 * </ul>
 *
 * @see Observable
 * @see ObservableData
 */
public class ObservableDispatcher<T> implements Observable<T> {

    private static final String TAG = "StatelessSubject";

    private Map<Observer<? super T>, ObserverWrapper> mObservers = new HashMap<>();

    private boolean mDispatchingValue;

    @SuppressWarnings("FieldCanBeLocal")
    private boolean mDispatchInvalidated;

    private void considerNotify(ObserverWrapper observer, T data) {
        if (!observer.mActive) {
            return;
        }
        // Check latest state b4 dispatch.
        // Maybe it changed state but we didn't get the event yet.
        if (!observer.shouldBeActive()) {
            observer.activeStateChanged(false);
            return;
        }
        observer.mObserver.onChanged(data);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void dispatchingValue(@Nullable ObserverWrapper initiator, T data) {
        if (mDispatchingValue) {
            mDispatchInvalidated = true;
            return;
        }
        mDispatchingValue = true;
        do {
            mDispatchInvalidated = false;
            if (initiator != null) {
                considerNotify(initiator, data);
                initiator = null;
            } else {
                for (Map.Entry<Observer<? super T>, ObserverWrapper> entry : mObservers.entrySet()) {
                    considerNotify(entry.getValue(), data);
                    if (mDispatchInvalidated) {
                        break;
                    }
                }
            }
        } while (mDispatchInvalidated);
        mDispatchingValue = false;
    }

    @Override
    @MainThread
    public void observe(@NonNull LifecycleOwner owner, @NonNull Observer<? super T> observer) {
        assertMainThread("observe");
        if (owner.getLifecycle().getCurrentState() == DESTROYED) {
            // ignore
            return;
        }
        LifecycleBoundObserver wrapper = new LifecycleBoundObserver(owner, observer);

        ObserverWrapper existing = mObservers.get(observer);
        if (existing != null && !existing.isAttachedTo(owner)) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        } else {
            mObservers.put(observer, wrapper);
            if (BuildConfig.DEBUG) {
                Log.d(TAG, String.format("Lifecycle aware observer added [observersCount=%s]", mObservers.size()));
            }
        }
        if (existing != null) {
            return;
        }
        owner.getLifecycle().addObserver(wrapper);
    }

    @Override
    @MainThread
    public void observeForever(@NonNull Observer<? super T> observer) {
        assertMainThread("observeForever");
        AlwaysActiveObserver wrapper = new AlwaysActiveObserver(observer);
        ObserverWrapper existing = mObservers.get(observer);
        if (existing != null && existing instanceof ObservableDispatcher.LifecycleBoundObserver) {
            throw new IllegalArgumentException("Cannot add the same observer"
                    + " with different lifecycles");
        } else {
            mObservers.put(observer, wrapper);
            if (BuildConfig.DEBUG) {
                Log.d(TAG, String.format("Lifecycle ignorant observer added [observersCount=%s]", mObservers.size()));
            }
        }
        if (existing != null) {
            return;
        }
        wrapper.activeStateChanged(true);
    }

    /**
     * Removes the given observer from the observers list.
     *
     * @param observer The Observer to receive events.
     */
    @Override
    @MainThread
    public void removeObserver(@NonNull final Observer<? super T> observer) {
        assertMainThread("removeObserver");
        ObserverWrapper removed = mObservers.remove(observer);
        postRemove(removed);
    }

    private void postRemove(ObserverWrapper removed) {
        if (removed == null) {
            return;
        }
        removed.detachObserver();
        removed.activeStateChanged(false);

        if (BuildConfig.DEBUG) {
            Log.d(TAG, String.format("Observer removed [observersCount=%s]", mObservers.size()));
        }
    }

    /**
     * Removes all observers that are tied to the given {@link LifecycleOwner}.
     *
     * @param owner The {@code LifecycleOwner} scope for the observers to be removed.
     */
    @SuppressWarnings("WeakerAccess")
    @MainThread
    public void removeObservers(@NonNull final LifecycleOwner owner) {
        assertMainThread("removeObservers");
        Iterator<Map.Entry<Observer<? super T>, ObserverWrapper>> iterator = mObservers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Observer<? super T>, ObserverWrapper> entry = iterator.next();
            ObserverWrapper observer = entry.getValue();
            if (observer.isAttachedTo(owner)) {
                iterator.remove();
                postRemove(observer);
            }
        }
    }

    @Override
    public void postValue(T value) {
        new Handler(Looper.getMainLooper()).post(() -> {
            setValue(value);
        });
    }

    /**
     * Always throws {@link UnsupportedOperationException}.
     * This is thrown by design of this class.
     */
    @MainThread
    public T getValue() {
        throw new UnsupportedOperationException("Stateless implementation of Subject just dispatches" +
                " the value to observers but doesn't own it's reference.");
    }

    @MainThread
    @Override
    public void setValue(T value) {
        assertMainThread("setValue");
        dispatchingValue(null, value);
    }

    class LifecycleBoundObserver extends ObserverWrapper implements LifecycleObserver {

        @NonNull
        final LifecycleOwner mOwner;

        LifecycleBoundObserver(@NonNull LifecycleOwner owner, Observer<? super T> observer) {
            super(observer);
            mOwner = owner;
        }

        @Override
        boolean shouldBeActive() {
            return mOwner.getLifecycle().getCurrentState().isAtLeast(STARTED);
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
        public void onStateChanged(LifecycleOwner source, Lifecycle.Event event) {
            if (mOwner.getLifecycle().getCurrentState() == DESTROYED) {
                removeObserver(mObserver);
                return;
            }
            activeStateChanged(shouldBeActive());
        }

        @Override
        boolean isAttachedTo(LifecycleOwner owner) {
            return mOwner == owner;
        }

        @Override
        void detachObserver() {
            mOwner.getLifecycle().removeObserver(this);
        }

    }

    private abstract class ObserverWrapper {

        final Observer<? super T> mObserver;
        boolean mActive;

        ObserverWrapper(Observer<? super T> observer) {
            mObserver = observer;
        }

        abstract boolean shouldBeActive();

        boolean isAttachedTo(LifecycleOwner owner) {
            return false;
        }

        void detachObserver() {
        }

        void activeStateChanged(boolean newActive) {

            if (BuildConfig.DEBUG) {
                Log.d(TAG, String.format("Observer active state changed [active=%s]", newActive));
            }

            mActive = newActive;
        }
    }

    private class AlwaysActiveObserver extends ObserverWrapper {

        AlwaysActiveObserver(Observer<? super T> observer) {
            super(observer);
        }

        @Override
        boolean shouldBeActive() {
            return true;
        }
    }

    /**
     * https://stackoverflow.com/a/34052602/915756
     **/
    private static void assertMainThread(String methodName) {

        boolean mainThread;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mainThread = Looper.getMainLooper().isCurrentThread();
        } else {
            mainThread = Thread.currentThread().equals(Looper.getMainLooper().getThread());
        }

        if (!mainThread) {
            throw new IllegalStateException("Cannot invoke " + methodName + " on a background"
                    + " thread");
        }
    }

}
