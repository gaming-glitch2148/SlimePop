package com.slimepop.asmr

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.slimepop.asmr.databinding.ActivityPrivacyBinding

class PrivacyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vb = ActivityPrivacyBinding.inflate(layoutInflater)
        setContentView(vb.root)

        vb.tvPrivacyPolicy.text = """
            Privacy Policy
            Last Updated: ${java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())}

            1. Information Collection
            Slime Pop ("the App") uses third-party services that may collect information used to identify you:
            - Google Play Services (Authentication and Cloud Saves)
            - Google AdMob (Advertising)
            - Google Play Billing (In-app Purchases)

            2. Use of Information
            We use the information collected to:
            - Provide and maintain the game's functionality.
            - Save your progress and purchases across devices via Google Play Games.
            - Show relevant advertisements.

            3. Local Storage
            The App stores game progress (coins, items) locally on your device and syncs it with Google Play Games Services if signed in.

            4. Third-Party Services
            The App uses AdMob for ads. AdMob may use device identifiers to personalize ads. You can manage ad settings in your device's Google settings.

            5. Children's Privacy
            This App does not knowingly collect personal identifiable information from children under 13.

            6. Contact
            For any questions, contact us via the developer page on the Google Play Store.
        """.trimIndent()

        vb.btnBack.setOnClickListener { finish() }
    }
}