package com.testobject

import com.facebook.react.ReactActivity
import com.facebook.react.ReactActivityDelegate
import com.facebook.react.defaults.DefaultNewArchitectureEntryPoint.fabricEnabled
import com.facebook.react.defaults.DefaultReactActivityDelegate
import com.reactnativepdfannotation.PdfAnnotationLifecycle

class MainActivity : ReactActivity() {

    override fun getMainComponentName(): String = "TestObject"

    override fun createReactActivityDelegate(): ReactActivityDelegate =
        DefaultReactActivityDelegate(this, mainComponentName, fabricEnabled)

    override fun onPause() {
        super.onPause()
        PdfAnnotationLifecycle.onHostPause(this)
    }

    override fun onStop() {
        super.onStop()
        PdfAnnotationLifecycle.onHostStop(this)
    }

    override fun onResume() {
        super.onResume()
        HostActivities.activity = this
        PdfAnnotationLifecycle.onHostResume(this)
    }

    override fun onDestroy() {
        if (HostActivities.activity === this) {
            HostActivities.activity = null
        }
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        PdfAnnotationLifecycle.onTrimMemory(this, level)
    }
}