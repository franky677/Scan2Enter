package com.scan2enter.session

import androidx.compose.runtime.mutableStateOf

data class SessionCustomer(
    val id: Int,
    val name: String
)

object SessionCustomerStore {

    private val banco =
        SessionCustomer(
            id = 9,
            name = "BANCO"
        )

    val current =
        mutableStateOf(banco)

    fun setCustomer(
        id: Int,
        name: String
    ) {
        current.value =
            SessionCustomer(
                id = id,
                name = name
            )
    }

    fun useBanco() {
        current.value = banco
    }
}