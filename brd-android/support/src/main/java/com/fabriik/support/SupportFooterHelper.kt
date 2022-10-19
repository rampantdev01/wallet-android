package com.fabriik.support

import android.content.pm.PackageInfo
import android.view.View
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner


object SupportFooterHelper {
    fun populate(view:View, lifecycleOwner: LifecycleOwner) {
        val context = view.context
        val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val version = pInfo.versionName //Version Name
        val verCode = pInfo.versionCode //Version Code

        textView(view, R.id.versionNumber).text = "$version ($verCode))"

        textView(view, R.id.privacyLink).setOnClickListener {
            lifecycleOwner.launchWebsite("https://fabriik.com/privacy-policy/")
        }
    }

    private fun textView(view: View, id:Int): TextView {
        return view.findViewById(id)
    }
}