package com.capztone.fishfy.ui.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.capztone.fishfy.databinding.ActivityVerifyNumberBinding
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.*
import com.google.firebase.auth.PhoneAuthProvider.*
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.TimeUnit

class VerifyNumberActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVerifyNumberBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var callbacks: OnVerificationStateChangedCallbacks
    private lateinit var resendToken: ForceResendingToken

    private var phoneNum: String = "+91"
    private var storedVerificationId: String? = null
    private val TAG = "VerifyNumberActivity"

    private lateinit var countDownTimer: CountDownTimer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVerifyNumberBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        auth.useAppLanguage()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }

        if (intent != null) {
            val num = intent.getStringExtra(phoneNumberKey).toString()
            phoneNum += num
            Log.d(TAG, phoneNum)
            "Authenticate $phoneNum".also { binding.textAuthenticateNum.text = it }
        } else {
            Toast.makeText(this, "Bad Gateway 😒", Toast.LENGTH_SHORT).show()
            finish()
        }

        binding.btnVerify.setOnClickListener {
            if (binding.etOtp.editText?.text.toString().isNotEmpty()) {
                binding.etOtp.clearFocus()
                verifyVerificationCode(binding.etOtp.editText?.text.toString())
            } else {
                binding.etOtp.error = "Enter OTP 🤨"
                binding.etOtp.requestFocus()
                return@setOnClickListener
            }
        }

        binding.resendotp.setOnClickListener {
            resendVerificationCode()
            startCountdown()
        }

        verificationCallbacks()
        startCountdown()

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNum)       // Phone number to verify
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
            .setActivity(this)                 // Activity (for callback binding)
            .setCallbacks(callbacks)          // OnVerificationStateChangedCallbacks
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verificationCallbacks() {
        callbacks = object : OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                Log.d(TAG, "onVerificationCompleted:$credential")
                val code = credential.smsCode
                if (code != null) {
                    verifyVerificationCode(code)
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                Log.w(TAG, "onVerificationFailed", e)

                when (e) {
                    is FirebaseAuthInvalidCredentialsException -> {
                        Toast.makeText(
                            this@VerifyNumberActivity,
                            "Invalid request", Toast.LENGTH_SHORT
                        ).show()
                        returnToEnterNumberActivity()
                    }
                    is FirebaseTooManyRequestsException -> {
                        Toast.makeText(
                            this@VerifyNumberActivity,
                            "Please try again later or Continue with Google login",
                            Toast.LENGTH_SHORT
                        ).show()
                        returnToEnterNumberActivity()
                    }
                    else -> {
                        Toast.makeText(
                            this@VerifyNumberActivity,
                            e.message.toString(),
                            Toast.LENGTH_SHORT
                        ).show()
                        returnToEnterNumberActivity()
                    }
                }
            }

            override fun onCodeSent(
                verificationId: String,
                token: ForceResendingToken
            ) {
                Log.d(TAG, "onCodeSent:$verificationId")
                storedVerificationId = verificationId
                resendToken = token

                Toast.makeText(
                    this@VerifyNumberActivity,
                    "OTP sent to $phoneNum",
                    Toast.LENGTH_SHORT
                ).show()

                super.onCodeSent(verificationId, resendToken)
            }
        }
    }

    private fun resendVerificationCode() {
        verificationCallbacks()
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNum)       // Phone number to verify
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout and unit
            .setActivity(this)                 // Activity (for callback binding)
            .setCallbacks(callbacks)          // OnVerificationStateChangedCallbacks
            .setForceResendingToken(resendToken) // Force resending with the token
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "signInWithCredential:success")
                    Toast.makeText(this, "Authorization Completed", Toast.LENGTH_SHORT).show()

                    val currentUser = auth.currentUser
                    val currentUserId = currentUser?.uid

                    if (currentUserId != null) {
                        val phoneNumberWithoutCountryCode = phoneNum.removePrefix("+91")
                        val usersRef = FirebaseDatabase.getInstance().reference.child("users")

                        usersRef.orderByChild("phoneNumber").equalTo(phoneNumberWithoutCountryCode)
                            .addListenerForSingleValueEvent(object : ValueEventListener {
                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (snapshot.exists()) {
                                        // Phone number already exists, navigate to MainActivity
                                        Log.d(TAG, "Phone number already exists, navigating to MainActivity")
                                        val intent = Intent(this@VerifyNumberActivity, MainActivity::class.java)
                                        startActivity(intent)
                                        finish()
                                    } else {
                                        // Phone number doesn't exist, save user data and navigate to LanguageActivity
                                        val userData = hashMapOf(
                                            "userId" to currentUserId,
                                            "phoneNumber" to phoneNumberWithoutCountryCode
                                        )
                                        usersRef.child(currentUserId).setValue(userData)
                                            .addOnSuccessListener {
                                                Log.d(TAG, "User data saved successfully")
                                                val intent = Intent(this@VerifyNumberActivity, LanguageActivity::class.java)
                                                startActivity(intent)
                                                finish()
                                            }
                                            .addOnFailureListener { e ->
                                                Log.w(TAG, "Error saving user data", e)
                                                Toast.makeText(
                                                    this@VerifyNumberActivity,
                                                    "Failed to save user data",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                returnToEnterNumberActivity()
                                            }
                                    }
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Log.w(TAG, "Database error: ${error.message}", error.toException())
                                    Toast.makeText(
                                        this@VerifyNumberActivity,
                                        "Failed to check user data",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    returnToEnterNumberActivity()
                                }
                            })
                    } else {
                        Log.w(TAG, "Current user ID is null")
                        returnToEnterNumberActivity()
                    }
                } else {
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    if (task.exception is FirebaseAuthInvalidCredentialsException) {
                        Toast.makeText(
                            this,
                            "The verification code entered was invalid",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(this, task.exception.toString(), Toast.LENGTH_SHORT).show()
                    }
                    returnToEnterNumberActivity()
                }
            }
    }


    private fun verifyVerificationCode(code: String) {
        val credential = PhoneAuthProvider.getCredential(storedVerificationId!!, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun returnToEnterNumberActivity() {
        val intent = Intent(applicationContext, LoginActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun startCountdown() {
        binding.resendotp.isEnabled = false
        countDownTimer = object : CountDownTimer(1 * 60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = (millisUntilFinished / 1000) / 60
                val seconds = (millisUntilFinished / 1000) % 60
                binding.resendotp.text = String.format("Resend OTP in %02d:%02d", minutes, seconds)
            }

            override fun onFinish() {
                binding.resendotp.isEnabled = true
                binding.resendotp.text = "Didn't receive OTP? Resend OTP"
            }
        }.start()
    }

    companion object {
        const val phoneNumberKey = "PHONE_NUMBER_KEY"
    }
}