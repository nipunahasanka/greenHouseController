package com.example.ui.screens

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@Composable
fun AuthScreen(
  onAuthSuccess: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val credentialManager = remember { CredentialManager.create(context) }
  val auth = Firebase.auth

  var isRegisterMode by remember { mutableStateOf(false) }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var isLoading by remember { mutableStateOf(false) }

  Box(
    modifier = modifier
      .fillMaxSize()
      .statusBarsPadding()
      .navigationBarsPadding()
      .background(
        Brush.verticalGradient(
          listOf(
            MaterialTheme.colorScheme.surface,
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          )
        )
      )
      .padding(20.dp),
    contentAlignment = Alignment.Center
  ) {
    ElevatedCard(
      shape = RoundedCornerShape(24.dp),
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .testTag("auth_card")
    ) {
      Column(
        modifier = Modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // App Brand Icon
        Box(
          modifier = Modifier
            .size(56.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Yard,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "Greenhouse Controller",
          style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = if (isRegisterMode) "Create an account to manage your ESP32" else "Sign in to access your greenhouse",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Switcher: Sign In vs Register
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(4.dp)
        ) {
          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (!isRegisterMode) MaterialTheme.colorScheme.primary else Color.Transparent,
            modifier = Modifier
              .weight(1f)
              .clickable {
                isRegisterMode = false
                errorMessage = null
              }
              .testTag("tab_signin")
          ) {
            Text(
              text = "Sign In",
              textAlign = TextAlign.Center,
              fontWeight = if (!isRegisterMode) FontWeight.Bold else FontWeight.Normal,
              color = if (!isRegisterMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 10.dp)
            )
          }

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = if (isRegisterMode) MaterialTheme.colorScheme.primary else Color.Transparent,
            modifier = Modifier
              .weight(1f)
              .clickable {
                isRegisterMode = true
                errorMessage = null
              }
              .testTag("tab_register")
          ) {
            Text(
              text = "Register",
              textAlign = TextAlign.Center,
              fontWeight = if (isRegisterMode) FontWeight.Bold else FontWeight.Normal,
              color = if (isRegisterMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(vertical = 10.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Email field
        OutlinedTextField(
          value = email,
          onValueChange = { email = it; errorMessage = null },
          label = { Text("Email Address") },
          leadingIcon = {
            Icon(imageVector = Icons.Default.Email, contentDescription = null)
          },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_email_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Password field
        OutlinedTextField(
          value = password,
          onValueChange = { password = it; errorMessage = null },
          label = { Text("Password") },
          leadingIcon = {
            Icon(imageVector = Icons.Default.Lock, contentDescription = null)
          },
          trailingIcon = {
            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
              Icon(
                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = null
              )
            }
          },
          visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          singleLine = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("auth_password_input")
        )

        // Confirm Password if registering
        if (isRegisterMode) {
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it; errorMessage = null },
            label = { Text("Confirm Password") },
            leadingIcon = {
              Icon(imageVector = Icons.Default.Lock, contentDescription = null)
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("auth_confirm_password_input")
          )
        }

        // Error message banner
        if (errorMessage != null) {
          Spacer(modifier = Modifier.height(12.dp))
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = errorMessage ?: "",
              color = MaterialTheme.colorScheme.onErrorContainer,
              style = MaterialTheme.typography.bodySmall,
              modifier = Modifier.padding(10.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Primary Email Submit Button
        Button(
          onClick = {
            if (email.isBlank() || password.isBlank()) {
              errorMessage = "Please enter both email and password"
              return@Button
            }
            if (isRegisterMode && password != confirmPassword) {
              errorMessage = "Passwords do not match"
              return@Button
            }
            if (password.length < 6) {
              errorMessage = "Password must be at least 6 characters"
              return@Button
            }

            isLoading = true
            errorMessage = null

            coroutineScope.launch {
              try {
                if (isRegisterMode) {
                  auth.createUserWithEmailAndPassword(email.trim(), password).await()
                } else {
                  auth.signInWithEmailAndPassword(email.trim(), password).await()
                }
                onAuthSuccess()
              } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Authentication failed"
              } finally {
                isLoading = false
              }
            }
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("auth_submit_button")
        ) {
          if (isLoading) {
            CircularProgressIndicator(
              color = Color.White,
              strokeWidth = 2.dp,
              modifier = Modifier.size(20.dp)
            )
          } else {
            Text(
              text = if (isRegisterMode) "Register Account" else "Sign In with Email",
              fontWeight = FontWeight.Bold
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .weight(1f)
              .height(1.dp)
              .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
          )
          Text(
            text = "OR",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp)
          )
          Box(
            modifier = Modifier
              .weight(1f)
              .height(1.dp)
              .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Google Sign-In with Credential Manager
        OutlinedButton(
          onClick = {
            isLoading = true
            errorMessage = null

            val clientId = try {
              context.getString(R.string.default_web_client_id)
            } catch (_: Exception) {
              ""
            }

            if (clientId.isBlank()) {
              errorMessage = "Google Web Client ID missing in configuration"
              isLoading = false
              return@OutlinedButton
            }

            val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
            val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

            coroutineScope.launch {
              try {
                val result = credentialManager.getCredential(context as Activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                  val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                  val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                  auth.signInWithCredential(authCredential).await()
                  onAuthSuccess()
                } else {
                  errorMessage = "Unexpected credential format returned"
                }
              } catch (e: GetCredentialCancellationException) {
                Log.w("Auth", "Google sign-in cancelled")
              } catch (e: Exception) {
                Log.e("Auth", "Google sign-in error", e)
                errorMessage = e.localizedMessage ?: "Google sign-in failed"
              } finally {
                isLoading = false
              }
            }
          },
          enabled = !isLoading,
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("google_signin_button")
        ) {
          Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
