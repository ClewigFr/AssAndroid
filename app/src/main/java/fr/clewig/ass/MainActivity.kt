package fr.clewig.ass

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import fr.clewig.asslib.AnonymousSimpleStatsManager
import fr.clewig.asslib.AssConfig

class MainActivity : AppCompatActivity() {
    private var ass: AnonymousSimpleStatsManager? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ass = AnonymousSimpleStatsManager.getInstance(
            application,
            AssConfig(
                endpoint = "https://gentle-inlet-02091.herokuapp.com",
                // The application UUID shown in the dashboard ("Mes applications")
                appId = "00000000-0000-4000-8000-000000000000",
                verbose = true
            )
        )
    }

    override fun onResume() {
        super.onResume()
        ass?.logScreen("4b344f25-b0cb-49cf-a865-c6626582332a")
    }
}
