package com.scan2enter.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.scan2enter.sales.SalesAccessManager

@Composable
fun SalesUnlockDialog(
    accessManager: SalesAccessManager,
    onDismiss: () -> Unit,
    onUnlocked: () -> Unit,
    areaName: String = "VENDITE"
) {
    val firstSetup = !accessManager.hasPassword()

    var password by remember {
        mutableStateOf("")
    }

    var confirmPassword by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var passwordVisible by remember {
        mutableStateOf(false)
    }

    fun confirm() {
        error = null

        if (firstSetup) {
            when {
                password.length < SalesAccessManager.MIN_PASSWORD_LENGTH -> {
                    error =
                        "La password deve contenere almeno " +
                                "${SalesAccessManager.MIN_PASSWORD_LENGTH} caratteri"
                }

                password != confirmPassword -> {
                    error = "Le due password non coincidono"
                }

                else -> {
                    accessManager.setPassword(password)
                    onUnlocked()
                }
            }
        } else {
            if (accessManager.verifyPassword(password)) {
                onUnlocked()
            } else {
                error = "Password non corretta"
                password = ""
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (firstSetup) {
                    "Imposta password $areaName"
                } else {
                    "Accesso $areaName"
                }
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (firstSetup) {
                    Text(
                        "Proteggi i dati $areaName con la password riservata."
                    )
                } else {
                    Text(
                        "Inserisci la password per visualizzare $areaName."
                    )
                }

                OutlinedTextField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(
                            if (firstSetup) {
                                "Nuova password"
                            } else {
                                "Password"
                            }
                        )
                    },
                    singleLine = true,
                    visualTransformation =
                        if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "🙈" else "👁")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction =
                            if (firstSetup) {
                                ImeAction.Next
                            } else {
                                ImeAction.Done
                            }
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (!firstSetup) {
                                confirm()
                            }
                        }
                    )
                )

                if (firstSetup) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = {
                            confirmPassword = it
                            error = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text("Ripeti password")
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                confirm()
                            }
                        )
                    )
                }

                error?.let {
                    Text(it)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    confirm()
                }
            ) {
                Text(
                    if (firstSetup) {
                        "SALVA E APRI"
                    } else {
                        "APRI"
                    }
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("ANNULLA")
            }
        }
    )
}

@Composable
fun SalesChangePasswordDialog(
    accessManager: SalesAccessManager,
    onDismiss: () -> Unit,
    onChanged: () -> Unit
) {
    var currentPassword by remember {
        mutableStateOf("")
    }

    var newPassword by remember {
        mutableStateOf("")
    }

    var confirmPassword by remember {
        mutableStateOf("")
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var passwordVisible by remember {
        mutableStateOf(false)
    }

    fun confirm() {
        error = null

        when {
            !accessManager.verifyPassword(currentPassword) -> {
                error = "Password attuale non corretta"
            }

            newPassword.length <
                    SalesAccessManager.MIN_PASSWORD_LENGTH -> {
                error =
                    "La nuova password deve contenere almeno " +
                            "${SalesAccessManager.MIN_PASSWORD_LENGTH} caratteri"
            }

            newPassword != confirmPassword -> {
                error = "Le due nuove password non coincidono"
            }

            else -> {
                accessManager.setPassword(newPassword)
                onChanged()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Cambia password VENDITE")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = {
                        currentPassword = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Password attuale")
                    },
                    singleLine = true,
                    visualTransformation =
                        if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "🙈" else "👁")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    )
                )

                OutlinedTextField(
                    value = newPassword,
                    onValueChange = {
                        newPassword = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Nuova password")
                    },
                    singleLine = true,
                    visualTransformation =
                        if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "🙈" else "👁")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password
                    )
                )

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = {
                        confirmPassword = it
                        error = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Ripeti nuova password")
                    },
                    singleLine = true,
                    visualTransformation =
                        if (passwordVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Text(if (passwordVisible) "🙈" else "👁")
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            confirm()
                        }
                    )
                )

                error?.let {
                    Text(it)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    confirm()
                }
            ) {
                Text("CAMBIA")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("ANNULLA")
            }
        }
    )
}