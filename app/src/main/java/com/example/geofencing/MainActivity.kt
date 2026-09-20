@file:OptIn(ExperimentalMaterial3Api::class)
package com.example.geofencing

import androidx.activity.viewModels
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.geofencing.ui.theme.GeofencingTheme
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import androidx.compose.ui.graphics.Color
import com.example.geofencing.ui.*
import com.google.firebase.FirebaseApp
import com.google.android.gms.tasks.Task
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings

class MainActivity : ComponentActivity() {

    private val permissionsGranted = mutableStateOf(false)
    private val signedIn = mutableStateOf(false)
    private val roleSelected = mutableStateOf(false)
    private val selectedRole = mutableStateOf<UserRole?>(null)
    private val emailInput = mutableStateOf("")
    private val passwordInput = mutableStateOf("")
    private val roleMenuExpanded = mutableStateOf(false)
    private val viewModel: MainViewModel by viewModels()
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    /** -------------------- Permission Launchers -------------------- **/

    // Step 5: Background location launcher
    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                Toast.makeText(
                    this,
                    "Background location ('Allow all the time') is required. Please enable it manually.",
                    Toast.LENGTH_LONG
                ).show()
                openLocationSettings()
            } else {
                onAllPermissionsGranted()
            }
        }

    // Step 4: Foreground location
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                requestBackgroundLocation()
            } else {
                Toast.makeText(this, "Location permission is required.", Toast.LENGTH_SHORT).show()
            }
        }

    // Step 3: Phone + contacts
    private val communicationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.all { it.value }
            if (allGranted) {
                requestLocationPermissions()
            } else {
                Toast.makeText(this, "Phone & contact permissions are required.", Toast.LENGTH_SHORT).show()
            }
        }

    // Step 2: Notifications
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                requestCommunicationPermissions()
            } else {
                Toast.makeText(this, "Notification permission is required.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        auth = FirebaseAuth.getInstance()
        signedIn.value = auth.currentUser != null

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            GeofencingTheme {
                LaunchedEffect(Unit) {
                    if (!permissionsGranted.value) requestAllPermissions()
                }

                when {
                    !permissionsGranted.value -> PermissionsScreen()
                    !signedIn.value -> LoginScreen()
                    signedIn.value && !roleSelected.value -> RoleSelectionScreen()

                    selectedRole.value == UserRole.HOST -> HostHomeScreen(viewModel, this) { signOut() }
                    selectedRole.value == UserRole.STUDENT -> StudentHomeScreen(viewModel, this) { signOut() }
                    selectedRole.value == UserRole.ADMIN -> AdminHomeScreen(viewModel, this) { signOut() }
                }
            }
        }
    }

    /** -------------------- Permissions Flow -------------------- **/
    private fun startPermissionFlow() {
        // Step 1: Check DND access
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Toast.makeText(this, "Please allow Do Not Disturb access.", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            return
        }

        // Step 2: Notifications
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }

        // Step 3: Contacts + Phone
        requestCommunicationPermissions()
    }

    private fun requestCommunicationPermissions() {
        val missing = mutableListOf<String>()
        listOf(
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS
        ).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                missing.add(it)
        }

        if (missing.isNotEmpty()) {
            communicationPermissionLauncher.launch(missing.toTypedArray())
        } else {
            requestLocationPermissions()
        }
    }

    private fun requestLocationPermissions() {
        val missing = mutableListOf<String>()
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ).forEach {
            if (ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED)
                missing.add(it)
        }

        if (missing.isNotEmpty()) {
            locationPermissionLauncher.launch(missing.toTypedArray())
        } else {
            requestBackgroundLocation()
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(
                    this,
                    "Please grant 'Allow all the time' location access for GeoMute.",
                    Toast.LENGTH_LONG
                ).show()
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return
            }
        }
        onAllPermissionsGranted()
    }

    private fun openLocationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = android.net.Uri.fromParts("package", packageName, null)
        startActivity(intent)
    }

    private fun checkAndRequestSpecialPermissions() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED)
        ) {
            permissionsGranted.value = false
            return
        }
        permissionsGranted.value = true
    }

    private fun onAllPermissionsGranted() {
        permissionsGranted.value = true
        Toast.makeText(this, "✅ All permissions granted!", Toast.LENGTH_SHORT).show()
    }

    private fun requestAllPermissions() {
        startPermissionFlow()
    }

    override fun onResume() {
        super.onResume()
        syncSignedInState()
        if (!permissionsGranted.value) checkAndRequestSpecialPermissions()
    }
    private fun syncSignedInState() {
        signedIn.value = auth.currentUser != null
    }

    /** -------------------- Composables -------------------- **/
    @Composable
    fun PermissionsScreen() {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GeoMute requires all permissions to function properly.")
                Spacer(Modifier.height(16.dp))
                Button(onClick = { requestAllPermissions() }) { Text("Grant All Permissions") }
            }
        }
    }




    /** -------------------- Permissions Flow -------------------- **/



    /** -------------------- Composables -------------------- **/

    @Composable
    fun LoginScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = emailInput.value,
                    onValueChange = { emailInput.value = it },
                    label = { Text("Email") },
                    placeholder = { Text("example@gmail.com") },
                    textStyle = TextStyle(Color.Black),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = passwordInput.value,
                    onValueChange = { passwordInput.value = it },
                    label = { Text("Password") },
                    placeholder = { Text("Min 6 characters") },
                    textStyle = TextStyle(Color.Black),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        cursorColor = MaterialTheme.colorScheme.primary
                    )
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { registerEmailPassword() }) { Text("Register") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = { signInEmailPassword() }) { Text("Sign In") }
                Spacer(Modifier.height(16.dp))
                Button(onClick = { signInWithGoogle() }) { Text("Sign in with Google") }
            }
        }
    }

    @Composable
    fun RoleSelectionScreen() {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Select your role:", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { roleMenuExpanded.value = true }) {
                    Text(
                        if (selectedRole.value == UserRole.STUDENT) "USER"
                        else selectedRole.value?.name ?: "Choose Role"
                    )

                }
                DropdownMenu(
                    expanded = roleMenuExpanded.value,
                    onDismissRequest = { roleMenuExpanded.value = false }
                ) {
                    UserRole.values().forEach { role ->
                        DropdownMenuItem(
                            text = {
                                Text(if (role == UserRole.STUDENT) "USER" else role.name)
                            }
                            ,
                            onClick = {
                                selectedRole.value = role
                                roleMenuExpanded.value = false
                                roleSelected.value = true
                            }
                        )
                    }
                }
            }
        }
    }



    /** -------------------- Auth -------------------- **/
    private fun registerEmailPassword() {
        val email = emailInput.value.trim()
        val password = passwordInput.value.trim()
        if (!validateInput(email, password)) return

        auth.createUserWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                onAuthSuccess(email)
            } else Toast.makeText(this, "Registration failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun signInEmailPassword() {
        val email = emailInput.value.trim()
        val password = passwordInput.value.trim()
        if (!validateInput(email, password)) return

        auth.signInWithEmailAndPassword(email, password).addOnCompleteListener { task ->
            if (task.isSuccessful) onAuthSuccess(email)
            else Toast.makeText(this, "Sign in failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun validateInput(email: String, password: String): Boolean {
        if (!email.endsWith("@gmail.com")) { Toast.makeText(this, "Email must be Gmail", Toast.LENGTH_SHORT).show(); return false }
        if (password.length < 6) { Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show(); return false }
        return true
    }

    private fun onAuthSuccess(email: String, role: UserRole? = null) {
        val prefs = getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("email", email).apply()

        emailInput.value = email       // <-- update this instead of savedEmail
        syncSignedInState()
        roleSelected.value = false     // user will select role after login

        Toast.makeText(this, "Signed in as $email", Toast.LENGTH_SHORT).show()
    }
    /** -------------------- Google Sign-In -------------------- **/
    private fun signInWithGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            startActivityForResult(googleSignInClient.signInIntent, 1001)
        }
    }
    private fun handleSignInResult(task: Task<GoogleSignInAccount>) {
        try {
            val account = task.getResult(ApiException::class.java)
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential)
                .addOnSuccessListener {
                    Toast.makeText(this, "Sign-in successful!", Toast.LENGTH_SHORT).show()
                    viewModel.onAuthSuccess()
                    syncSignedInState()// Now auth.currentUser is valid
                    navigateToRoleSelection()
                }

                .addOnFailureListener { e ->
                    Toast.makeText(this, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        } catch (e: ApiException) {
            Toast.makeText(this, "Google sign-in failed: ${e.statusCode}", Toast.LENGTH_SHORT).show()
        }
    }
    private fun navigateToRoleSelection() {
        signedIn.value = true
        roleSelected.value = false
    }



    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1001) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            handleSignInResult(task)
            try {
                val account = task.getResult(ApiException::class.java)!!
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                auth.signInWithCredential(credential).addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) onAuthSuccess(account.email ?: "")
                    else Toast.makeText(this, "Google Sign-In failed", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** -------------------- Sign-out -------------------- **/
    private fun signOut() {
        auth.signOut()
        selectedRole.value = null
        roleSelected.value = false
        signedIn.value = false
        emailInput.value = ""
        passwordInput.value = ""
    }

    /** -------------------- Geofence placeholders -------------------- **/
    fun addGeofenceAtCurrentLocation(name: String, radius: Float) {
        Toast.makeText(this, "Geofence added at current location: $name, $radius m", Toast.LENGTH_SHORT).show()
    }

    fun addGeofenceAt(lat: Double, lng: Double, name: String, radius: Float) {
        Toast.makeText(this, "Geofence added at ($lat,$lng): $name, $radius m", Toast.LENGTH_SHORT).show()
    }
}
