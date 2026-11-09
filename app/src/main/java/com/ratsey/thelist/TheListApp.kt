package com.ratsey.thelist

import android.app.Application
import com.ratsey.thelist.data.AppContainer

class TheListApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
