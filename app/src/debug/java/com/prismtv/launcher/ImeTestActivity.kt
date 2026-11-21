package com.prismtv.launcher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Debug only: a search field, like a streaming app's, for CI screenshots of Prism Keyboard. */
class ImeTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val density = resources.displayMetrics.density
        val field = EditText(this).apply {
            hint = "Search movies and shows"
            setTextColor(Color.WHITE)
            setHintTextColor(0x88FFFFFF.toInt())
            textSize = 22f
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setSingleLine()
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF15233A.toInt())
            setPadding((80 * density).toInt(), (60 * density).toInt(), (80 * density).toInt(), 0)
            addView(TextView(context).apply {
                text = "Search"
                textSize = 34f
                setTextColor(Color.WHITE)
            })
            addView(field, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        setContentView(root)
        field.requestFocus()
        field.postDelayed({
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, InputMethodManager.SHOW_FORCED)
        }, 400)
    }
}
