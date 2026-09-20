package com.example.geofencing.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.geofencing.MainActivity
import com.example.geofencing.MainViewModel
import com.example.geofencing.UserRole

@Composable
fun MainScreen(
    currentRole: UserRole?,
    viewModel: MainViewModel,
    activity: MainActivity,
    onRoleSelected: (UserRole) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedRole by remember { mutableStateOf(currentRole ?: UserRole.STUDENT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🎓 Auto Silent Classroom", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(24.dp))

        // Dropdown for role
        Box {
            Button(onClick = { expanded = true }) {
                Text("Select Role: ${selectedRole.name}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                UserRole.values().forEach { role ->
                    DropdownMenuItem(
                        text = { Text(role.name) },
                        onClick = {
                            selectedRole = role
                            expanded = false
                            onRoleSelected(role)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Google Sign-In / Signed in email
        if (viewModel.signedInEmail == null) {
            Button(onClick = { viewModel.launchGoogleSignIn(activity) }) {
                Text("Sign In with Google")
            }
        } else {
            Text("Signed in as ${viewModel.signedInEmail}")
        }
    }
}
