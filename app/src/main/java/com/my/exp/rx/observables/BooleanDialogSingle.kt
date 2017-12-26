// Copyright (c) 2017-present Cardinalblue
//
// Author: boy@cardinalblue.com
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

package com.my.exp.rx.observables

import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import io.reactivex.Single
import io.reactivex.SingleObserver
import io.reactivex.android.MainThreadDisposable

/**
 * An observable encapsulate [AlertDialog] and emit a boolean to downstream.
 */
class BooleanDialogSingle(
    private val mSource: AlertDialog.Builder,
    private val mPositiveButtonString: String,
    private val mNegativeButtonString: String)
    : Single<Boolean>() {

    override fun subscribeActual(observer: SingleObserver<in Boolean>) {
        val disposable = Disposable(observer)

        mSource.setPositiveButton(mPositiveButtonString, disposable)
        mSource.setNegativeButton(mNegativeButtonString, disposable)
        mSource.setOnCancelListener(disposable)

        disposable.actual = mSource.create()
        disposable.actual.show()

        observer.onSubscribe(disposable)
    }

    ///////////////////////////////////////////////////////////////////////////
    // Clazz //////////////////////////////////////////////////////////////////

    private class Disposable internal constructor(
        internal val observer: SingleObserver<in Boolean>)
        : MainThreadDisposable(),
          DialogInterface.OnClickListener,
          DialogInterface.OnCancelListener {

        // Given later on...
        internal lateinit var actual: AlertDialog

        override fun onDispose() {
            actual.dismiss()
        }

        override fun onClick(dialog: DialogInterface, which: Int) {
            if (which == DialogInterface.BUTTON_POSITIVE) {
                observer.onSuccess(true)
            } else {
                observer.onSuccess(false)
            }
        }

        override fun onCancel(dialog: DialogInterface?) {
            observer.onSuccess(false)
        }
    }
}
