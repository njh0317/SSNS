package com.example.makeyourcs.ui.user.management

import android.graphics.drawable.Drawable
import android.widget.ImageView
import androidx.databinding.BindingAdapter

@BindingAdapter("imageDrawable")
fun bindImageFromRes(view: ImageView, drawable: Int){
//    view.setImageDrawable(drawable)
    view.setImageResource(drawable)
}
