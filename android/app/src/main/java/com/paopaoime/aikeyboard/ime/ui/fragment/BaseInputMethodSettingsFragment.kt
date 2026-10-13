package com.paopaoime.aikeyboard.ime.ui.fragment

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.annotation.LayoutRes
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.paopaoime.aikeyboard.compat.agent.ui.HostNavigationControl
import com.paopaoime.aikeyboard.ime.R

abstract class BaseInputMethodSettingsFragment(
    @LayoutRes private val layoutResId: Int
) : Fragment() {
    override fun onStart() {
        super.onStart()
        hideBottomNavigation()
    }

    override fun onResume() {
        super.onResume()
        hideBottomNavigation()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            hideBottomNavigation()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(layoutResId, container, false)
    }

    protected fun bindHeader(rootView: View, headerId: Int, backButtonId: Int? = null) {
        val header = rootView.findViewById<View>(headerId) ?: return
        val initialHeaderPaddingTop = header.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(header) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = initialHeaderPaddingTop + bars.top)
            insets
        }
        backButtonId?.let { id ->
            rootView.findViewById<View>(id)?.setOnClickListener {
                if (parentFragmentManager.backStackEntryCount > 0) {
                    parentFragmentManager.popBackStack()
                } else {
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    }

    protected fun applyCardShadow(
        rootView: View,
        ids: IntArray,
        ambientColor: String = "#24000000",
        spotColor: String = "#18000000",
    ) {
        val context = context ?: return
        val elevationPx = 2f * context.resources.displayMetrics.density
        val ambientShadow = Color.parseColor(ambientColor)
        val spotShadow = Color.parseColor(spotColor)
        ids.forEach { id ->
            rootView.findViewById<View>(id)?.apply {
                elevation = elevationPx
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    outlineAmbientShadowColor = ambientShadow
                    outlineSpotShadowColor = spotShadow
                }
            }
        }
    }

    protected fun bindLocalToggle(toggle: SwitchCompat?) {
        val actualToggle = toggle ?: return
        val thumbTint = ColorStateList.valueOf(Color.WHITE)
        val trackTint = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf()
            ),
            intArrayOf(
                Color.parseColor("#20C19B"),
                Color.parseColor("#D8D8D8")
            )
        )
        actualToggle.thumbTintList = thumbTint
        actualToggle.trackTintList = trackTint
        actualToggle.setOnCheckedChangeListener { _, _ -> }
    }

    protected fun bindPlaceholderClicks(rootView: View, ids: IntArray) {
        val message = getString(R.string.input_method_settings_placeholder_toast)
        ids.forEach { id ->
            rootView.findViewById<View>(id)?.setOnClickListener {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    protected fun showPlaceholderToast() {
        Toast.makeText(
            requireContext(),
            getString(R.string.input_method_settings_placeholder_toast),
            Toast.LENGTH_SHORT
        ).show()
    }

    protected fun showSingleChoiceDialog(
        title: String,
        options: Array<String>,
        checkedIndex: Int,
        onSelected: (Int) -> Unit,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setSingleChoiceItems(options, checkedIndex) { dialog, which ->
                dialog.dismiss()
                onSelected(which)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    protected fun showMessageDialog(
        title: String,
        message: String,
        positiveText: String = getString(android.R.string.ok),
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText, null)
            .show()
    }

    protected fun showConfirmDialog(
        title: String,
        message: String,
        positiveText: String,
        onConfirmed: () -> Unit,
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveText) { _, _ -> onConfirmed() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    protected fun openSettingsPage(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .hide(this)
            .add(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
    }

    private fun hideBottomNavigation() {
        (activity as? HostNavigationControl)?.setBottomNavigationVisibility(false)
    }
}
