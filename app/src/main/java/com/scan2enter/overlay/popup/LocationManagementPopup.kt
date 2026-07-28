package com.scan2enter.overlay.popup

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.scan2enter.api.LocationDto
import com.scan2enter.api.DeleteLocationResult
import com.scan2enter.model.ProductInfo
import kotlin.math.min

/**
 * Gestione completa delle ubicazioni articolo.
 *
 * Mostra tutte le ubicazioni disponibili, evidenzia quelle già assegnate e
 * consente di aggiungere/rimuovere l'associazione con un singolo tocco.
 * La persistenza viene eseguita dal chiamante tramite ProductRepository.
 */
class LocationManagementPopup(
    private val context: Context,
    private val windowManager: WindowManager
) {

    private var overlayRoot: View? = null
    private var closeCallback: (() -> Unit)? = null
    private var locationsContainer: LinearLayout? = null
    private var searchEditText: EditText? = null

    private var availableLocations: List<LocationDto> = emptyList()
    private var assignedLocations: List<LocationDto> = emptyList()
    private var operationInProgress = false

    private var createCallback: ((String, (Result<Pair<List<LocationDto>, List<LocationDto>>>) -> Unit) -> Unit)? = null
    private var deleteCallback: ((LocationDto, (Result<DeleteLocationResult>) -> Unit) -> Unit)? = null
    private var renameCallback: ((LocationDto, String, (Result<LocationDto>) -> Unit) -> Unit)? = null
    private var duplicateNextCallback: ((LocationDto, (Result<LocationDto>) -> Unit) -> Unit)? = null

    private var toggleCallback: ((
        location: LocationDto,
        currentlyAssigned: Boolean,
        complete: (Result<List<LocationDto>>) -> Unit
    ) -> Unit)? = null

    fun isShowing(): Boolean = overlayRoot != null

    fun show(
        product: ProductInfo,
        availableLocations: List<LocationDto>,
        onToggle: (
            location: LocationDto,
            currentlyAssigned: Boolean,
            complete: (Result<List<LocationDto>>) -> Unit
        ) -> Unit,
        onCreate: (String, (Result<Pair<List<LocationDto>, List<LocationDto>>>) -> Unit) -> Unit,
        onDelete: (LocationDto, (Result<DeleteLocationResult>) -> Unit) -> Unit,
        onRename: (LocationDto, String, (Result<LocationDto>) -> Unit) -> Unit,
        onDuplicateNext: (LocationDto, (Result<LocationDto>) -> Unit) -> Unit,
        onClose: () -> Unit
    ) {
        if (overlayRoot != null) return

        closeCallback = onClose
        toggleCallback = onToggle
        createCallback = onCreate
        deleteCallback = onDelete
        renameCallback = onRename
        duplicateNextCallback = onDuplicateNext
        this.availableLocations = availableLocations
            .filter { it.id > 0 && it.name.isNotBlank() }
            .distinctBy(LocationDto::id)
            .sortedBy { it.name.lowercase() }
        assignedLocations = product.locations
            .filter { it.id > 0 && it.name.isNotBlank() }
            .distinctBy(LocationDto::id)

        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            isFocusable = true
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                (20 * density).toInt(),
                (18 * density).toInt(),
                (20 * density).toInt(),
                (18 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.WHITE)
                cornerRadius = 22 * density
            }
            elevation = 16 * density
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        header.addView(
            TextView(context).apply {
                text = "Ubicazioni articolo"
                textSize = 23f
                setTextColor(Color.BLACK)
                setTypeface(typeface, Typeface.BOLD)
            },
            LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        )

        header.addView(
            TextView(context).apply {
                text = "✕"
                textSize = 28f
                gravity = Gravity.CENTER
                setTextColor(Color.BLACK)
                isClickable = true
                isFocusable = true
                setPadding(
                    (12 * density).toInt(),
                    (4 * density).toInt(),
                    (4 * density).toInt(),
                    (4 * density).toInt()
                )
                setOnClickListener { remove() }
            }
        )

        card.addView(header)

        card.addView(
            TextView(context).apply {
                text = listOf(product.articleCode, product.description)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .joinToString(" · ")
                    .ifEmpty { "Articolo" }
                textSize = 14f
                setTextColor(Color.DKGRAY)
                setPadding(0, (4 * density).toInt(), 0, (10 * density).toInt())
            }
        )

        val search = EditText(context).apply {
            hint = "Cerca ubicazione"
            textSize = 16f
            setSingleLine(true)
            setPadding(
                (12 * density).toInt(),
                (8 * density).toInt(),
                (12 * density).toInt(),
                (8 * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.rgb(245, 245, 245))
                cornerRadius = 12 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), Color.LTGRAY)
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    renderLocations()
                }
                override fun afterTextChanged(s: android.text.Editable?) = Unit
            })
        }
        searchEditText = search

        card.addView(
            search,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            ).apply {
                bottomMargin = (10 * density).toInt()
            }
        )

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        locationsContainer = container

        card.addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(container)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        card.addView(
            Button(context).apply {
                text = "➕  NUOVA UBICAZIONE"
                textSize = 15f
                setOnClickListener { showCreateLocationDialog() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            ).apply {
                topMargin = (8 * density).toInt()
            }
        )

        card.addView(
            TextView(context).apply {
                text = "Tocca la riga per assegnare/rimuovere; usa 📄+, ✏️ o 🗑"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.DKGRAY)
                setPadding(0, (8 * density).toInt(), 0, (6 * density).toInt())
            }
        )

        card.addView(
            Button(context).apply {
                text = "CHIUDI"
                textSize = 15f
                setOnClickListener { remove() }
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (48 * density).toInt()
            )
        )

        val horizontalMargin = (20 * density).toInt()
        val verticalMargin = (40 * density).toInt()
        val cardWidth = min(
            (410 * density).toInt(),
            screenWidth - horizontalMargin * 2
        )
        val cardHeight = min(
            (680 * density).toInt(),
            screenHeight - verticalMargin * 2
        )

        root.addView(
            card,
            FrameLayout.LayoutParams(cardWidth, cardHeight).apply {
                gravity = Gravity.CENTER
            }
        )

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.OPAQUE
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            alpha = 1.0f
        }

        overlayRoot = root
        windowManager.addView(root, params)
        renderLocations()
    }

    fun remove() {
        val popup = overlayRoot ?: return

        try {
            windowManager.removeView(popup)
        } catch (_: Exception) {
        }

        overlayRoot = null
        locationsContainer = null
        searchEditText = null
        availableLocations = emptyList()
        assignedLocations = emptyList()
        operationInProgress = false
        toggleCallback = null
        createCallback = null
        deleteCallback = null
        renameCallback = null
        duplicateNextCallback = null

        val callback = closeCallback
        closeCallback = null
        callback?.invoke()
    }

    private fun renderLocations() {
        val container = locationsContainer ?: return
        val density = context.resources.displayMetrics.density
        val query = searchEditText?.text?.toString()?.trim()?.lowercase().orEmpty()

        container.removeAllViews()

        val filtered = availableLocations.filter {
            query.isEmpty() || it.name.lowercase().contains(query)
        }

        if (filtered.isEmpty()) {
            container.addView(
                TextView(context).apply {
                    text = if (availableLocations.isEmpty()) {
                        "Nessuna ubicazione disponibile"
                    } else {
                        "Nessuna ubicazione trovata"
                    }
                    textSize = 17f
                    gravity = Gravity.CENTER
                    setTextColor(Color.DKGRAY)
                    setPadding(
                        (8 * density).toInt(),
                        (36 * density).toInt(),
                        (8 * density).toInt(),
                        (36 * density).toInt()
                    )
                }
            )
            return
        }

        filtered.forEach { location ->
            val assigned = assignedLocations.any { it.id == location.id }
            container.addView(
                createLocationRow(location, assigned, density),
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = (8 * density).toInt()
                }
            )
        }
    }

    private fun createLocationRow(
        location: LocationDto,
        assigned: Boolean,
        density: Float
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * density).toInt(), 0, (4 * density).toInt(), 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(if (assigned) Color.rgb(232, 245, 233) else Color.rgb(245, 245, 245))
                cornerRadius = 12 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), if (assigned) Color.rgb(129, 199, 132) else Color.LTGRAY)
            }
        }

        val nameView = TextView(context).apply {
            text = if (assigned) "✓  ${location.name.trim()}" else "○  ${location.name.trim()}"
            textSize = 18f
            setTextColor(if (assigned) Color.rgb(27, 94, 32) else Color.BLACK)
            setTypeface(typeface, if (assigned) Typeface.BOLD else Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isEnabled = !operationInProgress
            setPadding(2, (12 * density).toInt(), 2, (12 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (operationInProgress) return@setOnClickListener
                operationInProgress = true
                renderLocations()
                toggleCallback?.invoke(location, assigned) { result ->
                    operationInProgress = false
                    result.onSuccess { refreshed ->
                        assignedLocations = refreshed.filter { it.id > 0 && it.name.isNotBlank() }.distinctBy(LocationDto::id)
                        Toast.makeText(context, if (assigned) "Ubicazione rimossa" else "Ubicazione assegnata", Toast.LENGTH_SHORT).show()
                    }.onFailure { error ->
                        Toast.makeText(context, "Errore ubicazione: ${error.message ?: "errore sconosciuto"}", Toast.LENGTH_LONG).show()
                    }
                    renderLocations()
                }
            }
        }

        val duplicateView = TextView(context).apply {
            text = "📄+"
            textSize = 17f
            gravity = Gravity.CENTER
            isClickable = true
            isEnabled = !operationInProgress
            contentDescription = "Crea ubicazione successiva da ${location.name}"
            layoutParams = LinearLayout.LayoutParams((52 * density).toInt(), (48 * density).toInt())
            setOnClickListener { duplicateNextLocation(location) }
        }

        val renameView = TextView(context).apply {
            text = "✏️"
            textSize = 20f
            gravity = Gravity.CENTER
            isClickable = true
            isEnabled = !operationInProgress
            contentDescription = "Rinomina ${location.name}"
            layoutParams = LinearLayout.LayoutParams((46 * density).toInt(), (48 * density).toInt())
            setOnClickListener { showRenameLocationDialog(location) }
        }

        val deleteView = TextView(context).apply {
            text = "🗑"
            textSize = 21f
            gravity = Gravity.CENTER
            isClickable = true
            isEnabled = !operationInProgress
            contentDescription = "Elimina ${location.name}"
            layoutParams = LinearLayout.LayoutParams((46 * density).toInt(), (48 * density).toInt())
            setOnClickListener { showDeleteLocationDialog(location) }
        }

        row.addView(nameView)
        row.addView(duplicateView)
        row.addView(renameView)
        row.addView(deleteView)
        return row
    }

    private fun showCreateLocationDialog() {
        val initialName = searchEditText
            ?.text
            ?.toString()
            ?.trim()
            ?.uppercase()
            .orEmpty()

        val input = EditText(context).apply {
            hint = "Es. LAMPADINE-10"
            setSingleLine(true)
            setSelectAllOnFocus(true)
            setText(initialName)
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Nuova ubicazione")
            .setMessage("Inserisci il nome dell'ubicazione")
            .setView(input)
            .setNegativeButton("ANNULLA", null)
            .setPositiveButton("CREA", null)
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = input.text.toString().trim().uppercase()
                if (name.isBlank()) {
                    input.error = "Nome obbligatorio"
                    return@setOnClickListener
                }
                operationInProgress = true
                createCallback?.invoke(name) { result ->
                    operationInProgress = false
                    result.onSuccess { (available, assigned) ->
                        availableLocations = available.sortedBy { it.name.lowercase() }
                        assignedLocations = assigned
                        Toast.makeText(context, "Ubicazione creata e assegnata", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }.onFailure { error ->
                        Toast.makeText(context, "Errore creazione: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                    renderLocations()
                }
            }
            input.requestFocus()
        }
        dialog.show()
    }


    private fun showRenameLocationDialog(location: LocationDto) {
        val input = EditText(context).apply {
            setSingleLine(true)
            setSelectAllOnFocus(true)
            setText(location.name.trim().uppercase())
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle("Rinomina ubicazione")
            .setMessage("Modifica il nome senza cambiare le assegnazioni")
            .setView(input)
            .setNegativeButton("ANNULLA", null)
            .setPositiveButton("SALVA", null)
            .create()

        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val newName = input.text.toString().trim().uppercase()

                if (newName.isBlank()) {
                    input.error = "Nome obbligatorio"
                    return@setOnClickListener
                }

                if (newName == location.name.trim().uppercase()) {
                    dialog.dismiss()
                    return@setOnClickListener
                }

                operationInProgress = true
                renderLocations()

                renameCallback?.invoke(location, newName) { result ->
                    operationInProgress = false

                    result.onSuccess { renamed ->
                        availableLocations = availableLocations
                            .map { if (it.id == renamed.id) renamed else it }
                            .sortedBy { it.name.lowercase() }

                        assignedLocations = assignedLocations
                            .map { if (it.id == renamed.id) renamed else it }

                        Toast.makeText(
                            context,
                            "Ubicazione rinominata",
                            Toast.LENGTH_SHORT
                        ).show()

                        dialog.dismiss()
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            "Errore rinomina: ${error.message ?: "errore sconosciuto"}",
                            Toast.LENGTH_LONG
                        ).show()
                    }

                    renderLocations()
                }
            }

            input.requestFocus()
            input.setSelection(input.text.length)
        }

        dialog.show()
    }

    private fun duplicateNextLocation(location: LocationDto) {
        if (operationInProgress) return

        operationInProgress = true
        renderLocations()

        duplicateNextCallback?.invoke(location) { result ->
            operationInProgress = false

            result.onSuccess { created ->
                availableLocations = (availableLocations + created)
                    .distinctBy(LocationDto::id)
                    .sortedBy { it.name.lowercase() }

                Toast.makeText(
                    context,
                    "Creata ${created.name}",
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "Errore creazione successiva: ${error.message ?: "errore sconosciuto"}",
                    Toast.LENGTH_LONG
                ).show()
            }

            renderLocations()
        }
    }

    private fun showDeleteLocationDialog(location: LocationDto) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Eliminare ubicazione?")
            .setMessage("${location.name}\n\nLa cancellazione è consentita solo se non è assegnata ad alcun articolo.")
            .setNegativeButton("ANNULLA", null)
            .setPositiveButton("ELIMINA") { _, _ ->
                operationInProgress = true
                deleteCallback?.invoke(location) { result ->
                    operationInProgress = false
                    result.onSuccess { outcome ->
                        if (outcome.deleted) {
                            availableLocations = availableLocations.filterNot { it.id == location.id }
                            assignedLocations = assignedLocations.filterNot { it.id == location.id }
                            Toast.makeText(context, "Ubicazione eliminata", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, outcome.message.ifBlank { "Ubicazione ancora utilizzata" }, Toast.LENGTH_LONG).show()
                        }
                    }.onFailure { error ->
                        Toast.makeText(context, "Errore eliminazione: ${error.message}", Toast.LENGTH_LONG).show()
                    }
                    renderLocations()
                }
            }
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

}
