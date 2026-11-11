package com.example.seniorlauncher

import android.Manifest
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.BatteryManager
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val REQUEST_PERMISSIONS = 100
    private val REQUEST_CALL = 101
    private val PREFS_NAME = "SeniorLauncherPrefs"
    private val KEY_PIN = "admin_pin"
    private val DEFAULT_PIN = "1234"

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1

            if (level >= 0 && scale > 0) {
                val batteryPct = (level * 100) / scale
                val tvBattery = findViewById<TextView>(R.id.tvBattery)
                tvBattery.text = "电量: $batteryPct%"
                if (batteryPct <= 20) {
                    tvBattery.setTextColor(Color.RED)
                } else {
                    tvBattery.setTextColor(Color.parseColor("#2E7D32"))
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupBottomDock()
        setupProtectedSettings()
        
        // Check permissions on launch; if granted, load photos immediately
        if (checkPermissions()) {
            setupContactCards()
        }
    }

    override fun onResume() {
        super.onResume()
        setupClock()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(batteryReceiver)
    }

    // Refreshes the UI instantly the moment the user clicks "Allow" on the permission popup
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            setupContactCards()
        }
    }

    private fun setupClock() {
        val tvClock = findViewById<TextView>(R.id.tvClock)
        val tvDateAndLunar = findViewById<TextView>(R.id.tvDateAndLunar)

        val timeFormatter = SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE)
        val dateFormatter = SimpleDateFormat("MM月dd日 EEE", Locale.SIMPLIFIED_CHINESE)

        val now = Date()
        tvClock.text = timeFormatter.format(now)
        val gregorianDate = dateFormatter.format(now)
        val lunarDate = LunarCalendarUtil.getTodayLunarDate()
        tvDateAndLunar.text = "$gregorianDate ($lunarDate)"
    }

    private fun setupContactCards() {
        // REPLACE THESE STRINGS with your actual family names and phone numbers
        bindContactCard(R.id.cardContact1, R.id.imgContact1, "👩 大女儿", "13800138000")
        bindContactCard(R.id.cardContact2, R.id.imgContact2, "👨 小儿子", "13900139000")
        bindContactCard(R.id.cardContact3, R.id.imgContact3, "👵 老伴", "13600136000")
        bindContactCard(R.id.cardContact4, R.id.imgContact4, "🩺 社区医院", "120")
    }

    private fun bindContactCard(cardId: Int, imageId: Int, name: String, phoneNumber: String) {
        val card = findViewById<com.google.android.material.card.MaterialCardView>(cardId)
        val imageView = findViewById<ImageView>(imageId)

        val photoUri = getContactPhotoUri(phoneNumber)
        if (photoUri != null) {
            imageView.setImageURI(photoUri)
        } else {
            imageView.setImageBitmap(AvatarGenerator.createInitialAvatar(name, 144))
        }

        card.setOnClickListener {
            showCallConfirmationDialog(name, phoneNumber)
        }
    }

    private fun setupBottomDock() {
        findViewById<Button>(R.id.btnContacts).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI))
        }
        findViewById<Button>(R.id.btnPhone).setOnClickListener {
            startActivity(Intent(Intent.ACTION_DIAL))
        }
        findViewById<Button>(R.id.btnCamera).setOnClickListener {
            startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
        }
        findViewById<Button>(R.id.btnGallery).setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW).apply { type = "image/*" })
        }
        findViewById<Button>(R.id.btnSos).setOnClickListener {
            showCallConfirmationDialog("🚨 紧急求助 (120)", "120")
        }
    }

    private fun setupProtectedSettings() {
        findViewById<View>(R.id.tvClock).setOnLongClickListener {
            showPinVerificationDialog()
            true
        }
    }

    private fun showCallConfirmationDialog(name: String, phoneNumber: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_call_confirmation, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.90).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        view.findViewById<TextView>(R.id.tvDialogName).text = name
        view.findViewById<TextView>(R.id.tvDialogPhone).text = phoneNumber

        val imgAvatar = view.findViewById<ImageView>(R.id.imgDialogAvatar)
        val photoUri = getContactPhotoUri(phoneNumber)
        if (photoUri != null) {
            imgAvatar.setImageURI(photoUri)
        } else {
            imgAvatar.setImageBitmap(AvatarGenerator.createInitialAvatar(name, 200))
        }

        view.findViewById<Button>(R.id.btnConfirmCall).setOnClickListener {
            dialog.dismiss()
            makeCall(phoneNumber)
        }
        view.findViewById<Button>(R.id.btnCancelCall).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun showPinVerificationDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_pin_lock, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.85).toInt(),
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val etPin = view.findViewById<EditText>(R.id.etPinInput)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedPin = prefs.getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

        view.findViewById<Button>(R.id.btnConfirmPin).setOnClickListener {
            if (etPin.text.toString().trim() == savedPin) {
                dialog.dismiss()
                try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            } else {
                Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show()
                etPin.setText("")
            }
        }
        view.findViewById<Button>(R.id.btnCancelPin).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun makeCall(phoneNumber: String) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), REQUEST_CALL)
        } else {
            startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")))
        }
    }

    private fun getContactPhotoUri(phoneNumber: String): Uri? {
        // Hard safety check: aborts silently if permission isn't granted yet
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val photoUriString = cursor.getString(0)
                    if (photoUriString != null) return Uri.parse(photoUriString)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun checkPermissions(): Boolean {
        val permissions = arrayOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_CONTACTS)
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), REQUEST_PERMISSIONS)
            return false
        }
        return true
    }
}
