package net.azarquiel.controlhorario

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import java.text.ParseException
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import android.content.SharedPreferences
import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.*

enum class ProviderType {
    BASIC
}

class HomeActivity : AppCompatActivity() {
    private var seconds = 0
    private var isRunning = false

    private lateinit var stopButton: Button
    private lateinit var startButton: Button
    private lateinit var nameTextView: TextView
    private lateinit var emailTextView: TextView
    private lateinit var hoursWeekTextView: TextView
    private lateinit var hoursDayTextView: TextView
    private lateinit var timerDayTextView: TextView
    private lateinit var timerWeekTextView: TextView
    private lateinit var percentageDayTextView: TextView
    private lateinit var percentageWeekTextView: TextView
    private val calendar = Calendar.getInstance()
    private val currentDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
    private var totalSecondsWeek = 0
    private var currentDaySeconds = 0
    private var isPaused = false
    private var pauseStartTime = 0L
    private var totalPauseDuration = 0L
    private var pauseStartDateTime: String = ""
    private var pauseEndDateTime: String = ""
    private var email: String? = null
    private var timerJob: Job? = null
    private var hasResetHours = false
    private lateinit var sharedPreferences: SharedPreferences

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bundle = intent.extras
        email = bundle?.getString("email")

        setup(email ?: "")
        timerWeekTextView = findViewById(R.id.timerWeekTextView)
        nameTextView = findViewById(R.id.nameTextView)
        emailTextView = findViewById(R.id.emailTextView)
        hoursDayTextView = findViewById(R.id.hoursDayTextView)
        hoursWeekTextView = findViewById(R.id.hoursWeekTextView)
        timerDayTextView = findViewById(R.id.timerDayTextView)
        percentageDayTextView = findViewById(R.id.percentageDayTextView)
        percentageWeekTextView = findViewById(R.id.percentageWeekTextView)
        stopButton = findViewById(R.id.stopButton)
        startButton = findViewById(R.id.startButton)

        setupTimeButtons()
        createDynamicLayouts()
        hoursDay()
        hoursWeek()
    }

    private fun createDynamicLayouts() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = Date()
        val calendar = Calendar.getInstance()

        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        val offsetToMonday = if (dayOfWeek == Calendar.SUNDAY) -6 else Calendar.MONDAY - dayOfWeek
        calendar.add(Calendar.DAY_OF_YEAR, offsetToMonday)

        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val mainLayout = findViewById<LinearLayout>(R.id.mainLayout)

        for (i in 1..7) {
            val date = dateFormat.format(calendar.time)

            db.collection("registros_horarios")
                    .document("trabajadores")
                    .collection(uid)
                    .document(date)
                    .get()
                    .addOnSuccessListener { document ->
                        val inicio = document.getString("inicio") ?: "No disponible"
                        val fin = document.getString("fin") ?: "No disponible"
                        val pausas = document.get("pausas") as? List<Map<String, String>> ?: emptyList()
                        val total = document.getString("tiempo_dia") ?: "No disponible"

                        val pausaStr = if (pausas.isNotEmpty()) {
                            pausas.joinToString { it["inicio"] ?: "Sin datos" }
                        } else {
                            "Sin pausas"
                        }

                        val linearLayout = LinearLayout(this).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                            orientation = LinearLayout.HORIZONTAL
                        }

                        val textViewFecha = createTextView(date)
                        val textViewInicio = createTextView(inicio)
                        val textViewPausa = createTextView(pausaStr)
                        val textViewFin = createTextView(fin)
                        val textViewTotal = createTextView(total)

                        linearLayout.addView(textViewFecha)
                        linearLayout.addView(textViewInicio)
                        linearLayout.addView(textViewPausa)
                        linearLayout.addView(textViewFin)
                        linearLayout.addView(textViewTotal)

                        mainLayout.addView(linearLayout)

                        val divider = View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                1
                            ).apply {
                                setMargins(0, 10, 0, 10)
                            }
                            setBackgroundResource(android.R.color.darker_gray)
                        }
                        mainLayout.addView(divider)
                    }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }

    private fun createTextView(text: String): TextView {
        return TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(0, 10, 0, 10)
            }
            this.text = text
            textAlignment = TextView.TEXT_ALIGNMENT_CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
    }
    private fun setup(email: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        sharedPreferences = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
        totalSecondsWeek = sharedPreferences.getInt("totalSecondsWeek", 0)

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val name = document.getString("name") ?: "Nombre no disponible"

                    title = "Inicio"
                    emailTextView = findViewById(R.id.emailTextView)
                    nameTextView = findViewById(R.id.nameTextView)
                    nameTextView.text = name
                    emailTextView.text = email
                } else {
                    nameTextView.text = "Nombre no disponible"
                }
            }
            .addOnFailureListener { e ->
                nameTextView.text = "Error al cargar el nombre"
            }
    }


    private fun setupTimeButtons() {
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        var inicio: String? = null
        var fin: String? = null

        fun saveSessionData() {
            val email = FirebaseAuth.getInstance().currentUser?.email ?: return
            val year = currentDate.substring(0, 4)
            val yearMonth = currentDate.substring(0, 7)

            val sessionData = hashMapOf(
                "email" to email,
                "año" to year,
                "añomes" to yearMonth,
                "fecha" to currentDate,
                "inicio" to inicio,
                "fin" to fin,
                "tiempo_dia" to timerDayTextView.text.toString(),
                "tiempo_total" to timerWeekTextView.text.toString()
            )
            db.collection("registros_horarios")
                .document("trabajadores")
                .collection(uid)
                .document(currentDate)
                .set(sessionData)
        }
        stopButton.setOnClickListener {
            if (isRunning) {
                pauseTimer()
                startButton.isEnabled = false

                val options = arrayOf("Pausa por turno partido", "Pausa personal", "Pausa justificada")
                var selectedOption = -1

                val dialogBuilder = AlertDialog.Builder(this)
                dialogBuilder.setTitle("Selecciona una opción")
                    .setSingleChoiceItems(options, selectedOption) { dialog, which ->
                        selectedOption = which
                    }
                    .setPositiveButton("Aceptar") { dialog, _ ->
                        if (selectedOption != -1) {
                            Toast.makeText(
                                this,
                                "Seleccionaste: ${options[selectedOption]}",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                "No seleccionaste ninguna opción.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }

                        val sessionData = hashMapOf(
                            "pausa-inicio" to pauseStartDateTime,
                            "pausa-fin" to pauseEndDateTime,
                            "tipo" to options[selectedOption]
                        )

                        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@setPositiveButton

                        db.collection("registros_horarios")
                            .document("trabajadores")
                            .collection(uid)
                            .document(currentDate)
                            .update("pausas", FieldValue.arrayUnion(sessionData))
                            .addOnSuccessListener {
                                Toast.makeText(this, "Pausa registrada correctamente", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, "Error al registrar la pausa: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .setNegativeButton("Cancelar") { dialog, _ ->
                        dialog.dismiss()
                    }

                val alertDialog = dialogBuilder.create()
                alertDialog.show()
                stopButton.text = "Reanudar"
            } else {
                pauseTimer()
                startButton.isEnabled = true
                stopButton.text = "Pausa"
            }
        }
        startButton.setOnClickListener {

            if (isRunning) {
                fin = timeFormat.format(Date())
                stopTimer()
                startButton.text = "Finalizado"
                stopButton.isEnabled = false
                startButton.isEnabled = false
                val sessionData = hashMapOf<String, Any?>(
                    "fin" to fin
                )
                db.collection("registros_horarios")
                    .document("trabajadores")
                    .collection(uid)
                    .document(currentDate)
                    .set(sessionData, SetOptions.merge())
                    .addOnSuccessListener {
                        saveSessionData()
                    }
            } else {
                inicio = timeFormat.format(Date())
                startTimer()
                startButton.text = "Terminar"

                val sessionDataInicio = hashMapOf<String, Any?>(
                    "inicio" to inicio
                )
                db.collection("registros_horarios")
                    .document("trabajadores")
                    .collection(uid)
                    .document(currentDate)
                    .set(sessionDataInicio, SetOptions.merge())

            }
        }
    }

    private fun startTimer() {
        if (currentDayOfWeek == 1 && !hasResetHours) {
            totalSecondsWeek = 0
            hasResetHours = true
        }
        val totalHoursDay = hoursDayTextView.text.toString().toFloatOrNull() ?: 0f
        val totalSecondsDay = (totalHoursDay * 3600).toInt()
        val totalHoursWeek = hoursWeekTextView.text.toString().toFloatOrNull() ?: 0f
        val totalSecondsWeekAll = (totalHoursWeek * 3600).toInt()
        isRunning = true
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (isRunning) {
                currentDaySeconds++
                totalSecondsWeek++

                val hoursToday = currentDaySeconds / 3600
                val minutesToday = (currentDaySeconds % 3600) / 60
                val secsToday = currentDaySeconds % 60

                val hoursWeek = totalSecondsWeek / 3600
                val minutesWeek = (totalSecondsWeek % 3600) / 60
                val secsWeek = totalSecondsWeek % 60

                val timeToday = String.format("%02d:%02d:%02d", hoursToday, minutesToday, secsToday)
                val timeWeek = String.format("%02d:%02d:%02d", hoursWeek, minutesWeek, secsWeek)
                
                timerDayTextView.text = timeToday
                timerWeekTextView.text = timeWeek

                if (totalSecondsDay > 0) {
                    val percentageDay = (currentDaySeconds.toFloat() / totalSecondsDay) * 100
                    percentageDayTextView.text = String.format("%.2f%%", percentageDay)
                } else {
                    percentageDayTextView.text = "N/A"
                }
                if (totalSecondsWeekAll > 0) {
                    val percentageWeek = (totalSecondsWeek.toFloat() / totalSecondsWeekAll) * 100
                    percentageWeekTextView.text = String.format("%.2f%%", percentageWeek)
                } else {
                    percentageWeekTextView.text = "N/A"
                }

                delay(1000L)
            }
        }
    }
    private fun stopTimer() {
        isRunning = false
        saveTimeToPreferences()
    }
    private fun pauseTimer() {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        if (!isPaused) {
            pauseStartTime = System.currentTimeMillis()
            pauseStartDateTime = dateFormat.format(Date(pauseStartTime))
            isPaused = true

            isRunning = false
        } else {
            val pauseEndTime = System.currentTimeMillis()
            pauseEndDateTime = dateFormat.format(Date(pauseEndTime))

            val pauseDuration = pauseEndTime - pauseStartTime

            if (pauseDuration < 2 * 60 * 60 * 1000) {
                totalPauseDuration += pauseDuration
            } else {
                totalPauseDuration = 0
            }
            isRunning = true
            startTimer()
            isPaused = false

            println("Pausa iniciada a: $pauseStartDateTime, Pausa terminada a: $pauseEndDateTime")
        }
    }

    private fun saveTimeToPreferences() {
        val editor = sharedPreferences.edit()
        editor.putInt("totalSecondsWeek", totalSecondsWeek)
        editor.apply()
    }
    private fun hoursDay() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        db.collection("horarios").document("1").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val diasList = document.get("dias") as? List<Map<String, String>> ?: emptyList()

                    val diaActual = diasList.getOrNull(currentDayOfWeek)
                    val inicio: String
                    val fin: String
                    if (diaActual != null) {
                        inicio = diaActual["inicio"] ?: "Hora de inicio no disponible"
                        fin = diaActual["fin"] ?: "Hora de fin no disponible"
                    } else {
                        inicio = "Hora de inicio no disponible"
                        fin = "Hora de fin no disponible"
                    }
                    if (inicio != "Hora de inicio no disponible" && fin != "Hora de fin no disponible") {
                        try {
                            val inicioDate = timeFormat.parse(inicio)
                            val finDate = timeFormat.parse(fin)

                            val differenceMillis = finDate.time - inicioDate.time

                            val differenceHours = differenceMillis / (1000 * 60 * 60).toFloat()
                            hoursDayTextView.text = String.format("%.2f", differenceHours)

                        } catch (e: ParseException) {
                            println("Error al parsear las horas: ${e.message}")
                        }
                    } else {
                        println("No se puede calcular la diferencia de horas. Inicio o fin no disponibles.")
                    }
                }
            }
    }
    private fun hoursWeek() {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        var totalHours = 0.0

        if (currentDayOfWeek == 0 && !hasResetHours) {
            totalHours = 0.0
            hasResetHours = true
        }


        db.collection("horarios").document("1").get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val diasList = document.get("dias") as? List<Map<String, String>> ?: emptyList()

                    for (dia in diasList) {
                        val inicio = dia["inicio"] ?: "Hora de inicio no disponible"
                        val fin = dia["fin"] ?: "Hora de fin no disponible"

                        if (inicio != "Hora de inicio no disponible" && fin != "Hora de fin no disponible") {
                            try {
                                val inicioDate = timeFormat.parse(inicio)
                                val finDate = timeFormat.parse(fin)

                                val differenceMillis = finDate.time - inicioDate.time

                                val differenceHours = differenceMillis / (1000 * 60 * 60).toFloat()
                                totalHours += differenceHours

                            } catch (e: ParseException) {
                                println("Error al parsear las horas: ${e.message}")
                            }
                        }
                    }
                    hoursWeekTextView.text = String.format("%.2f", totalHours)
                }
            }
            .addOnFailureListener { e ->
                hoursWeekTextView.text = "Error al calcular horas"
            }
    }
}
