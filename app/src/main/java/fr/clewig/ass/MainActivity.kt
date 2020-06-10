package fr.clewig.ass

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import fr.clewig.asslib.AnonymousSimpleStatsManager

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val ass = AnonymousSimpleStatsManager()
        ass.setup(true)
        ass.logScreen("d9ced6a4-4151-4bd2-b9d9-457bf83d03a9")

    }
}
