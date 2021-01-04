package com.educards.nuts.ui;

import androidx.lifecycle.MutableLiveData;

/**
 * <ul>
 *     <li>
 *         A stateful observable which is just a generalization of {@link MutableLiveData}
 *         so that it complies with {@link Observable} interface and therefore can
 *         be easily used by the <i>Nuts library</i>.
 *     </li><li>
 *         For stateless version see {@link ObservableDispatcher}.
 *     </li>
 * </ul>
 *
 * @see Observable
 * @see ObservableDispatcher
 */
public class ObservableData<T> extends MutableLiveData<T> implements Observable<T> {

    // There's nothing to implement.
    // The MutableLiveData implementation complies with Observable interface.

}
