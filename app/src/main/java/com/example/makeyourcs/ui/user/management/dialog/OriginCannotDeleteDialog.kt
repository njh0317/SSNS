package com.example.makeyourcs.ui.user.management.dialog

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import android.widget.Button
import com.example.makeyourcs.R


class OriginCannotDeleteDialog(context: Context) {
    private val dialog = Dialog(context)

    fun WarningConfirm(){
        dialog.setContentView(R.layout.origin_cannotdelete_dialog)
        // Dialog 배경 투명하게
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        // Dialog 크기 설정
        dialog.window!!.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setCancelable(true)
        dialog.show()

        val btn = dialog.findViewById<Button>(R.id.warning_confirm)
        btn.setOnClickListener {
            dialog.dismiss()
        }
    }
}