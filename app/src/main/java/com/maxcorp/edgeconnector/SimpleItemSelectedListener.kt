package com.maxcorp.gosha.mobile

import android.view.View
import android.widget.AdapterView

class SimpleItemSelectedListener(private val onSelect: () -> Unit) : AdapterView.OnItemSelectedListener {
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        onSelect()
    }

    override fun onNothingSelected(parent: AdapterView<*>?) = Unit
}
