// Copyright (c) 2016-present boyw165
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

package com.my.exp.rx.view

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.view.View
import com.jakewharton.rxbinding2.view.RxView
import com.my.exp.rx.R
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.subjects.PublishSubject
import java.util.concurrent.TimeUnit


class ExpListActivity : AppCompatActivity() {

    // View.
    private val mBtnExp: View by lazy { findViewById<View>(R.id.btn_exp_menu) }
    private val mBtnExpMenu: AlertDialog by lazy {
        AlertDialog.Builder(this@ExpListActivity)
            .setTitle(R.string.pick_exp)
            .setItems(R.array.menu_list, { dialog: DialogInterface, which: Int ->
                mOnClickMenuItem.onNext(which)
            })
            .create()
    }

    private val mOnClickMenuItem = PublishSubject.create<Int>()

    private val mDisposablesOnCreate: CompositeDisposable = CompositeDisposable()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_exp_list)

        // Exp menu button.
        mDisposablesOnCreate.add(
            RxView.clicks(mBtnExp)
                .debounce(150, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { _ ->
                    mBtnExpMenu.show()
                })

        // Exp menu.
        mDisposablesOnCreate.add(
            mOnClickMenuItem
                .debounce(100, TimeUnit.MILLISECONDS)
                .throttleFirst(1000, TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { which: Int ->
                    when (which) {
                        0 -> {
                            startActivity(Intent(
                                this@ExpListActivity,
                                ExampleOfRxCancelActivity::class.java))
                        }
                    }
                })

        // Actively show the menu.
        mBtnExpMenu.show()
    }

    override fun onDestroy() {
        super.onDestroy()

        mDisposablesOnCreate.clear()
    }
}
