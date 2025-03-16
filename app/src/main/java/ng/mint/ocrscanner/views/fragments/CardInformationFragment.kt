package ng.mint.ocrscanner.views.fragments

import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.findNavController
import by.kirich1409.viewbindingdelegate.viewBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ng.mint.ocrscanner.R
import ng.mint.ocrscanner.contracts.ScanCreditCardContract
import ng.mint.ocrscanner.databinding.FragmentCardInformationBinding
import ng.mint.ocrscanner.model.CardResult
import ng.mint.ocrscanner.networking.ConnectionDetector
import ng.mint.ocrscanner.viewmodel.CardsViewModel
import ng.mint.ocrscanner.views.activities.BaseActivity
import ng.mint.ocrscanner.views.common.MessageDialogManager
import ng.mint.ocrscanner.views.common.ProgressDialogManager
import javax.inject.Inject

@AndroidEntryPoint
class CardInformationFragment(
    var viewModel: CardsViewModel? = null
) : Fragment(R.layout.fragment_card_information) {

    sealed class BinValidationResult {
        object Valid : BinValidationResult()
        object Empty : BinValidationResult()
        data class Invalid(@StringRes val errorMessageRes: Int) : BinValidationResult()
    }

    companion object {

        const val MY_SCAN_REQUEST_CODE = 500
    }

    val binding by viewBinding(FragmentCardInformationBinding::bind)

    @Inject
    lateinit var connectionDetector: ConnectionDetector

    @Inject
    lateinit var progressDialog: ProgressDialogManager

    @Inject
    lateinit var messageDialog: MessageDialogManager

    private val binValidator = BinInputValidator()
    private val openScanCreditCard = registerForActivityResult(ScanCreditCardContract()) { cardNumber ->
        cardNumber?.let { number ->
            val panNumber = number.replace(" ", "")
            if (panNumber.length > 5) {
                processCard(panNumber)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = viewModel ?: ViewModelProvider(this)[CardsViewModel::class.java]
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupBinInputValidation()

        binding.nextButton.setOnClickListener {
            (activity as? BaseActivity)?.hideKeyboard()
            val panNumber = binding.panInputField.text.toString()
            if (panNumber.length > 5) {
                processCard(panNumber.take(8))
            }
        }

        binding.scanButton.setOnClickListener { openScanCreditCard.launch(MY_SCAN_REQUEST_CODE) }

        binding.recentCards.setOnClickListener {
            (activity as? BaseActivity)?.hideKeyboard()
            it.findNavController()
                .navigate(R.id.action_cardInformationFragment_to_recentlyViewedCardsFragment)

        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel?.cardResult?.collectLatest { observeData(it) }
            }
        }


    }

    private fun observeData(cardResult: CardResult) {
        when (cardResult) {
            is CardResult.Loading -> {
                binding.nextButton.isClickable = false
                progressDialog.showLoading(getString(R.string.processing))
            }
            is CardResult.Success -> {
                progressDialog.dismissDialog()
                binding.nextButton.isClickable = true
                binding.bankData = cardResult.data
                binding.cardInformationLayout.visibility = View.VISIBLE
            }
            is CardResult.Error -> {
                progressDialog.dismissDialog()
                binding.nextButton.isClickable = true
                binding.cardInformationLayout.visibility = View.GONE
                
                val message = when (cardResult.errorType) {
                    CardResult.ErrorType.RATE_LIMIT -> {
                        // Store card for later processing when offline
                        viewModel?.insertOfflineCard(binding.panInputField.text.toString())
                        getString(R.string.rate_limit_error)
                    }
                    CardResult.ErrorType.NETWORK -> {
                        // Store card for later processing when offline
                        val bin = binding.panInputField.text.toString()
                        viewModel?.insertOfflineCard(bin)
                        String.format(getString(R.string.no_internet_card_saved_for_future), bin)
                    }
                    CardResult.ErrorType.NOT_FOUND -> getString(R.string.no_bin_number_was_found)
                    CardResult.ErrorType.SERVER_ERROR -> getString(R.string.server_error_try_again)
                    CardResult.ErrorType.UNKNOWN -> getString(R.string.unknown_error_try_again)
                }
                messageDialog.displayMessage(message)
                
                if (cardResult.errorType in arrayOf(
                    CardResult.ErrorType.NETWORK,
                    CardResult.ErrorType.RATE_LIMIT
                )) {
                    binding.panInputField.text = null
                }
            }
            CardResult.EmptyState -> {
                progressDialog.dismissDialog()
                binding.nextButton.isClickable = true
                binding.cardInformationLayout.visibility = View.GONE
            }
        }
    }

    private fun setupBinInputValidation() {
        binding.panInputField.doAfterTextChanged { text ->
            binding.cardInformationLayout.visibility = View.GONE
            
            val input = text?.toString() ?: ""
            when (val validationResult = binValidator.validate(input)) {
                is BinValidationResult.Valid -> {
                    binding.panInputLayout.error = null
                    binding.nextButton.isEnabled = true
                }
                is BinValidationResult.Invalid -> {
                    binding.panInputLayout.error = getString(validationResult.errorMessageRes)
                    binding.nextButton.isEnabled = false
                }
                BinValidationResult.Empty -> {
                    binding.panInputLayout.error = null
                    binding.nextButton.isEnabled = false
                }
            }
        }
    }

    private class BinInputValidator {
        fun validate(input: String): BinValidationResult = when {
            input.isEmpty() -> BinValidationResult.Empty
            input.length < 6 -> BinValidationResult.Invalid(R.string.bin_too_short)
            !input.all { it.isDigit() } -> BinValidationResult.Invalid(R.string.bin_invalid_characters)
            else -> BinValidationResult.Valid
        }
    }

    private fun processCard(value: String) {
        val sanitizedBin = value.trim().take(8)
        
        // Validate BIN format
        if (!isValidBin(sanitizedBin)) {
            messageDialog.displayMessage(getString(R.string.invalid_bin_format))
            return
        }

        when {
            connectionDetector.isConnectingToInternet() -> {
                binding.nextButton.isClickable = false
                progressDialog.showLoading(getString(R.string.processing))
                viewModel?.processCardDetail(sanitizedBin)
            }
            else -> {
                messageDialog.displayMessage(
                    String.format(getString(R.string.no_internet_card_saved_for_future), sanitizedBin)
                )
                viewModel?.insertOfflineCard(sanitizedBin)
                binding.panInputField.text = null
            }
        }
    }

    private fun isValidBin(bin: String): Boolean {
        // BIN should be 6-8 digits
        return bin.length in 6..8 && bin.all { it.isDigit() }
    }


}