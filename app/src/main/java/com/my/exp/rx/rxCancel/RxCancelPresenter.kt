// Copyright (c) 2017-present WANG, TAI-CHUN
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
//    The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
//    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.

package com.my.exp.rx.rxCancel

import com.my.exp.rx.model.ProgressState
import com.my.exp.rx.protocol.INavigator
import com.my.exp.rx.protocol.IPresenter
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.Scheduler
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.util.concurrent.TimeUnit

class RxCancelPresenter(navigator: INavigator,
                        workerSchedulers: Scheduler,
                        uiScheduler: Scheduler)
    : IPresenter<RxCancelContract.View> {

    // Navigator.
    private val mNavigator = navigator

    // View
    private lateinit var mView: RxCancelContract.View

    // Cancel signal.
    private val mCancelSrc = PublishSubject.create<Any>()

    // Schedulers.
    private val mWorkerSchedulers = workerSchedulers
    private val mUiSchedulers = uiScheduler

    // Progress.
    private val mOnUpdateProgress: Subject<ProgressState> = PublishSubject.create()
    private val mOnThrowError: Subject<Throwable> = PublishSubject.create()

    // Disposables.
    private val mDisposablesOnCreate = CompositeDisposable()

    override fun bindViewOnCreate(view: RxCancelContract.View) {
        mView = view

        // Long computation operation.
        mDisposablesOnCreate.add(
            // Start button.
            mView.onClickStart()
                .debounce(150, TimeUnit.MILLISECONDS)
                .throttleFirst(1000, TimeUnit.MILLISECONDS)
                .switchMap { _ ->
                    // First, switchMap convert the click to an action observable.
                    // Whenever a new click is received, switchMap interrupt and
                    // kills the existing ongoing observable and replace it with
                    // the new one.
                    // Second, subscribe to the action observable with a cancel
                    // throttle where the takeUntil self terminates and also kill
                    // the action observable when a cancel signal is received.
                    toShareAction()
                        .takeUntil(mCancelSrc)
                }
                .observeOn(mUiSchedulers)
                .subscribe { _ ->
                    mView.printLog("all finished!")
                })

        // Cancel signal.
        mDisposablesOnCreate.add(
            mView.onClickCancel()
                .debounce(150, TimeUnit.MILLISECONDS)
                .subscribe { _ ->
                    mCancelSrc.onNext(0)
                })

        // Clear log button.
        mDisposablesOnCreate.add(
            mView.onClickClearLog()
                .debounce(150, TimeUnit.MILLISECONDS)
                .observeOn(mUiSchedulers)
                .subscribe { _ ->
                    mView.clearLog()
                })

        // Close button.
        mDisposablesOnCreate.add(
            mView.onClickClose()
                .debounce(150, TimeUnit.MILLISECONDS)
                .observeOn(mUiSchedulers)
                .subscribe { _ ->
                    mNavigator.gotoBack()
                })

        // Progress.
        mDisposablesOnCreate.add(
            mOnUpdateProgress
                .observeOn(mUiSchedulers)
                .subscribe { state ->
                    when {
                        state.justStart -> mView.printLog("--- START ---")
                        state.justStop -> mView.printLog("---!!! STOP !!!---")
                        state.doing -> mView.printLog(
                            "doing %d%%...".format(
                                state.progress))
                    }
                })

        // Error.
        mDisposablesOnCreate.add(
            mOnThrowError
                .observeOn(mUiSchedulers)
                .subscribe { error ->
                    mView.showError(error)
                })
    }

    override fun unBindViewOnDestroy() {
        mDisposablesOnCreate.clear()
    }

    override fun onResume() {
        // DO NOTHING.
    }

    override fun onPause() {
        // DO NOTHING.
    }

    ///////////////////////////////////////////////////////////////////////////
    // Protected / Private Methods ////////////////////////////////////////////

    /**
     * Returns a DO-SOMETHING action.
     */
    private fun toShareAction(): Observable<Any> {
        // #1 observable simulating an arbitrary long-run process.
        return generateBmp()
            .compose(handleError(ProgressState(justStop = true)))
            // #2 observable that shows a dialog.
            .switchMap { _ ->
                mView.showConfirmDialog()
                    // The following code is just for logging.
                    .doOnSubscribe { _ -> mView.printLog("Show a confirmation dialog...") }
                    .subscribeOn(mUiSchedulers)
                    .observeOn(mUiSchedulers)
                    .doOnSuccess { v -> mView.printLog("Confirmation dialog returns %s".format(v)) }
                    .toObservable()
            }
            // #3
            .switchMap { b: Boolean ->
                if (b) {
                    shareToFacebook()
                        .compose(handleError(ProgressState(justStop = true)))
                } else {
                    Observable.just(ProgressState(justStop = true))
                }
            }
    }

    /**
     * An observable emitting the status of generating the Bitmap.
     */
    private fun generateBmp(): Observable<ProgressState> {
        return getSimulatingLongRunProcess()
    }

    /**
     * An observable emitting the status of sharing.
     */
    private fun shareToFacebook(): Observable<ProgressState> {
        return getSimulatingLongRunProcess()
    }

    /**
     * Returns a CANCEL action.
     */
    private fun toCancelAction(): Observable<ProgressState> {
        return Observable
            .just(ProgressState(justStop = true))
            .doOnNext { state -> mOnUpdateProgress.onNext(state) }
    }

    /**
     * Returns an Observable that emitting [ProgressState].
     */
    private fun getSimulatingLongRunProcess(): Observable<ProgressState> {
        return Observable
            // The first simulated long-run process.
            .intervalRange(
                // Start progress.
                1,
                // End progress.
                100,
                // Start delay.
                0,
                // Interval period.
                25, TimeUnit.MILLISECONDS)
            .map { value ->
                ProgressState(doing = true,
                              progress = value.toInt())
            }
            .compose(handleProgress())
            .compose(goUntilPreviousTaskStops())
    }

    /**
     * A transformer that massage [ProgressState] from upstream and bypass to
     * [mOnUpdateProgress] channel.
     */
    private fun handleProgress(): ObservableTransformer<ProgressState, ProgressState> {
        return ObservableTransformer { upstream ->
            upstream
                // Create a "start" state.
                .startWith(ProgressState(justStart = true))
                .map { state ->
                    return@map if (state.doing && state.progress == 100) {
                        val stopState = state.copy(justStop = true)

                        // Dispatch the progress and "stop" state.
                        mOnUpdateProgress.onNext(state)
                        mOnUpdateProgress.onNext(stopState)

                        stopState
                    } else {
                        // Dispatch the progress.
                        mOnUpdateProgress.onNext(state)

                        state
                    }
                }
        }
    }

    /**
     * A transformer that filters START and DOING [ProgressState] state.
     */
    private fun goUntilPreviousTaskStops(): ObservableTransformer<ProgressState, ProgressState> {
        return ObservableTransformer { upstream ->
            upstream
                .filter { state -> state.justStop }
                .debounce(300, TimeUnit.MILLISECONDS)
        }
    }

    /**
     * A transformer that catches the exception and convert it to an ERROR state.
     */
    private fun <T> handleError(item: T): ObservableTransformer<T, T> {
        return ObservableTransformer { upstream ->
            upstream.onErrorReturn { error: Throwable ->
                // Bypass the error to the error channel so that someone interested
                // to it get notified.
                mOnThrowError.onNext(error)

                // Return whatever you want~~~
                item
            }
        }
    }
}
