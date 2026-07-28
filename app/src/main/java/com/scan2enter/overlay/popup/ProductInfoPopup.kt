package com.scan2enter.overlay.popup

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import com.scan2enter.R
import kotlin.math.min

/**
 * Gestisce esclusivamente la creazione e la rimozione grafica
 * del popup informazioni articolo.
 *
 * La logica di aggiornamento dei dati resta temporaneamente in OverlayService
 * e verrà spostata in un passaggio successivo del refactoring.
 */
class ProductInfoPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {

    data class Bindings(
        val root: View,
        val windowParams: WindowManager.LayoutParams,
        val priceValueText: TextView,
        val articleCodeValueText: TextView,
        val barcodeValueText: TextView,
        val barcodeImageView: ImageView,
        val descriptionValueText: TextView,
        val yearValueText: TextView,
        val seasonValueText: TextView,
        val locationValueText: TextView,
        val taxablePriceValueText: TextView,
        val vatRateValueText: TextView,
        val stockValueText: TextView,
        val stockStatusContainer: LinearLayout,
        val stockStatusText: TextView,
        val reorderText: TextView,
        val minimumStockValueText: TextView,
        val reorderLotValueText: TextView,
        val popupDurationSeekBar: SeekBar,
        val popupDurationModeButton: TextView,
        val popupDurationValueText: TextView
    )

    private var bindings: Bindings? = null

    fun isShowing(): Boolean = bindings != null

    fun create(onStockClick: () -> Unit): Bindings {
        bindings?.let { return it }

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        val overlayRoot = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            alpha = 1.0f
            clipChildren = false
            clipToPadding = false
        }

        val popupView = LayoutInflater.from(context)
            .inflate(R.layout.product_info_popup, overlayRoot, false)

        val createdBindings = Bindings(
            root = overlayRoot,
            windowParams = createWindowParams(),
            priceValueText = popupView.findViewById(R.id.productPublicPriceText),
            articleCodeValueText = popupView.findViewById(R.id.productArticleCodeText),
            barcodeValueText = popupView.findViewById(R.id.productBarcodeText),
            barcodeImageView = popupView.findViewById(R.id.productBarcodeImage),
            descriptionValueText = popupView.findViewById(R.id.productDescriptionText),
            yearValueText = popupView.findViewById(R.id.productYearText),
            seasonValueText = popupView.findViewById(R.id.productSeasonText),
            locationValueText = popupView.findViewById(R.id.productLocationText),
            taxablePriceValueText = popupView.findViewById(R.id.productTaxablePriceText),
            vatRateValueText = popupView.findViewById(R.id.productVatRateText),
            stockValueText = popupView.findViewById(R.id.productStockText),
            stockStatusContainer = popupView.findViewById(R.id.productStockStatusContainer),
            stockStatusText = popupView.findViewById(R.id.productStockStatusText),
            reorderText = popupView.findViewById(R.id.productReorderText),
            minimumStockValueText = popupView.findViewById(R.id.productMinimumStockText),
            reorderLotValueText = popupView.findViewById(R.id.productReorderLotValueText),
            popupDurationSeekBar = popupView.findViewById(R.id.popupDurationSeekBar),
            popupDurationModeButton = popupView.findViewById(R.id.popupDurationModeButton),
            popupDurationValueText = popupView.findViewById(R.id.popupDurationValueText)
        )

        popupView.findViewById<TextView>(R.id.closeProductInfoButton)
            .visibility = View.GONE

        popupView.findViewById<View>(R.id.productStockCard)
            .setOnClickListener { onStockClick() }

        val horizontalMargin = (6 * density).toInt()
        val topMargin = (42 * density).toInt()
        val bottomMargin = (8 * density).toInt()
        val bottomBreathingRoom = (22 * density).toInt()
        val shadowOffset = (5 * density).toInt()

        val popupWidth = min(
            (430 * density).toInt(),
            screenWidth - horizontalMargin * 2
        )

        val popupMaxHeight = screenHeight - topMargin - bottomMargin

        popupView.setPadding(
            popupView.paddingLeft,
            popupView.paddingTop,
            popupView.paddingRight,
            popupView.paddingBottom + bottomBreathingRoom
        )

        popupView.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = 22 * density
        }
        popupView.alpha = 1.0f
        popupView.clipToOutline = true
        popupView.outlineProvider = ViewOutlineProvider.BACKGROUND
        popupView.elevation = 14 * density

        popupView.measure(
            View.MeasureSpec.makeMeasureSpec(
                popupWidth,
                View.MeasureSpec.EXACTLY
            ),
            View.MeasureSpec.makeMeasureSpec(
                popupMaxHeight - shadowOffset,
                View.MeasureSpec.AT_MOST
            )
        )

        val cardHeight = min(
            popupView.measuredHeight,
            popupMaxHeight - shadowOffset
        )

        val cardContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        val grayBase = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.BLACK)
                cornerRadius = 22 * density
            }
        }

        cardContainer.addView(
            grayBase,
            FrameLayout.LayoutParams(popupWidth, cardHeight).apply {
                gravity = Gravity.TOP or Gravity.START
                this.topMargin = shadowOffset
            }
        )

        cardContainer.addView(
            popupView,
            FrameLayout.LayoutParams(popupWidth, cardHeight).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        )

        overlayRoot.addView(
            cardContainer,
            FrameLayout.LayoutParams(
                popupWidth,
                cardHeight + shadowOffset
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                this.topMargin = topMargin
            }
        )

        bindings = createdBindings
        windowManager.addView(
            createdBindings.root,
            createdBindings.windowParams
        )

        return createdBindings
    }

    fun remove() {
        val current = bindings ?: return

        try {
            windowManager.removeView(current.root)
        } catch (_: Exception) {
        }

        bindings = null
    }

    private fun createWindowParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1.0f
        }
    }
}