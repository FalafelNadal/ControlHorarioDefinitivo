package net.azarquiel.controlhorario

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AuthActivity2 : AppCompatActivity() {
    private lateinit var signUpButton: Button
    private lateinit var emailEditText: EditText
    private lateinit var passwordEditText: EditText
    private lateinit var nameEditText: EditText
    private lateinit var surnameEditText: EditText
    private lateinit var phoneEditText: EditText
    private lateinit var dniEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth2)

        val analytics = FirebaseAnalytics.getInstance(this)
        val bundle = Bundle()
        bundle.putString("message", "Integración de Firebase completa")
        analytics.logEvent("InitScreen", bundle)

        setup()
    }
    private fun showAlert() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Error")
        builder.setMessage("Se ha producido un error")
        builder.setPositiveButton("Aceptar", null)
        val dialog: AlertDialog = builder.create()
        dialog.show()
    }
    private fun showHome(email: String, provider: ProviderType){
        val homeIntent = Intent(this, HomeActivity::class.java).apply{
            putExtra("email", email)
            putExtra("provider", provider.name)
        }
        startActivity(homeIntent)
    }private fun setup() {

        title = "Autenticación"

        signUpButton = findViewById(R.id.signUpButton)
        emailEditText = findViewById(R.id.emailEditText)
        passwordEditText = findViewById(R.id.passwordEditText)
        nameEditText = findViewById(R.id.nameEditText)
        surnameEditText = findViewById(R.id.surnameEditText)
        phoneEditText = findViewById(R.id.phoneEditText)
        dniEditText = findViewById(R.id.dniEditText)


        signUpButton.setOnClickListener {
            if (emailEditText.text.isNotEmpty() && passwordEditText.text.isNotEmpty() && nameEditText.text.isNotEmpty() && surnameEditText.text.isNotEmpty() && surnameEditText.text.isNotEmpty() && phoneEditText.text.isNotEmpty() && dniEditText.text.isNotEmpty()) {
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(
                    emailEditText.text.toString(),
                    passwordEditText.text.toString()

                    ).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val uid = task.result?.user?.uid ?: ""
                        val email = task.result?.user?.email ?: ""

                        val userData = hashMapOf(
                            "uid" to uid,
                            "email" to email,
                            "name" to nameEditText.text.toString(),
                            "surname" to surnameEditText.text.toString(),
                            "phone" to phoneEditText.text.toString(),
                            "dni" to dniEditText.text.toString()
                        )

                        val db = FirebaseFirestore.getInstance()
                        db.collection("users").document(uid)
                            .set(userData)
                            .addOnSuccessListener {
                                showHome(email, ProviderType.BASIC)
                            }
                            .addOnFailureListener { e ->
                                println("Error al guardar el UID: ${e.message}")
                                showAlert()
                            }
                    } else {
                        showAlert()
                    }
                }
            } else {
                showAlert()
            }
        }
    }


    }