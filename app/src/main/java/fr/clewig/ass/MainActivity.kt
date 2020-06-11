package fr.clewig.ass

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import fr.clewig.asslib.AnonymousSimpleStatsManager

class MainActivity : AppCompatActivity() {
    private var ass: AnonymousSimpleStatsManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ass = AnonymousSimpleStatsManager(applicationContext)
        ass?.setup(true)
    }

    override fun onResume() {
        super.onResume()
        ass?.logScreen("4b344f25-b0cb-49cf-a865-c6626582332a")
    }
}
