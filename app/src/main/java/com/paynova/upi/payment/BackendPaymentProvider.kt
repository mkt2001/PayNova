package com.paynova.upi.payment

import com.paynova.upi.BuildConfig
import com.paynova.upi.Graph
import com.paynova.upi.data.TxEntity
import com.paynova.upi.data.TxStatus
import kotlinx.coroutines.CancellationException
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

object ProviderConfig {
    val baseUrl: String = BuildConfig.BACKEND_URL.let { if (it.isNotEmpty() && !it.endsWith("/")) "$it/" else it }
    /** Production stays disabled until an HTTPS backend URL is compiled in. */
    val isConfigured: Boolean get() = baseUrl.startsWith("https://")
    const val SUPPORT_EMAIL = ""
}

data class LoginBody(val email: String, val password: String)
data class LoginResp(val accessToken: String)
data class ResolveBody(val kind: String, val value: String, val ifsc: String? = null, val name: String? = null)
data class ResolveResp(val name: String?, val vpa: String, val verified: Boolean, val merchantInfo: String?)
data class InitiateBody(val vpa: String, val name: String?, val amountPaise: Long, val note: String, val method: String, val category: String)
data class InitiateResp(val providerRef: String, val status: String, val upiTxnId: String?, val maskedAccount: String?)
data class StatusResp(val status: String, val upiTxnId: String?, val maskedAccount: String?)
data class TxDto(
    val id: String, val counterpartyName: String?, val counterpartyVpa: String, val amountPaise: Long,
    val direction: String, val status: String, val method: String, val note: String?, val category: String?,
    val createdAt: Long, val providerRef: String, val upiTxnId: String?, val maskedAccount: String?
)
data class BillBody(val category: String, val customerId: String)
data class BillResp(val billerName: String, val customerName: String?, val dueDate: String?, val amountPaise: Long)

interface BackendApi {
    @POST("api/auth/login") suspend fun login(@Body b: LoginBody): LoginResp
    @POST("api/qr/resolve") suspend fun resolve(@Body b: ResolveBody): ResolveResp
    @POST("api/payment/initiate")
    suspend fun initiate(@Header("Idempotency-Key") key: String, @Body b: InitiateBody): InitiateResp
    @GET("api/payment/status/{id}") suspend fun status(@Path("id") id: String): StatusResp
    @GET("api/transactions") suspend fun transactions(): List<TxDto>
    @POST("api/bbps/fetch") suspend fun bill(@Body b: BillBody): BillResp
}

/** Talks to the PayNova backend (see /backend), which holds the PSP credentials. No secrets live in the app. */
object BackendPaymentProvider : PaymentProvider {
    override val mode = ProviderMode.PRODUCTION
    override val supportsUpiLite = false

    private val api: BackendApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val b = chain.request().newBuilder()
                val t = Graph.settings.authToken
                if (t.isNotBlank()) b.header("Authorization", "Bearer $t")
                chain.proceed(b.build())
            }
            .build()
        Retrofit.Builder()
            .baseUrl(ProviderConfig.baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BackendApi::class.java)
    }

    private suspend fun <T> call(block: suspend () -> T): Result<T> =
        if (!ProviderConfig.isConfigured) {
            Result.failure(NotConfiguredException())
        } else {
            try {
                Result.success(block())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun st(s: String): TxStatus = try { TxStatus.valueOf(s.uppercase()) } catch (e: Exception) { TxStatus.PENDING }

    suspend fun login(email: String, password: String): Result<String> =
        call { api.login(LoginBody(email, password)).accessToken }

    private suspend fun resolve(kind: String, value: String, ifsc: String?, name: String?): Result<Recipient> =
        call { api.resolve(ResolveBody(kind, value, ifsc, name)) }
            .map { Recipient(it.name, it.vpa, it.verified, it.merchantInfo) }

    override suspend fun resolveVpa(vpa: String, qrName: String?) = resolve("VPA", vpa, null, qrName)
    override suspend fun resolveMobile(mobile: String, contactName: String?) = resolve("MOBILE", mobile, null, contactName)
    override suspend fun resolveBank(account: String, ifsc: String, name: String?) = resolve("BANK", account, ifsc, name)

    override suspend fun initiate(req: PaymentRequest): Result<PaymentHandle> =
        call { api.initiate(req.idempotencyKey, InitiateBody(req.vpa, req.name, req.amountPaise, req.note, req.method, req.category)) }
            .map { PaymentHandle(it.providerRef, st(it.status), it.upiTxnId, it.maskedAccount) }

    override suspend fun status(providerRef: String): Result<ProviderStatus> =
        call { api.status(providerRef) }.map { ProviderStatus(st(it.status), it.upiTxnId, it.maskedAccount) }

    override suspend fun listTransactions(): Result<List<TxEntity>> =
        call { api.transactions() }.map { list ->
            list.map {
                TxEntity(
                    id = it.id, counterpartyName = it.counterpartyName.orEmpty(), counterpartyVpa = it.counterpartyVpa,
                    amountPaise = it.amountPaise, direction = it.direction, status = st(it.status).name, method = it.method,
                    note = it.note.orEmpty(), category = it.category ?: "Others", createdAt = it.createdAt,
                    providerRef = it.providerRef, upiTxnId = it.upiTxnId.orEmpty(), maskedAccount = it.maskedAccount.orEmpty(),
                    demo = false, idempotencyKey = it.id
                )
            }
        }

    override suspend fun fetchBill(category: String, customerId: String): Result<Bill> =
        call { api.bill(BillBody(category, customerId)) }
            .map { Bill(it.billerName, customerId, it.customerName, it.dueDate, it.amountPaise, false) }
}
