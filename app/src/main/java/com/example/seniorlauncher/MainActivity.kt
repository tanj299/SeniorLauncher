package com.example.seniorlauncher

import android.Manifest
import android.annotation.SuppressLint
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
import android.widget.GridLayout
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
                tvBattery.setTextColor(if (batteryPct <= 20) Color.RED else Color.parseColor("#2E7D32"))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupBottomDock()
        setupProtectedSettings()
        
        if (checkPermissions()) {
            loadStarredContacts()
        }
    }

    override fun onResume() {
        super.onResume()
        setupClock()
        checkDefaultLauncherStatus()
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            loadStarredContacts()
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(batteryReceiver)
    }

    private fun checkDefaultLauncherStatus() {
        val warningBanner = findViewById<TextView>(R.id.tvNotDefaultWarning)
        val intent = Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_HOME) }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        val isDefault = resolveInfo?.activityInfo?.packageName == packageName

        if (!isDefault) {
            warningBanner.visibility = View.VISIBLE
            warningBanner.setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                    Toast.makeText(this, "请在列表中选择【简易桌面】", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        } else {
            warningBanner.visibility = View.GONE
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadStarredContacts()
        }
    }

    private fun setupClock() {
        val now = Date()
        findViewById<TextView>(R.id.tvClock).text = SimpleDateFormat("HH:mm", Locale.SIMPLIFIED_CHINESE).format(now)
        findViewById<TextView>(R.id.tvDateAndLunar).text = "${SimpleDateFormat("MM月dd日 EEE", Locale.SIMPLIFIED_CHINESE).format(now)} (${LunarCalendarUtil.getTodayLunarDate()})"
    }

    @SuppressLint("Range")
    private fun loadStarredContacts() {
        val gridContacts = findViewById<GridLayout>(R.id.gridContacts)
        val tvEmptyContacts = findViewById<TextView>(R.id.tvEmptyContacts)
        gridContacts.removeAllViews()

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1"
        
        val addedContacts = mutableSetOf<String>()
        var count = 0
        val maxContacts = 6

        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, selection, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")?.use { cursor ->
            while (cursor.moveToNext() && count < maxContacts) {
                val name = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)) ?: "未知"
                val phone = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))?.replace(" ", "") ?: ""

                if (!addedContacts.contains(name)) {
                    addedContacts.add(name)
                    addContactCardToGrid(gridContacts, name, phone, count)
                    count++
                }
            }
        }

        if (count == 0) {
            tvEmptyContacts.visibility = View.VISIBLE
            gridContacts.visibility = View.GONE
        } else {
            tvEmptyContacts.visibility = View.GONE
            gridContacts.visibility = View.VISIBLE
        }
    }

    private fun addContactCardToGrid(grid: GridLayout, name: String, phone: String, index: Int) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_contact_card, grid, false)
        val imgAvatar = view.findViewById<ImageView>(R.id.imgContactAvatar)
        view.findViewById<TextView>(R.id.tvContactName).text = name

        val photoUri = getContactPhotoUri(phone)
        if (photoUri != null) {
            imgAvatar.setImageURI(photoUri)
        } else {
            imgAvatar.setImageBitmap(AvatarGenerator.createInitialAvatar(name, 144))
        }

        val colors = arrayOf("#E3F2FD", "#E8F5E9", "#FFF3E0", "#F3E5F5", "#E0F7FA", "#FCE4EC")
        view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.item_contact_card)?.setCardBackgroundColor(Color.parseColor(colors[index % colors.size]))

        val params = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f)).apply {
            width = 0
            setMargins(8, 8, 8, 8)
        }
        view.layoutParams = params

        view.setOnClickListener { showCallConfirmationDialog(name, phone) }
        grid.addView(view)
    }

    private fun setupBottomDock() {
        findViewById<Button>(R.id.btnContacts).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, ContactsContract.Contacts.CONTENT_URI)) }
        findViewById<Button>(R.id.btnPhone).setOnClickListener { startActivity(Intent(Intent.ACTION_DIAL)) }
        findViewById<Button>(R.id.btnCamera).setOnClickListener { startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
        findViewById<Button>(R.id.btnGallery).setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW).apply { type = "image/*" }) }
        findViewById<Button>(R.id.btnSos).setOnClickListener { showCallConfirmationDialog("🚨 紧急求助 (120)", "120") }
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
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        view.findViewById<TextView>(R.id.tvDialogName).text = name
        view.findViewById<TextView>(R.id.tvDialogPhone).text = phoneNumber
        val imgAvatar = view.findViewById<ImageView>(R.id.imgDialogAvatar)
        val photoUri = getContactPhotoUri(phoneNumber)
        if (photoUri != null) imgAvatar.setImageURI(photoUri) else imgAvatar.setImageBitmap(AvatarGenerator.createInitialAvatar(name, 200))

        view.findViewById<Button>(R.id.btnConfirmCall).setOnClickListener {
            dialog.dismiss()
            makeCall(phoneNumber)
        }
        view.findViewById<Button>(R.id.btnCancelCall).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showPinVerificationDialog() {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_pin_lock, null)
        dialog.setContentView(view)

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.85).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)

        val etPin = view.findViewById<EditText>(R.id.etPinInput)
        val savedPin = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

        view.findViewById<Button>(R.id.btnConfirmPin).setOnClickListener {
            if (etPin.text.toString().trim() == savedPin) {
                dialog.dismiss()
                try { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } catch (e: Exception) { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            } else {
                Toast.makeText(this, "密码错误", Toast.LENGTH_SHORT).show()
                etPin.setText("")
            }
        }
        view.findViewById<Button>(R.id.btnCancelPin).setOnClickListener { dialog.dismiss() }
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber))
            contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.PHOTO_URI), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) return Uri.parse(cursor.getString(0) ?: return null)
            }
        } catch (e: Exception) { e.printStackTrace() }
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
