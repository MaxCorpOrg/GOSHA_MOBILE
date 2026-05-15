package com.maxcorp.gosha.mobile

import android.view.MotionEvent
import android.view.View
import android.view.animation.OvershootInterpolator

object UiPlayful {
    fun enhanceButtons(vararg views: View?) {
        views.filterNotNull().forEach { view ->
            view.setOnTouchListener { touched, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        touched.animate()
                            .scaleX(0.96f)
                            .scaleY(0.96f)
                            .setDuration(90L)
                            .start()
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        touched.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(160L)
                            .setInterpolator(OvershootInterpolator(1.3f))
                            .start()
                    }
                }
                false
            }
        }
    }
}
