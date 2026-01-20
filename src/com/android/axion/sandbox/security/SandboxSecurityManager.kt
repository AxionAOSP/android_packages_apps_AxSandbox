package com.android.axion.sandbox.security

import android.content.Context
import android.content.SharedPreferences
import android.hardware.biometrics.BiometricManager
import android.provider.Settings
import com.android.axion.sandbox.R
import java.security.MessageDigest

enum class SecurityType {
    NONE,
    PIN,
    PASSWORD,
    PATTERN
}

enum class LockedAppBehavior {
    ON_LEAVE,
    TIMEOUT,
    ON_SCREEN_OFF,
    ON_KILL
}

enum class PrivateSectionBehavior {
    ON_LEAVE,
    TIMEOUT
}

class SandboxSecurityManager(private val context: Context) {
    
    fun getSecurityType(): SecurityType {
        val type = Settings.Secure.getString(context.contentResolver, KEY_SECURITY_TYPE)
            ?: return SecurityType.NONE
        return try {
            SecurityType.valueOf(type)
        } catch (e: IllegalArgumentException) {
            SecurityType.NONE
        }
    }
    
    fun isSetup(): Boolean {
        return getSecurityType() != SecurityType.NONE && 
               Settings.Secure.getString(context.contentResolver, KEY_CREDENTIAL_HASH) != null
    }
    
    fun setPin(pin: String): Boolean {
        if (pin.length < MIN_PIN_LENGTH || pin.length > MAX_PIN_LENGTH) {
            return false
        }
        if (!pin.all { it.isDigit() }) {
            return false
        }
        
        val hash = hashCredential(pin)
        return Settings.Secure.putString(context.contentResolver, KEY_SECURITY_TYPE, SecurityType.PIN.name) &&
               Settings.Secure.putString(context.contentResolver, KEY_CREDENTIAL_HASH, hash)
    }
    
    fun setPassword(password: String): Boolean {
        if (password.length < MIN_PASSWORD_LENGTH) {
            return false
        }
        
        val hash = hashCredential(password)
        return Settings.Secure.putString(context.contentResolver, KEY_SECURITY_TYPE, SecurityType.PASSWORD.name) &&
               Settings.Secure.putString(context.contentResolver, KEY_CREDENTIAL_HASH, hash)
    }
    
    fun setPattern(pattern: List<Int>): Boolean {
        if (pattern.size < MIN_PATTERN_LENGTH) {
            return false
        }
        
        val patternString = pattern.joinToString(",")
        val hash = hashCredential(patternString)
        return Settings.Secure.putString(context.contentResolver, KEY_SECURITY_TYPE, SecurityType.PATTERN.name) &&
               Settings.Secure.putString(context.contentResolver, KEY_CREDENTIAL_HASH, hash)
    }
    
    fun verifyCredential(credential: String): Boolean {
        val storedHash = Settings.Secure.getString(context.contentResolver, KEY_CREDENTIAL_HASH)
            ?: return false
        val inputHash = hashCredential(credential)
        return storedHash == inputHash
    }
    
    fun verifyPattern(pattern: List<Int>): Boolean {
        val patternString = pattern.joinToString(",")
        return verifyCredential(patternString)
    }
    
    enum class BiometricType {
        NONE,
        FINGERPRINT,
        FACE
    }

    fun isBiometricAvailable(): Boolean {
        val biometricManager = context.getSystemService(BiometricManager::class.java)
        return biometricManager?.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    
    fun getBiometricType(): BiometricType {
        val pm = context.packageManager
        if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FINGERPRINT)) {
            return BiometricType.FINGERPRINT
        }
        if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_FACE)) {
            return BiometricType.FACE
        }
        return BiometricType.NONE
    }
    
    fun isBiometricEnabled(): Boolean {
        return Settings.Secure.getInt(context.contentResolver, KEY_BIOMETRIC_ENABLED, 0) == 1
    }
    
    fun setBiometricEnabled(enabled: Boolean) {
        Settings.Secure.putInt(context.contentResolver, KEY_BIOMETRIC_ENABLED, if (enabled) 1 else 0)
        if (!enabled) {
            setPreferBiometric(false)
        }
    }
    
    fun isPreferBiometric(): Boolean {
        return Settings.Secure.getInt(context.contentResolver, KEY_PREFER_BIOMETRIC, 0) == 1
    }
    
    fun setPreferBiometric(preferred: Boolean) {
        Settings.Secure.putInt(context.contentResolver, KEY_PREFER_BIOMETRIC, if (preferred) 1 else 0)
    }
    
    fun getPrivateSectionBehavior(): PrivateSectionBehavior {
        val behavior = Settings.Secure.getString(context.contentResolver, KEY_PRIVATE_SECTION_BEHAVIOR)
            ?: return PrivateSectionBehavior.ON_LEAVE
        return try {
            PrivateSectionBehavior.valueOf(behavior)
        } catch (e: IllegalArgumentException) {
            PrivateSectionBehavior.ON_LEAVE
        }
    }

    fun setPrivateSectionBehavior(behavior: PrivateSectionBehavior) {
        Settings.Secure.putString(context.contentResolver, KEY_PRIVATE_SECTION_BEHAVIOR, behavior.name)
    }
    
    fun getPrivateSectionTimeout(): Int {
        return Settings.Secure.getInt(context.contentResolver, KEY_PRIVATE_SECTION_TIMEOUT, DEFAULT_TIMEOUT_SECONDS)
    }
    
    fun setPrivateSectionTimeout(seconds: Int) {
        Settings.Secure.putInt(context.contentResolver, KEY_PRIVATE_SECTION_TIMEOUT, seconds)
    }

    fun getLockedAppTimeout(): Int {
        return Settings.Secure.getInt(context.contentResolver, KEY_LOCKED_APP_TIMEOUT, DEFAULT_TIMEOUT_SECONDS)
    }
    
    fun setLockedAppTimeout(seconds: Int) {
        Settings.Secure.putInt(context.contentResolver, KEY_LOCKED_APP_TIMEOUT, seconds)
    }
    
    fun getLastUnlockTime(): Long {
        return Settings.Secure.getLong(context.contentResolver, KEY_LAST_UNLOCK_TIME, 0L)
    }
    
    fun setLastUnlockTime(time: Long = System.currentTimeMillis()) {
        Settings.Secure.putLong(context.contentResolver, KEY_LAST_UNLOCK_TIME, time)
    }
    
    fun isTimeoutExpired(): Boolean {
        if (getPrivateSectionBehavior() != PrivateSectionBehavior.TIMEOUT) return false
        val elapsed = System.currentTimeMillis() - getLastUnlockTime()
        return elapsed > getPrivateSectionTimeout() * 1000L
    }
    
    private fun hashCredential(credential: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(credential.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
    
    fun getLockedAppBehavior(): LockedAppBehavior {
        return try {
            val ordinal = Settings.Secure.getInt(
                context.contentResolver,
                SETTING_LOCKED_APP_BEHAVIOR,
                LockedAppBehavior.ON_LEAVE.ordinal
            )
            LockedAppBehavior.entries.getOrElse(ordinal) { LockedAppBehavior.ON_LEAVE }
        } catch (e: Exception) {
            LockedAppBehavior.ON_LEAVE
        }
    }
    
    fun setLockedAppBehavior(behavior: LockedAppBehavior) {
        Settings.Secure.putInt(
            context.contentResolver,
            SETTING_LOCKED_APP_BEHAVIOR,
            behavior.ordinal
        )
    }
    
    fun setSecurityQuestion(question: String, answer: String) {
        Settings.Secure.putString(context.contentResolver, KEY_SECURITY_QUESTION, question)
        Settings.Secure.putString(context.contentResolver, KEY_SECURITY_ANSWER, hashCredential(answer.lowercase().trim()))
    }
    
    fun getSecurityQuestion(): String? {
        return Settings.Secure.getString(context.contentResolver, KEY_SECURITY_QUESTION)
    }
    
    fun hasSecurityQuestion(): Boolean {
        return getSecurityQuestion() != null && 
               Settings.Secure.getString(context.contentResolver, KEY_SECURITY_ANSWER) != null
    }
    
    fun verifySecurityAnswer(answer: String): Boolean {
        val storedHash = Settings.Secure.getString(context.contentResolver, KEY_SECURITY_ANSWER)
            ?: return false
        val inputHash = hashCredential(answer.lowercase().trim())
        return storedHash == inputHash
    }
    
    fun clearCredentials() {
        Settings.Secure.putString(context.contentResolver, KEY_SECURITY_TYPE, SecurityType.NONE.name)
        Settings.Secure.putString(context.contentResolver, KEY_CREDENTIAL_HASH, null)
        Settings.Secure.putString(context.contentResolver, KEY_SECURITY_QUESTION, null)
        Settings.Secure.putString(context.contentResolver, KEY_SECURITY_ANSWER, null)
        Settings.Secure.putInt(context.contentResolver, KEY_BIOMETRIC_ENABLED, 0)
    }
    
    companion object {
        private const val KEY_SECURITY_TYPE = "sandbox_security_type"
        private const val KEY_CREDENTIAL_HASH = "sandbox_credential_hash"
        private const val KEY_PRIVATE_SECTION_BEHAVIOR = "sandbox_private_section_behavior"
        private const val KEY_PRIVATE_SECTION_TIMEOUT = "sandbox_lock_timeout"
        private const val KEY_LOCKED_APP_TIMEOUT = "sandbox_locked_app_timeout"
        private const val KEY_LAST_UNLOCK_TIME = "sandbox_last_unlock_time"
        private const val KEY_BIOMETRIC_ENABLED = "sandbox_biometric_enabled"
        private const val KEY_PREFER_BIOMETRIC = "sandbox_prefer_biometric"
        private const val KEY_SECURITY_QUESTION = "sandbox_security_question"
        private const val KEY_SECURITY_ANSWER = "sandbox_security_answer"
        const val SETTING_LOCKED_APP_BEHAVIOR = "sandbox_locked_app_behavior"
        
        
        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 4
        const val MIN_PASSWORD_LENGTH = 4
        const val MIN_PATTERN_LENGTH = 4
        const val DEFAULT_TIMEOUT_SECONDS = 30
        
        val SECURITY_QUESTIONS = listOf(
            R.string.question_pet_name,
            R.string.question_birth_city,
            R.string.question_fav_movie,
            R.string.question_mother_maiden,
            R.string.question_first_car
        )
    }
}
