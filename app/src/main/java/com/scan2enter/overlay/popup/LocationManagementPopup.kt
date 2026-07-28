package com.scan2enter.overlay.popup

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.scan2enter.R
import com.scan2enter.api.LocationDto
import com.scan2enter.model.ProductInfo

/**
 * Popup dedicato alla visualizzazione delle ubicazioni assegnate a un articolo.
 *
 * In questa prima fase il popup:
 * - mostra le ubicazioni già presenti in ProductInfo;
 * - si apre e si chiude autonomamente;
 * - non esegue ancora chiamate GET, POST o DELETE.
 */
class LocationManagementPopup(
    context: Context
) {

    private val appContext = context.applicationContext
    private val windowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var popupView: View? = null

    /**
     * Mostra il popup per l'articolo indicato.
     * Se era già aperto, viene prima ricostruito con i dati aggiornati.
     */
    fun show(product: ProductInfo) {
        dismiss()

        val view = LayoutInflater.from(appContext)
            .inflate(R.layout.location_management_popup, null, false)

        val articleText = view.findViewById<TextView>(
            R.id.tvLocationManagementArticle
        )
        val locationsContainer = view.findViewById<LinearLayout>(
            R.id.locationsContainer
        )
        val noLocationsText = view.findViewById<TextView>(
            R.id.tvNoLocations
        )
        val addButton = view.findViewById<Button>(
            R.id.btnAddLocation
        )
        val closeButton = view.findViewById<Button>(
            R.id.btnCloseLocationManagement
        )

        articleText.text = buildArticleLabel(product)

        renderLocations(
            container = locationsContainer,
            emptyText = noLocationsText,
            locations = product.locations
        )

        // Sarà attivato nel prossimo step, con LocationPickerPopup.
        addButton.isEnabled = false

        closeButton.setOnClickListener {
            dismiss()
        }

        popupView = view
        windowManager.addView(view, createLayoutParams())
    }

    /** Chiude il popup, se visibile. */
    fun dismiss() {
        val view = popupView ?: return

        runCatching {
            windowManager.removeViewImmediate(view)
        }

        popupView = null
    }

    fun isShowing(): Boolean = popupView != null

    private fun renderLocations(
        container: LinearLayout,
        emptyText: TextView,
        locations: List<LocationDto>
    ) {
        container.removeAllViews()

        val visibleLocations = locations
            .filter { it.name.isNotBlank() }
            .distinctBy { it.id }
            .sortedBy { it.name.lowercase() }

        emptyText.visibility = if (visibleLocations.isEmpty()) {
            View.VISIBLE
        } else {
            View.GONE
        }

        visibleLocations.forEach { location ->
            container.addView(createLocationRow(location))
        }
    }

    private fun createLocationRow(location: LocationDto): View {
        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(8), dp(10))
            setBackgroundColor(Color.rgb(42, 42, 42))

            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
        }

        val locationName = TextView(appContext).apply {
            text = "📍 ${location.name}"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL

            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }

        val removePlaceholder = TextView(appContext).apply {
            text = "✕"
            setTextColor(Color.GRAY)
            textSize = 21f
            gravity = Gravity.CENTER
            isEnabled = false
            alpha = 0.45f
            contentDescription =
                "Rimozione non ancora disponibile per ${location.name}"

            layoutParams = LinearLayout.LayoutParams(
                dp(44),
                dp(44)
            )
        }

        row.addView(locationName)
        row.addView(removePlaceholder)

        return row
    }

    private fun buildArticleLabel(product: ProductInfo): String {
        val code = product.articleCode.trim()
        val description = product.description.trim()

        return when {
            code.isNotEmpty() && description.isNotEmpty() ->
                "$code · $description"

            code.isNotEmpty() -> code
            description.isNotEmpty() -> description
            else -> "Articolo"
        }
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        return WindowManager.LayoutParams(
            dp(360),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }

    private fun dp(value: Int): Int {
        return (value * appContext.resources.displayMetrics.density)
            .toInt()
    }
}