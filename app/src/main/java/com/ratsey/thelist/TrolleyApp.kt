package com.ratsey.thelist

import android.app.Application
import com.ratsey.thelist.data.AppContainer

class TrolleyApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
