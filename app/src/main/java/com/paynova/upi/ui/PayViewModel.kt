package com.paynova.upi.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.paynova.upi.Graph
import com.paynova.upi.payment.Draft
import kotlinx.coroutines.launch
import java.util.UUID

class PayViewModel : ViewModel() {
    var draft by mutableStateOf<Draft?>(null)
    var paying by mutableStateOf(false)
    var payError by mutableStateOf<String?>(null)
    private var key: String? = null

    fun begin(d: Draft) {
        draft = d
        key = null
        payError = null
    }

    /** One idempotency key per payment attempt; reused on retry so a retry can never double-charge. */
    fun ensureKey(): String {
        val k = key ?: UUID.randomUUID().toString()
        key = k
        return k
    }

    fun pay(onDone: (String) -> Unit) {
        val d = draft ?: return
        if (d.resolved == null || paying) return
        paying = true
        payError = null
        val k = ensureKey()
        viewModelScope.launch {
            val res = Graph.repo.pay(d, k)
            paying = false
            res.onSuccess { id ->
                key = null
                draft = null
                onDone(id)
            }.onFailure {
                payError = it.message ?: "Payment could not be started. You were not charged. Please try again."
            }
        }
    }
}
