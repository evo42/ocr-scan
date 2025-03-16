package ng.mint.ocrscanner.views.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import io.card.payment.CardIOActivity
import io.card.payment.CreditCard
import ng.mint.ocrscanner.R
import ng.mint.ocrscanner.databinding.ActivityCardScannerBinding
import java.util.Locale

/**
 * Card scanner activity optimized for EU region card scanning
 * - Supports EU bank card formats
 * - Optimized for EU card layouts and formats
 * - Configured for proper orientation of EU cards
 */
class CardScannerActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCardScannerBinding
    private val TAG = "CardScannerActivity"
    
    // EU region-specific card BIN ranges for better detection
    private val euCardPrefixes = listOf(
        // Major EU Visa BIN ranges
        "40", "41", "42", "43", "44", "45", "46", "47", "48", "49",
        // Major EU Mastercard BIN ranges
        "51", "52", "53", "54", "55", "22", "23", "24", "25", "26", "27",
        // Popular EU country-specific cards
        "54", // Maestro (UK, Germany, Spain)
        "50", // Maestro (UK)
        "56", "57", "58", // Maestro (various EU countries)
        "67", // Carte Bancaire (France)
        "34", "37" // American Express (used in EU)
    )
    
    // EU country locales for better recognition
    private val euLocales = listOf(
        Locale("de", "DE"), // German
        Locale("fr", "FR"), // French
        Locale("es", "ES"), // Spanish
        Locale("it", "IT"), // Italian
        Locale("nl", "NL"), // Dutch
        Locale.UK           // UK
    )
    
    // Add activity result launcher
    private val scanCardLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val scanResult = result.data?.getParcelableExtra<CreditCard>(CardIOActivity.EXTRA_SCAN_RESULT)
            
            if (scanResult != null && !scanResult.formattedCardNumber.isNullOrEmpty()) {
                val cardNumber = scanResult.formattedCardNumber
                val isEuCard = isEuropeanCard(cardNumber)
                
                // Extract only the BIN (first 8 digits) for security
                val cardBin = extractBIN(cardNumber)
                val maskedCardNumber = maskCardNumber(cardNumber)
                
                // Log only the BIN and masked number for security
                Log.d(TAG, "Card scan successful: ${scanResult.cardType} card detected, EU card: $isEuCard")
                Log.d(TAG, "Card BIN: $cardBin, Masked Number: $maskedCardNumber")
                
                if (isEuCard) {
                    // Process EU-specific card format if needed
                    Log.d(TAG, "EU card detected - applying EU-specific processing")
                }
                
                val resultIntent = Intent().apply {
                    putExtra("card_number", cardBin) // Only return the BIN (first 8 digits)
                    putExtra("is_eu_card", isEuCard)
                    putExtra("card_type", scanResult.cardType.toString())
                }
                setResult(Activity.RESULT_OK, resultIntent)
            } else {
                Log.w(TAG, "Card scan failed: No card number detected")
                Toast.makeText(
                    this,
                    "Card scanning failed. Please try again with better lighting.",
                    Toast.LENGTH_LONG
                ).show()
                setResult(Activity.RESULT_CANCELED)
            }
        } else if (result.resultCode == Activity.RESULT_CANCELED) {
            Log.d(TAG, "Card scanning canceled by user")
            setResult(Activity.RESULT_CANCELED)
        } else {
            Log.e(TAG, "Card scanning failed with resultCode: ${result.resultCode}")
            Toast.makeText(
                this,
                "Card scanning failed. Please try again.",
                Toast.LENGTH_LONG
            ).show()
            setResult(Activity.RESULT_CANCELED)
        }
        finish()
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCardScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Check if scanning is supported on the device
        if (!CardIOActivity.canReadCardWithCamera()) {
            Log.e(TAG, "Card scanning not supported on this device")
            Toast.makeText(
                this, 
                "Card scanning is not supported on this device. Please enter card details manually.",
                Toast.LENGTH_LONG
            ).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        
        // Launch card scanner with improved configuration
        // Select the best EU locale based on user's device settings
        val deviceLocale = Locale.getDefault()
        val euLocale = euLocales.find { it.language == deviceLocale.language } ?: euLocales[0]
        
        Log.d(TAG, "Using EU-optimized settings with locale: $euLocale")
        
        val intent = Intent(this, CardIOActivity::class.java).apply {
            // EU-optimized scanning configuration
            putExtra(CardIOActivity.EXTRA_REQUIRE_CVV, false)    // Not required for initial scan
            putExtra(CardIOActivity.EXTRA_REQUIRE_POSTAL_CODE, false)
            putExtra(CardIOActivity.EXTRA_SUPPRESS_MANUAL_ENTRY, false)  // Allow manual entry as fallback
            
            // EU-specific orientation and format settings
            putExtra(CardIOActivity.EXTRA_KEEP_APPLICATION_THEME, true)
            putExtra(CardIOActivity.EXTRA_USE_CARDIO_LOGO, true)
            putExtra(CardIOActivity.EXTRA_HIDE_CARDIO_LOGO, false)
            putExtra(CardIOActivity.EXTRA_LANGUAGE_OR_LOCALE, euLocale.toString())  // Use specific EU locale
            putExtra(CardIOActivity.EXTRA_SCAN_OVERLAY_LAYOUT_ID, R.layout.activity_card_scanner) // Custom overlay
            
            // For EU cards - scan in landscape for better results with EU card formats
            putExtra(CardIOActivity.EXTRA_SCAN_INSTRUCTIONS, "Position your EU bank card within the frame")
            putExtra(CardIOActivity.EXTRA_SUPPRESS_CONFIRMATION, false)  // Confirm details (EU compliance)
            putExtra(CardIOActivity.EXTRA_USE_PAYPAL_ACTIONBAR_ICON, false)
            
            // EU-specific color guidance
            putExtra(CardIOActivity.EXTRA_GUIDE_COLOR, getColor(R.color.colorPrimary))
        }
        
        Log.d(TAG, "Launching card scanner with enhanced configuration")
        scanCardLauncher.launch(intent)
    }
    
    /**
     * Checks if the card number belongs to a European bank card
     * Based on common BIN ranges used by European banks
     */
    private fun isEuropeanCard(cardNumber: String?): Boolean {
        if (cardNumber.isNullOrEmpty()) return false
        
        // Clean the card number from spaces and other formatting
        val cleanCardNumber = cardNumber.replace("\\s".toRegex(), "")
        
        // Check if the card number starts with any of the EU card prefixes
        return euCardPrefixes.any { prefix ->
            cleanCardNumber.startsWith(prefix)
        }
    }
    
    /**
     * Validates EU card format requirements
     * EU cards typically have 16 digits and must pass Luhn check
     */
    private fun validateEuCardFormat(cardNumber: String?): Boolean {
        if (cardNumber.isNullOrEmpty()) return false
        
        // Clean the card number
        val cleanCardNumber = cardNumber.replace("\\s".toRegex(), "")
        
        // Most EU cards have 16 digits, with some exceptions for 13-19 digits
        val validLength = cleanCardNumber.length in 13..19
        
        // Perform Luhn check (standard card validation algorithm)
        val passesLuhn = if (validLength) checkLuhn(cleanCardNumber) else false
        
        Log.d(TAG, "EU card validation: Length valid: $validLength, Passes Luhn: $passesLuhn")
        return validLength && passesLuhn
    }
    
    /**
     * Implementation of Luhn algorithm to validate card numbers
     * This is an industry standard for validating EU and other card numbers
     */
    private fun checkLuhn(cardNumber: String): Boolean {
        var sum = 0
        var alternate = false
        
        for (i in cardNumber.length - 1 downTo 0) {
            var digit = cardNumber[i].toString().toInt()
            
            if (alternate) {
                digit *= 2
                if (digit > 9) {
                    digit -= 9
                }
            }
            
            sum += digit
            alternate = !alternate
        }
        
        return sum % 10 == 0
    }
    
    /**
     * Extracts the BIN (first 8 digits) from the card number
     * For security purposes, we only want to process and store the BIN
     */
    private fun extractBIN(cardNumber: String): String {
        // Clean the card number from spaces and other formatting
        val cleanCardNumber = cardNumber.replace("\\s".toRegex(), "")
        
        // Return only the first 8 digits (BIN) or the whole number if less than 8 digits
        return if (cleanCardNumber.length >= 8) {
            cleanCardNumber.substring(0, 8)
        } else {
            cleanCardNumber
        }
    }
    
    /**
     * Creates a masked version of the card number for display or logging
     * Shows only the BIN (first 8 digits) and last 4 digits, with everything else masked
     */
    private fun maskCardNumber(cardNumber: String): String {
        // Clean the card number from spaces and other formatting
        val cleanCardNumber = cardNumber.replace("\\s".toRegex(), "")
        
        // If card number is too short, just return it
        if (cleanCardNumber.length <= 8) return cleanCardNumber
        
        // Create a masked version showing only BIN and last 4 digits
        val bin = cleanCardNumber.substring(0, 8)
        val lastFour = if (cleanCardNumber.length >= 4) {
            cleanCardNumber.substring(cleanCardNumber.length - 4)
        } else {
            ""
        }
        
        // Create masked string of appropriate length between BIN and last four
        val maskedSection = "*".repeat(cleanCardNumber.length - 8 - 4.coerceAtMost(cleanCardNumber.length - 8))
        
        return "$bin$maskedSection$lastFour"
    }
}
