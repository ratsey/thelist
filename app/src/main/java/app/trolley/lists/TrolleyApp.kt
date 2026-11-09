package app.trolley.lists

import android.app.Application
import app.trolley.lists.data.AppContainer

class TrolleyApp : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
