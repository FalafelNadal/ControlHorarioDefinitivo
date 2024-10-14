package net.azarquiel.controlhorario

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
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
import java.util.Date
import java.util.Locale


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
    private var email: String? = null
    private var timerJob: Job? = null

    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        val bundle = intent.extras
        email = bundle?.getString("email")
        val provider = bundle?.getString("provider")

        setup(email ?: "")
        timerWeekTextView = findViewById(R.id.timerWeekTextView)
        nameTextView = findViewById(R.id.nameTextView)
        emailTextView = findViewById(R.id.emailTextView)
        hoursDayTextView = findViewById(R.id.hoursDayTextView)
        hoursWeekTextView = findViewById(R.id.hoursWeekTextView)
        timerDayTextView = findViewById(R.id.timerDayTextView)
        stopButton = findViewById(R.id.stopButton)
        startButton = findViewById(R.id.startButton)

        setupTimeButtons()
    }

    private fun setup(email: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return

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
                    println("No se encontró el documento del usuario.")
                    nameTextView.text = "Nombre no disponible"
                }
            }
            .addOnFailureListener { e ->
                println("Error al obtener el nombre del usuario: ${e.message}")
                nameTextView.text = "Error al cargar el nombre"
            }
    }


    private fun setupTimeButtons() {
        stopButton.setOnClickListener {
            if (!isRunning && seconds>0) {
                startTimer()
                stopButton.text = "Pausa"

            } else if (isRunning && seconds>0){
                stopTimer()
                stopButton.text = "Reanudar"

            }
        }

        startButton.setOnClickListener {
            if (isRunning) {
                stopTimer()
                startButton.text = "Empezar"
                saveSessionData()
            } else {
                startTimer()
                startButton.text = "Terminar"
            }

        }
    }

    private fun startTimer() {
        isRunning = true
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (isRunning) {
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val secs = seconds % 60

                val time = String.format("%02d:%02d:%02d", hours, minutes, secs)
                timerDayTextView.text = time

                seconds++

                delay(1000L)
            }
        }
    }

    private fun stopTimer() {
        isRunning = false
        timerJob?.cancel()
    }

    private fun saveSessionData() {

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val email = FirebaseAuth.getInstance().currentUser?.email ?: return

        val sessionData = hashMapOf(
            "email" to email,
            "tiempo_dia" to timerDayTextView.text.toString(),
            "tiempo_total" to timerWeekTextView.text.toString(),
        )

        val db = FirebaseFirestore.getInstance()

        db.collection("registros_horarios")
            .document("trabajadores")
            .collection(uid)
            .document(currentDate)
            .set(sessionData)
            .addOnSuccessListener {
                println("Datos guardados correctamente con UID: $uid en la fecha $currentDate")
            }
            .addOnFailureListener { e ->
                println("Error al guardar los datos: ${e.message}")
            }
    }

}