package com.fabriik.support

import androidx.fragment.app.FragmentManager
import com.fabriik.support.pages.Topic
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CashSupport private constructor(
    private val pageType: PageType,
    private val topic: Topic?,
    private val fromIndex:Boolean = false) {

    data class Builder(private var pageType: PageType = PageType.INDEX) {
        private var topic: Topic? = null
        private var fromIndex:Boolean = false

        fun detail(topic: Topic, fromIndex:Boolean = false) = apply {
            this.topic = topic
            this.pageType = PageType.DETAIL
            this.fromIndex = fromIndex
        }

        fun build() = CashSupport(pageType, topic, fromIndex)
    }

    fun createDialogFragment(): BottomSheetDialogFragment = run {
        return when(pageType) {
            PageType.INDEX -> {
                IndexDialogFragment()
            }
            PageType.DETAIL -> {
                DetailDialogFragment.newInstance(topic!!, fromIndex)
            }
        }
    }

    fun show(fragmentManager: FragmentManager, tag:String = "CASH_SUPPORT") {
        createDialogFragment().show(fragmentManager, tag)
    }

    enum class PageType {
        INDEX,
        DETAIL
    }
}