package app.kkambbak

import android.app.Application

class KkambbakApplication : Application() {
    lateinit var controller: Controller
        private set

    override fun onCreate() {
        super.onCreate()
        controller = Controller(this)
        controller.start()
    }
}
