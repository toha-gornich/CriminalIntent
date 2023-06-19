package com.bignerdranch.android.criminalintent

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bignerdranch.android.criminalintent.databinding.FragmentCrimeDetailBinding
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

private const val TAG = "CrimeDetailFragment"
private const val DATE_FORMAT = "EEE, MMM, dd"

class CrimeDetailFragment : Fragment() {

    private var _binding: FragmentCrimeDetailBinding? = null
    private val binding
        get() = checkNotNull(_binding) {
            "Cannot access binding because it is null. Is the view visible?"
        }

    private val args: CrimeDetailFragmentArgs by navArgs()

    private val crimeDetailViewModel: CrimeDetailViewModel by viewModels {
        CrimeDetailViewModelFactory(args.crimeId)
    }

    private val selectSuspect = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? -> uri?.let { parseContactSelection(it) } }

    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { didTakePhoto: Boolean ->
        if (didTakePhoto && photoName != null) {
            crimeDetailViewModel.updateCrime { oldCrime ->
                oldCrime.copy(photoFileName = photoName)
            }
        }
    }
    private var photoName: String? = null
//    private val selectSuspectPhone = registerForActivityResult(
//        ActivityResultContracts.PickContact()
//    ) { uri: Uri? ->uri?.let { parseContactSelectionPhone(it) } }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
//        val callback = requireActivity().onBackPressedDispatcher.addCallback(this) {
//        }
//
//        }
//        val crime = Crime(
//            id = UUID.randomUUID(),
//            title = "",
//            date = Date(),
//            isSolved = false
//        )
//
//        Log.d(TAG, "The crime ID is: ${args.crimeId}")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding =
            FragmentCrimeDetailBinding.inflate(inflater, container, false)
        return binding.root
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.apply {
            crimeTitle.doOnTextChanged { text, _, _, _ ->
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(title = text.toString())
                }
            }
            crimeSuspect.setOnClickListener { selectSuspect.launch(null) }
            val selectSuspectIntent = selectSuspect.contract.createIntent(
                requireContext(),
                null
            )
            crimeSuspect.isEnabled = canResolveIntent(selectSuspectIntent)

            crimeCamera.setOnClickListener {
                photoName = "IMG_${Date()}.JPG"
                val photoFile = File(
                    requireContext().applicationContext.filesDir,
                    photoName
                )
                val photoUri = FileProvider.getUriForFile(
                    requireContext(),
                    "com.bignerdranch.android.criminalintent.fileprovider",
                    photoFile
                )

                takePhoto.launch(photoUri)
            }
            val captureImageIntent = takePhoto.contract.createIntent(
                requireContext(),
                Uri.parse("")
            )
            crimeCamera.isEnabled = canResolveIntent(captureImageIntent)

            crimeSolved.setOnCheckedChangeListener { _, isChecked ->
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(isSolved = isChecked)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                crimeDetailViewModel.crime.collect { crime ->
                    crime?.let { updateUi(it) }
                }
            }
        }

        //коли очем повернути дані після нашого діалога
        setFragmentResultListener(
            DatePickerFragment.REQUEST_KEY_DATE
        ) { _, bundle ->
            val newDate =
                bundle.getSerializable(DatePickerFragment.BUNDLE_KEY_DATE) as Date
            crimeDetailViewModel.updateCrime { it.copy(date = newDate) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateUi(crime: Crime) {
        binding.apply {
            if (crimeTitle.text.toString() != crime.title) {
                crimeTitle.setText(crime.title)
            }
            crimeDate.text = crime.date.toString()
            crimeDate.setOnClickListener {
                findNavController().navigate(
                    CrimeDetailFragmentDirections.selectDate(crime.date)
                )
            }
            crimeSolved.isChecked = crime.isSolved

            crimeReport.setOnClickListener {
                val reportIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getCrimeReport(crime))

                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(R.string.crime_report_subject)
                    )
                }
                //
                crimeSuspect.text = crime.suspect.ifEmpty { getString(R.string.crime_suspect_text) }
                val chooserIntent = Intent.createChooser(
                    reportIntent,
                    getString(R.string.send_report)
                )
                startActivity(chooserIntent)

            }
            updatePhoto(crime.photoFileName)

            crimeCall.setOnClickListener {
                val phoneIntent = Intent(Intent.ACTION_DIAL).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "")

                }
                crimeSuspect.text = crime.suspect.ifEmpty { getString(R.string.crime_suspect_text) }
                startActivity(phoneIntent)
            }


        }
    }

    //    private  fun parseContactSelectionPhone(contactUri: Uri){
//        val queryFields = arrayOf(ContactsContract.Contacts._ID)
//        val queryCursor =
//            requireActivity().contentResolver.query(contactUri, queryFields, null, null, null)
//        queryCursor?.use { cursor ->
//            if (cursor.moveToFirst()) {
//                val suspect = cursor.getString(0)
//                crimeDetailViewModel.updateCrime { oldCrime ->
//                    oldCrime.copy(suspect = suspect)
//                }
//            }
//        }
//    }
    private fun getCrimeReport(crime: Crime): String {
        val solvedString = if (crime.isSolved) {
            getString(R.string.crime_report_solved)
        } else {
            getString(R.string.crime_report_unsolved)
        }

        val dateString = DateFormat.format(DATE_FORMAT, crime.date).toString()
        val suspectText = if (crime.suspect.isBlank()) {
            getString(R.string.crime_report_no_suspect)
        } else {
            getString(R.string.crime_report_suspect, crime.suspect)
        }

        return getString(
            R.string.crime_report,
            crime.title, dateString, solvedString, suspectText
        )
    }


    private fun parseContactSelection(contactUri: Uri) {
        val queryFields = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)

        val queryCursor =
            requireActivity().contentResolver.query(contactUri, queryFields, null, null, null)

        queryCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val suspect = cursor.getString(0)
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(suspect = suspect)
                }
            }
        }
    }

    //    пристрій не вийде з ладу, якщо користувач спробує вибрати підозрюваного, коли на пристрої немає програми контактів.
    private fun canResolveIntent(intent: Intent): Boolean {
//        PackageManager знає про всі компоненти, встановлені на пристрої Android, включаючи всі його дії.
        val packageManager: PackageManager = requireActivity().packageManager
//        Прапор MATCH_DEFAULT_ONLY обмежує цей пошук діяльністю з прапором CATEGORY_DEFAULT, як і startActivity(Intent).
        val resolveActivity: ResolveInfo? =
            packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
//        Якщо цей пошук буде успішним, він поверне екземпляр ResolveInfo, який розповість вам про те, яку активність він знайшов
        return resolveActivity != null
    }


    private fun updatePhoto(photoFileName: String?) {
        if (binding.crimePhoto.tag != photoFileName) {
            val photoFile = photoFileName?.let {
                File(requireContext().applicationContext.filesDir, it)
            }

            if (photoFile?.exists() == true) {
                binding.crimePhoto.doOnLayout { measuredView ->
                    val scaledBitmap = getScaledBitmap(
                        photoFile.path,
                        measuredView.width,
                        measuredView.height
                    )
                    binding.crimePhoto.setImageBitmap(scaledBitmap)
                    binding.crimePhoto.tag = photoFileName
                }
            } else {
                binding.crimePhoto.setImageBitmap(null)
                binding.crimePhoto.tag = null
            }
        }
    }
}
