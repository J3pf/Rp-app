package dev.j3pf.rp.data.ports

import dev.j3pf.rp.data.secure.Session
import kotlinx.coroutines.flow.StateFlow

/**
 * Credential store port (S18: no plaintext credentials anywhere).
 *
 * Android impl: the existing Tink AES256-GCM store (`app/.../secure/SecretStore.kt`) backed by
 * Android Keystore (`android-keystore://rp_master_key`), keyset prefs `rp_keyset` in
 * `rp_keyset_prefs`, secret prefs `rp_secrets`, Base64 NO_WRAP ciphertext, field name as
 * associated data. iOS impl (T3d, written fresh under S12): Keychain with the
 * `keychain-access-groups` entitlement, CryptoKit AES-GCM, identical AD discipline.
 */
interface SecretStore {
    val session: StateFlow<Session?>

    /** Synchronous read for receivers/services (session cache value). */
    fun current(): Session?

    fun save(session: Session)

    fun clear()

    /** 8 random bytes as 16 lowercase hex chars; two calls differ (SC-04). */
    fun newRelayId(): String
}

/**
 * Low-level crypto seam inside the secure store — extracted so the S14 upgrade proof can pin the
 * ciphertext FORMAT and associated-data discipline without a real device Keystore (ADR §9).
 * Contract: Base64 NO_WRAP ciphertext, the `field` key is the AEAD associated data; decrypt of a
 * foreign/garbled value returns null instead of throwing (SC-03 tolerance).
 */
interface SecureCrypto {
    fun encrypt(
        field: String,
        value: String,
    ): String

    fun decrypt(
        field: String,
        value: String,
    ): String?
}
