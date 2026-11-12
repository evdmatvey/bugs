package com.example.bugs.data

import android.content.Context
import android.util.Log
import android.util.Xml
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.xmlpull.v1.XmlPullParser
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming

data class GoldQuote(
    val price: BigDecimal,
    val date: LocalDate
) {
    val points: Int = price.movePointLeft(2).setScale(0, RoundingMode.HALF_UP)
        .intValueExact().coerceAtLeast(1)

    init {
        require(price.signum() > 0)
    }
}

data class GoldState(
    val quote: GoldQuote? = null,
    val loading: Boolean = false,
    val error: Boolean = false,
    val updatedAt: Long = 0
)

interface GoldApi {
    @Streaming
    @GET("scripts/xml_metall.asp")
    suspend fun metals(
        @Query("date_req1") from: String,
        @Query("date_req2") to: String
    ): ResponseBody
}

class GoldRepository(
    context: Context,
    private val onChanged: () -> Unit
) {

    private val preferences = context.getSharedPreferences("gold", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private var lastAttempt = 0L
    private val api = Retrofit.Builder()
        .baseUrl("https://www.cbr.ru/")
        .client(
            OkHttpClient.Builder()
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
        )
        .build()
        .create(GoldApi::class.java)

    private val state = MutableStateFlow(
        GoldState(
            quote = runCatching {
                GoldQuote(
                    requireNotNull(preferences.getString("price", null)).toBigDecimal(),
                    LocalDate.parse(preferences.getString("date", null))
                )
            }.getOrNull(),
            updatedAt = preferences.getLong("updatedAt", 0)
        )
    )

    val gold = state.asStateFlow()

    suspend fun refresh(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val requestedAt = System.currentTimeMillis()

        mutex.withLock {
            if (lastAttempt >= requestedAt) return@withLock !state.value.error
            val age = requestedAt - state.value.updatedAt
            if (!force && state.value.quote != null && age in 0 until TimeUnit.HOURS.toMillis(1)) {
                return@withLock true
            }

            state.value = state.value.copy(loading = true, error = false)
            onChanged()

            try {
                val today = LocalDate.now(ZoneId.of("Europe/Moscow"))
                val format = DateTimeFormatter.ofPattern("dd/MM/yyyy")
                val quote = api.metals(today.minusDays(30).format(format), today.format(format)).use { body ->
                    body.byteStream().use { stream ->
                        val parser = Xml.newPullParser()
                        parser.setInput(stream, null)
                        var latest: GoldQuote? = null

                        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                            if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Record") {
                                val gold = parser.getAttributeValue(null, "Code") == "1"
                                val date = LocalDate.parse(
                                    parser.getAttributeValue(null, "Date"),
                                    DateTimeFormatter.ofPattern("dd.MM.yyyy")
                                )
                                var price: BigDecimal? = null

                                while (parser.next() != XmlPullParser.END_TAG || parser.name != "Record") {
                                    require(parser.eventType != XmlPullParser.END_DOCUMENT)
                                    if (gold && parser.eventType == XmlPullParser.START_TAG && parser.name == "Buy") {
                                        price = parser.nextText()
                                            .filterNot { it.isWhitespace() || it == '\u00a0' }
                                            .replace(',', '.')
                                            .toBigDecimal()
                                    }
                                }

                                if (gold && !date.isAfter(today) && (latest == null || date.isAfter(latest.date))) {
                                    latest = GoldQuote(requireNotNull(price), date)
                                }
                            }

                            parser.next()
                        }

                        requireNotNull(latest)
                    }
                }
                val current = state.value.quote
                require(current == null || !quote.date.isBefore(current.date))
                val updatedAt = System.currentTimeMillis()
                check(
                    preferences.edit()
                        .putString("price", quote.price.toPlainString())
                        .putString("date", quote.date.toString())
                        .putLong("updatedAt", updatedAt)
                        .commit()
                )
                state.value = GoldState(quote = quote, updatedAt = updatedAt)
                lastAttempt = updatedAt

                true
            } catch (e: CancellationException) {
                state.value = state.value.copy(loading = false)
                throw e
            } catch (e: Exception) {
                Log.w("GoldRepository", "Unable to refresh gold quote", e)
                state.value = state.value.copy(loading = false, error = true)
                lastAttempt = System.currentTimeMillis()

                false
            } finally {
                onChanged()
            }
        }
    }
}
