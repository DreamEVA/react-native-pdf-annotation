package com.testobject

import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider

class PdfDocumentPickerPackage : BaseReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? {
        return if (name == PdfDocumentPickerModule.NAME) {
            PdfDocumentPickerModule(reactContext)
        } else {
            null
        }
    }

    override fun getReactModuleInfoProvider(): ReactModuleInfoProvider {
        return ReactModuleInfoProvider {
            mapOf(
                PdfDocumentPickerModule.NAME to ReactModuleInfo(
                    PdfDocumentPickerModule.NAME,
                    PdfDocumentPickerModule.NAME,
                    false,
                    false,
                    false,
                    false,
                ),
            )
        }
    }
}
