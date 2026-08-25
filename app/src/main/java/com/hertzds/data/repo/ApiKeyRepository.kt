package com.hertzds.data.repo

import com.hertzds.core.crypto.SecretStore
import com.hertzds.data.db.ApiKeyDao
import com.hertzds.data.db.ApiKeyEntity
import com.hertzds.data.db.UsageDao
import com.hertzds.deepseek.ApiFailure
import com.hertzds.deepseek.DeepSeekClient
import com.hertzds.deepseek.DeepSeekException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** A usable key: the decrypted secret plus the row it came from. */
data class ResolvedKey(val id: String, val label: String, val secret: String)

/** Credit view of one key, combining the live balance with local estimates. */
data class KeyStatus(
    val entity: ApiKeyEntity,
    val spentUsd: Double,
    val inCooldown: Boolean,
) {
    /** Live balance when we have one, otherwise "top-up minus what we spent". */
    val remainingUsd: Double?
        get() = entity.lastBalanceUsd
            ?: entity.manualToppedUpUsd?.let { (it - spentUsd).coerceAtLeast(0.0) }

    val budgetUsd: Double?
        get() = entity.manualToppedUpUsd
            ?: entity.lastBalanceUsd?.let { it + spentUsd }

    val remainingPercent: Float?
        get() {
            val budget = budgetUsd ?: return null
            val remaining = remainingUsd ?: return null
            if (budget <= 0.0) return null
            return (remaining / budget).toFloat().coerceIn(0f, 1f)
        }
}

class ApiKeyRepository(
    private val keyDao: ApiKeyDao,
    private val usageDao: UsageDao,
    private val client: DeepSeekClient,
) {

    companion object {
        private const val RATE_LIMIT_COOLDOWN_MS = 60_000L
        private const val BALANCE_COOLDOWN_MS = 6 * 60 * 60 * 1000L
        private const val AUTH_COOLDOWN_MS = 24 * 60 * 60 * 1000L
    }

    val keys: Flow<List<ApiKeyEntity>> = keyDao.observeAll()

    val hasKeys: Flow<Boolean> = keys.map { it.isNotEmpty() }

    suspend fun add(rawKey: String, label: String?): Result<ApiKeyEntity> {
        val trimmed = rawKey.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("empty key"))
        val existing = keyDao.all()
        if (existing.any { SecretStore.decrypt(it.encryptedKey) == trimmed }) {
            return Result.failure(IllegalStateException("duplicate"))
        }
        val entity = ApiKeyEntity(
            id = UUID.randomUUID().toString(),
            label = label?.takeIf { it.isNotBlank() } ?: "Key ${existing.size + 1}",
            encryptedKey = SecretStore.encrypt(trimmed),
            maskedKey = SecretStore.mask(trimmed),
            sortOrder = existing.size,
            createdAt = System.currentTimeMillis(),
        )
        keyDao.upsert(entity)
        refreshBalance(entity.id)
        return Result.success(entity)
    }

    suspend fun remove(id: String) = keyDao.delete(id)

    suspend fun setEnabled(id: String, enabled: Boolean) = keyDao.setEnabled(id, enabled)

    suspend fun setManualTopUp(id: String, amount: Double?) = keyDao.setManualTopUp(id, amount)

    /**
     * Picks the next usable key. Keys already tried in this turn are skipped so the
     * agent can walk the whole chain after 401/402/429 instead of retrying one key.
     */
    suspend fun nextKey(exclude: Set<String> = emptySet()): ResolvedKey? {
        val now = System.currentTimeMillis()
        val candidates = keyDao.all()
            .filter { it.enabled && it.id !in exclude }
            .filter { (it.cooldownUntil ?: 0L) <= now }
            .sortedWith(
                compareByDescending<ApiKeyEntity> { (it.lastBalanceUsd ?: Double.MAX_VALUE) > 0.0 }
                    .thenBy { it.sortOrder },
            )
        for (candidate in candidates) {
            val secret = SecretStore.decrypt(candidate.encryptedKey) ?: continue
            return ResolvedKey(candidate.id, candidate.label, secret)
        }
        return null
    }

    /** Records why a key failed and how long to park it. */
    suspend fun reportFailure(keyId: String, error: DeepSeekException) {
        val cooldown = when (error.failure) {
            ApiFailure.RATE_LIMIT -> System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
            ApiFailure.INSUFFICIENT_BALANCE -> System.currentTimeMillis() + BALANCE_COOLDOWN_MS
            ApiFailure.AUTH -> System.currentTimeMillis() + AUTH_COOLDOWN_MS
            else -> null
        }
        keyDao.setError(keyId, error.message, cooldown)
        if (error.failure == ApiFailure.INSUFFICIENT_BALANCE) {
            keyDao.setBalance(keyId, 0.0, System.currentTimeMillis())
        }
    }

    suspend fun reportSuccess(keyId: String) {
        keyDao.setError(keyId, null, null)
    }

    /** Asks DeepSeek for the authoritative balance of one key. */
    suspend fun refreshBalance(keyId: String): Double? {
        val entity = keyDao.get(keyId) ?: return null
        val secret = SecretStore.decrypt(entity.encryptedKey) ?: return null
        return runCatching {
            val response = client.balance(secret)
            val usd = response.balanceInfos.firstOrNull { it.currency.equals("USD", true) }
                ?: response.balanceInfos.firstOrNull()
            val value = usd?.totalBalance?.toDoubleOrNull() ?: 0.0
            keyDao.setBalance(keyId, value, System.currentTimeMillis())
            if (!response.isAvailable) {
                keyDao.setError(keyId, "balance unavailable", System.currentTimeMillis() + BALANCE_COOLDOWN_MS)
            }
            value
        }.onFailure { throwable ->
            (throwable as? DeepSeekException)?.let { keyDao.setError(keyId, it.message, null) }
        }.getOrNull()
    }

    suspend fun refreshAllBalances() {
        keyDao.all().filter { it.enabled }.forEach { refreshBalance(it.id) }
    }

    suspend fun statuses(): List<KeyStatus> {
        val now = System.currentTimeMillis()
        return keyDao.all().map { entity ->
            KeyStatus(
                entity = entity,
                spentUsd = usageDao.spendForKey(entity.id),
                inCooldown = (entity.cooldownUntil ?: 0L) > now,
            )
        }
    }

    /** Total credit left across every enabled key, or null if nothing is known. */
    suspend fun totalRemainingUsd(): Double? {
        val values = statuses().filter { it.entity.enabled }.mapNotNull { it.remainingUsd }
        return if (values.isEmpty()) null else values.sum()
    }
}
