package com.resq.resq_sos_mientrung_android.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.resq.resq_sos_mientrung_android.R

class MoreMenuBottomSheet : BottomSheetDialogFragment() {

    interface OnMenuItemClickListener {
        fun onRescuersClick()
        fun onNewsClick()
        fun onAIChatbotClick()
        fun onSettingsClick()
        fun onAboutClick()
    }

    private var listener: OnMenuItemClickListener? = null

    fun setOnMenuItemClickListener(listener: OnMenuItemClickListener) {
        this.listener = listener
    }

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_more_menu, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Menu: Đội cứu hộ
        view.findViewById<LinearLayout>(R.id.menuRescuers)?.setOnClickListener {
            listener?.onRescuersClick()
            dismiss()
        }

        // Menu: Tin tức thiên tai
        view.findViewById<LinearLayout>(R.id.menuNews)?.setOnClickListener {
            listener?.onNewsClick()
            dismiss()
        }

        // Menu: Chat Bot AI
        view.findViewById<LinearLayout>(R.id.menuAIChatbot)?.setOnClickListener {
            listener?.onAIChatbotClick()
            dismiss()
        }

        // Menu: Cài đặt
        view.findViewById<LinearLayout>(R.id.menuSettings)?.setOnClickListener {
            listener?.onSettingsClick()
            dismiss()
        }

        // Menu: Thông tin ứng dụng
        view.findViewById<LinearLayout>(R.id.menuAbout)?.setOnClickListener {
            listener?.onAboutClick()
            dismiss()
        }
    }

    companion object {
        const val TAG = "MoreMenuBottomSheet"

        fun newInstance(): MoreMenuBottomSheet {
            return MoreMenuBottomSheet()
        }
    }
}
