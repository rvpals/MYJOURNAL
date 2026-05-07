package com.journal.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.util.Base64
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.journal.app.ThemeManager.C

object DashboardCardComponent {

    data class CardConfig(
        val name: String,
        val count: Int,
        val iconData: String?,
        val fallbackEmoji: String,
        val columnIndex: Int,
        val totalColumns: Int,
        val onClick: (() -> Unit)? = null
    )

    fun createCard(context: Context, config: CardConfig): FrameLayout {
        val wrapper = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = if (config.columnIndex < config.totalColumns - 1) dp(context, 6) else 0
            }
        }

        // 3D shadow layer (offset below and right)
        val shadowDrawable = GradientDrawable().apply {
            cornerRadius = dp(context, 12).toFloat()
            setColor(Color.parseColor("#40808080"))
        }
        // Main card face with gradient highlight for 3D effect
        val cardFace = GradientDrawable().apply {
            cornerRadius = dp(context, 12).toFloat()
            setColor(ThemeManager.color(C.CARD_BG))
            setStroke(dp(context, 1), ThemeManager.color(C.CARD_BORDER))
        }
        // Top highlight edge for 3D raised look
        val highlightEdge = GradientDrawable().apply {
            cornerRadius = dp(context, 12).toFloat()
            setColor(Color.TRANSPARENT)
            setStroke(dp(context, 1), Color.parseColor("#20FFFFFF"))
        }

        val layered = LayerDrawable(arrayOf(shadowDrawable, cardFace, highlightEdge))
        layered.setLayerInset(0, dp(context, 2), dp(context, 2), 0, 0) // shadow offset
        layered.setLayerInset(1, 0, 0, dp(context, 2), dp(context, 2)) // card inset from shadow
        layered.setLayerInset(2, 0, 0, dp(context, 2), dp(context, 2)) // highlight matches card

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(context, 8), dp(context, 10), dp(context, 8), dp(context, 10))
            background = layered
            elevation = dp(context, 4).toFloat()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = false
            if (config.onClick != null) {
                isClickable = true
                isFocusable = true
                setOnClickListener { config.onClick.invoke() }
            }
        }

        // Icon
        val iconStr = config.iconData
        if (!iconStr.isNullOrEmpty() && iconStr != "null") {
            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)).apply { bottomMargin = dp(context, 6) }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
            try {
                val dataUrl = iconStr.removeSurrounding("\"")
                val b64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
                if (b64.isNotEmpty()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) iv.setImageBitmap(bmp)
                }
            } catch (_: Exception) {}
            card.addView(iv)
        } else {
            card.addView(TextView(context).apply {
                text = config.fallbackEmoji
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)).apply { bottomMargin = dp(context, 6) }
            })
        }

        // Name
        card.addView(TextView(context).apply {
            text = config.name
            setTextColor(ThemeManager.color(C.TEXT))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })

        // Count badge
        card.addView(TextView(context).apply {
            text = "${config.count}"
            setTextColor(ThemeManager.color(C.CARD_BG))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(context, 8), dp(context, 2), dp(context, 8), dp(context, 2))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(context, 4) }
            background = GradientDrawable().apply {
                cornerRadius = dp(context, 10).toFloat()
                setColor(ThemeManager.color(C.ACCENT))
            }
        })

        wrapper.addView(card)
        return wrapper
    }

    fun createEmptyCell(context: Context, columnIndex: Int, totalColumns: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                marginEnd = if (columnIndex < totalColumns - 1) dp(context, 6) else 0
            }
        }
    }

    private fun dp(context: Context, v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
