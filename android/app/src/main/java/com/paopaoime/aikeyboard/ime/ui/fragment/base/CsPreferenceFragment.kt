
package com.paopaoime.aikeyboard.ime.ui.fragment.base

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.paopaoime.aikeyboard.ime.view.widget.applyNavBarInsetsBottomPadding

abstract class CsPreferenceFragment : PreferenceFragmentCompat() {

    open protected fun isSingleCardMode(): Boolean = false

    override fun onCreateAdapter(preferenceScreen: androidx.preference.PreferenceScreen): androidx.recyclerview.widget.RecyclerView.Adapter<*> {
        return object : androidx.preference.PreferenceGroupAdapter(preferenceScreen) {
            override fun onBindViewHolder(holder: androidx.preference.PreferenceViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                val preference = getItem(position)
                if (preference is androidx.preference.PreferenceCategory) {
                    return
                }

                if (isSingleCardMode()) {
                    holder.itemView.setBackgroundResource(com.paopaoime.aikeyboard.ime.R.drawable.bg_settings_card_single)
                    val params = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
                    if (params != null) {
                        val density = holder.itemView.context.resources.displayMetrics.density
                        val marginH = (16 * density).toInt()
                        val marginV = (10 * density).toInt() 
                        
                        params.marginStart = marginH
                        params.marginEnd = marginH
                        params.topMargin = marginV
                        params.bottomMargin = marginV
                        holder.itemView.layoutParams = params
                    }
                    return
                }

                // 计算圆角背景
                // 逻辑：
                // 1. 如果上一个是Category或null，则是Top
                // 2. 如果下一个是Category或null，则是Bottom
                // 3. 结合起来：
                //    Top + Bottom -> Single
                //    Top -> Top
                //    Bottom -> Bottom
                //    None -> Middle

                val prevPos = position - 1
                val nextPos = position + 1
                
                val isPrevCategoryOrNull = if (prevPos < 0) true else getItem(prevPos) is androidx.preference.PreferenceCategory
                val isNextCategoryOrNull = if (nextPos >= itemCount) true else getItem(nextPos) is androidx.preference.PreferenceCategory

                val bgRes = when {
                    isPrevCategoryOrNull && isNextCategoryOrNull -> com.paopaoime.aikeyboard.ime.R.drawable.bg_settings_card_single
                    isPrevCategoryOrNull -> com.paopaoime.aikeyboard.ime.R.drawable.bg_settings_card_top
                    isNextCategoryOrNull -> com.paopaoime.aikeyboard.ime.R.drawable.bg_settings_card_bottom
                    else -> com.paopaoime.aikeyboard.ime.R.drawable.bg_settings_card_middle
                }

                // 找到根布局并设置背景
                // 注意：AndroidX Preference的itemView通常是一个LinearLayout或ConstraintLayout
                // 我们的自定义布局 item_preference_card_single 已经有一个背景了，
                // 如果我们在这里设置，可能会覆盖或者不起作用，取决于holder.itemView到底指向谁。
                // 实际上，layoutResource指定的是内容，PreferenceGroupAdapter会将它inflate出来作为itemView。
                // 所以holder.itemView就是我们自定义布局的根View。
                
                holder.itemView.setBackgroundResource(bgRes)
                
                // 还需要处理Padding，因为不同背景可能有不同的padding需求（尤其是Single/Top/Bottom可能有圆角）
                // 这里我们统一使用了16dp左右margin在XML里，但是背景变化时，margin/padding可能需要调整吗？
                // 在XML里我们设置了 marginStart/End = 16dp, marginTop/Bottom 动态调整
                // 
                // 修正：XML里写死了margin和background。
                // 如果我们在这里动态设置背景，那么XML里的background应该去掉，或者设为透明。
                // 并且XML里的margin top/bottom 也需要根据位置动态调整？
                // 
                // 观察图：卡片之间有间距，卡片内部Item无间距。
                // 
                // 方案：
                // 动态修改LayoutParam的Margin。
                
                val params = holder.itemView.layoutParams as? ViewGroup.MarginLayoutParams
                if (params != null) {
                    val density = holder.itemView.context.resources.displayMetrics.density
                    val marginH = (16 * density).toInt()
                    val marginV_Small = 0
                    val marginV_Big = (8 * density).toInt() // 卡片间距
                    
                    params.marginStart = marginH
                    params.marginEnd = marginH
                    
                    // Top Margin
                    if (isPrevCategoryOrNull) {
                        params.topMargin = marginV_Big
                    } else {
                        params.topMargin = marginV_Small
                    }
                    
                    // Bottom Margin
                    if (isNextCategoryOrNull) {
                        params.bottomMargin = marginV_Big
                    } else {
                        params.bottomMargin = marginV_Small
                    }
                    
                    holder.itemView.layoutParams = params
                }
            }
        }
    }

    @CallSuper
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): android.view.View {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        setDivider(null)
        return view.apply {
            listView.applyNavBarInsetsBottomPadding()
            listView.isVerticalScrollBarEnabled = false
        }
    }

    @Suppress("DEPRECATION")
    @SuppressLint("RestrictedApi")
    override fun onDisplayPreferenceDialog(preference: Preference) {

        var handled = false
        if (callbackFragment is OnPreferenceDisplayDialogCallback) {
            handled =
                (callbackFragment as OnPreferenceDisplayDialogCallback).onPreferenceDisplayDialog(
                    this,
                    preference
                )
        }

        var callbackFragment: Fragment? = this
        while (!handled && callbackFragment != null) {
            if (callbackFragment is OnPreferenceDisplayDialogCallback) {
                handled = (callbackFragment as OnPreferenceDisplayDialogCallback)
                    .onPreferenceDisplayDialog(this, preference)
            }
            callbackFragment = callbackFragment.parentFragment
        }
        if (!handled && context is OnPreferenceDisplayDialogCallback) {
            handled = (context as OnPreferenceDisplayDialogCallback)
                .onPreferenceDisplayDialog(this, preference)
        }
        if (!handled && activity is OnPreferenceDisplayDialogCallback) {
            handled = (activity as OnPreferenceDisplayDialogCallback)
                .onPreferenceDisplayDialog(this, preference)
        }

        if (handled) {
            return
        }

        if (parentFragmentManager.findFragmentByTag(javaClass.name) != null) {
            return
        }

        val f: DialogFragment = when (preference) {
            is EditTextPreference -> {
                CsEditTextPreferenceDialogFragment.newInstance(preference.getKey())
            }
            is ListPreference -> {
                CsListPreferenceDialogFragment.newInstance(preference.getKey())
            }
            else -> {
                throw IllegalArgumentException(
                    "Cannot display dialog for an unknown Preference type: ${preference.javaClass.name}"
                )
            }
        }
        f.setTargetFragment(this, 0)
        f.show(parentFragmentManager, javaClass.name)
    }
}
