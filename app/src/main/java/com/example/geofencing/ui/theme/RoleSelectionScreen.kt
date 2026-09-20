package com.example.geofencing.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.geofencing.MainViewModel
import com.example.geofencing.UserRole

@Composable
fun RoleSelectionScreen(
    onRoleSelect: (UserRole) -> Unit,
    viewModel: MainViewModel,
    onSignedIn: () -> Unit
) {
    var selectedRole by remember { mutableStateOf<UserRole?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Select Role", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { selectedRole = UserRole.HOST }) { Text("Host") }
            Button(onClick = { selectedRole = UserRole.STUDENT }) { Text("Student") }
            Button(onClick = { selectedRole = UserRole.ADMIN }) { Text("Admin") }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (selectedRole != null) {
            Button(
                onClick = {
                    onRoleSelect(selectedRole!!)
                    onSignedIn()
                },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) { Text("Sign In / Sign Up") }
        }
    }
}
