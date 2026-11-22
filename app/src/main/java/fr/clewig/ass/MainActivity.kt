package fr.clewig.ass

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import fr.clewig.asslib.AnonymousSimpleStatsManager
import fr.clewig.asslib.AssConfig

/**
 * Demo / test bench for the library: one button per page logs a view of it,
 * plus a button that logs 25 views at once (more than the batch threshold).
 * Endpoint, application UUID and page UUIDs come from BuildConfig (see app/build.gradle).
 */
class MainActivity : AppCompatActivity() {
    private lateinit var ass: AnonymousSimpleStatsManager
    private lateinit var status: TextView
    private var logged = 0
    private val pages: List<String> by lazy {
        BuildConfig.ASS_PAGES.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ass = AnonymousSimpleStatsManager.getInstance(
            application,
            AssConfig(
                endpoint = BuildConfig.ASS_ENDPOINT,
                // The application UUID shown in the dashboard ("Mes applications")
                appId = BuildConfig.ASS_APP_ID,
                verbose = true
            )
        )

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }
        status = TextView(this).apply { text = "Endpoint: ${BuildConfig.ASS_ENDPOINT}\nApp: ${BuildConfig.ASS_APP_ID}" }
        root.addView(status)
        pages.forEachIndexed { i, page ->
            root.addView(Button(this).apply {
                text = "Log page ${i + 1}"
                contentDescription = "log_page_${i + 1}"
                setOnClickListener { log(page) }
            })
        }
        root.addView(Button(this).apply {
            text = "Log 25 views"
            contentDescription = "log_25"
            setOnClickListener { repeat(25) { log(pages[it % pages.size]) } }
        })
        setContentView(root)
    }

    private fun log(page: String) {
        ass.logScreen(page)
        logged++
        status.text = "Endpoint: ${BuildConfig.ASS_ENDPOINT}\nApp: ${BuildConfig.ASS_APP_ID}\nLogged: $logged"
    }

    override fun onResume() {
        super.onResume()
        pages.firstOrNull()?.let { log(it) }
    }
}
