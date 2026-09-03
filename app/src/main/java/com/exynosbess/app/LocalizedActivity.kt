package com.exynosbess.app

import android.app.Activity
import android.content.Context
import com.exynosbess.app.settings.LanguageManager

open class LocalizedActivity :
    Activity() {

    override fun attachBaseContext(
        newBase: Context
    ) {

        super.attachBaseContext(
            LanguageManager.wrap(
                newBase
            )
        )
    }
}
