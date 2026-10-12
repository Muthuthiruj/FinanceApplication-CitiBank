package com.example.financeapplication.data.repository

import com.example.financeapplication.core.DemoAccount
import com.example.financeapplication.core.Resource
import com.example.financeapplication.data.local.AppPrefs
import com.example.financeapplication.data.remote.UserSeeder
import com.example.financeapplication.data.remote.str
import com.example.financeapplication.data.remote.toFriendlyMessage
import com.example.financeapplication.domain.model.User
import com.example.financeapplication.domain.repository.AuthRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseDatabase,
    private val prefs: AppPrefs
) : AuthRepository {

    private data class PendingSignUp(val fullName: String, val email: String, val phone: String, val password: String)

    private var pending: PendingSignUp? = null
    private val _user = MutableStateFlow<User?>(null)
    private var profileRef: DatabaseReference? = null
    private var profileListener: ValueEventListener? = null

    override val currentUser: StateFlow<User?> = _user.asStateFlow()
    override val onboardingSeen: Boolean get() = prefs.onboardingSeen
    override val pendingEmail: String get() = pending?.email.orEmpty()
    override val isLoggedIn: Boolean
        get() = auth.currentUser?.let { isVerifiedOrDemo(it) } == true

    init {
        // Keeps `currentUser` in sync with Firebase Auth + the profile node.
        auth.addAuthStateListener { fa -> bindProfile(fa.currentUser) }
    }

    private fun bindProfile(user: FirebaseUser?) {
        profileListener?.let { profileRef?.removeEventListener(it) }
        profileRef = null; profileListener = null
        if (user == null) { _user.value = null; return }
        // Show something immediately, then the real profile as soon as it arrives.
        _user.value = User(user.uid, user.displayName.orEmpty(), user.email.orEmpty(), "", "")
        val ref = db.getReference("users/${user.uid}/profile")
        val listener = object : ValueEventListener {
            override fun onDataChange(s: DataSnapshot) {
                if (s.exists()) _user.value = User(user.uid, s.str("fullName"), s.str("email").ifEmpty { user.email.orEmpty() }, s.str("phone"), s.str("address"))
            }
            override fun onCancelled(error: DatabaseError) = Unit
        }
        ref.addValueEventListener(listener)
        profileRef = ref; profileListener = listener
    }

    override fun markOnboardingSeen() { prefs.onboardingSeen = true }

    override suspend fun login(email: String, password: String): Resource<User> {
        return try {
            val isDemo = DemoAccount.isDemoEmail(email.trim()) && password == DemoAccount.PASSWORD
            val user = try {
                auth.signInWithEmailAndPassword(email.trim(), password).await().user
            } catch (e: Exception) {
                // First use of the demo credentials: create the demo account (with sample data) on the fly.
                val missing = e is FirebaseAuthInvalidUserException || e is FirebaseAuthInvalidCredentialsException
                if (isDemo && missing) createDemoAccount() else throw e
            } ?: return Resource.Error("Sign in failed")
            if (!isVerifiedOrDemo(user)) runCatching { user.reload().await() } // pick up a verification done in the mail app
            if (!isVerifiedOrDemo(auth.currentUser ?: user)) {
                runCatching { user.sendEmailVerification().await() }
                auth.signOut()
                Resource.Error("Please verify your email first. We just sent you a new verification link.")
            } else {
                Resource.Success(User(user.uid, user.displayName.orEmpty(), user.email.orEmpty(), "", ""))
            }
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun startSignUp(fullName: String, email: String, phone: String, password: String): Resource<Unit> {
        discardUnverifiedAccount()
        pending = PendingSignUp(fullName.trim(), email.trim().lowercase(), phone.trim(), password)
        return Resource.Success(Unit)
    }

    override suspend fun createPendingAccount(): Resource<Unit> {
        val p = pending ?: return Resource.Error("Your sign-up session expired. Please start again.")
        return try {
            val user = auth.createUserWithEmailAndPassword(p.email, p.password).await().user
                ?: return Resource.Error("Could not create the account")
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(p.fullName).build()).await()
            // Atomic seed of profile + starter accounts/bills/offers (security rules limit this to the owner).
            withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                db.getReference("users/${user.uid}").updateChildren(UserSeeder.build(p.fullName, p.email, p.phone)).await()
            }
            if (REQUIRE_EMAIL_VERIFICATION) user.sendEmailVerification().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun resendVerification(): Resource<Unit> {
        return try {
            val user = auth.currentUser ?: return Resource.Error("Please sign up again")
            user.sendEmailVerification().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun checkEmailVerified(force: Boolean): Resource<Boolean> {
        return try {
            val user = auth.currentUser ?: return Resource.Error("Please sign up again")
            user.reload().await()
            if (force && auth.currentUser?.isEmailVerified != true) {
                // Some devices keep a stale cached token; refresh it and look again.
                auth.currentUser?.getIdToken(true)?.await()
                auth.currentUser?.reload()?.await()
            }
            Resource.Success(auth.currentUser?.isEmailVerified == true)
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun refreshSession(): Boolean {
        val user = auth.currentUser ?: return false
        if (!isVerifiedOrDemo(user)) runCatching { user.reload().await() }
        return isLoggedIn
    }

    override suspend fun sendResetLink(email: String): Resource<Unit> {
        return try {
            auth.sendPasswordResetEmail(email.trim()).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            // Unknown e-mail addresses are reported as success so accounts can't be enumerated.
            if (e is com.google.firebase.auth.FirebaseAuthInvalidUserException) Resource.Success(Unit)
            else Resource.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun updateProfile(fullName: String, phone: String, address: String): Resource<User> {
        return try {
            val user = auth.currentUser ?: return Resource.Error("You are signed out")
            withTimeoutOrNull(WRITE_TIMEOUT_MS) {
                db.getReference("users/${user.uid}/profile").updateChildren(
                    mapOf("fullName" to fullName.trim(), "phone" to phone.trim(), "address" to address.trim())
                ).await()
            }
            runCatching { user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(fullName.trim()).build()).await() }
            Resource.Success(User(user.uid, fullName.trim(), user.email.orEmpty(), phone.trim(), address.trim()))
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun changePassword(oldPassword: String, newPassword: String): Resource<Unit> {
        return try {
            val user = auth.currentUser ?: return Resource.Error("You are signed out")
            if (DemoAccount.isDemoEmail(user.email)) return Resource.Error("The shared demo account's password can't be changed")
            if (oldPassword == newPassword) return Resource.Error("New password must be different")
            user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), oldPassword)).await()
            user.updatePassword(newPassword).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    override suspend fun deleteAccount(password: String): Resource<Unit> {
        return try {
            val user = auth.currentUser ?: return Resource.Error("You are signed out")
            if (DemoAccount.isDemoEmail(user.email)) return Resource.Error("The shared demo account can't be deleted")
            user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), password)).await()
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { db.getReference("users/${user.uid}").removeValue().await() }
            user.delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e.toFriendlyMessage())
        }
    }

    override fun logout() { auth.signOut() }

    /** Removes a half-finished (unverified) sign-up so the user can retry with another address. */
    private suspend fun discardUnverifiedAccount() {
        val user = auth.currentUser ?: return
        if (isVerifiedOrDemo(user)) return
        runCatching {
            withTimeoutOrNull(WRITE_TIMEOUT_MS) { db.getReference("users/${user.uid}").removeValue().await() }
            user.delete().await()
        }.onFailure { auth.signOut() }
    }

    private fun isVerifiedOrDemo(user: FirebaseUser): Boolean =
        !REQUIRE_EMAIL_VERIFICATION || user.isEmailVerified || DemoAccount.isDemoEmail(user.email)

    private suspend fun createDemoAccount(): FirebaseUser {
        val user = try {
            auth.createUserWithEmailAndPassword(DemoAccount.EMAIL, DemoAccount.PASSWORD).await().user
        } catch (e: FirebaseAuthUserCollisionException) {
            throw IllegalStateException("The demo account exists with a different password. Delete it in Firebase Authentication to recreate it.")
        } ?: throw IllegalStateException("Could not create the demo account")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(DemoAccount.NAME).build()).await()
        withTimeoutOrNull(WRITE_TIMEOUT_MS) {
            db.getReference("users/${user.uid}")
                .updateChildren(UserSeeder.build(DemoAccount.NAME, DemoAccount.EMAIL, DemoAccount.PHONE, demo = true)).await()
        }
        return user
    }

    private companion object {
        /** Set to false to let users in without confirming their e-mail (handy while testing). */
        const val REQUIRE_EMAIL_VERIFICATION = true
        const val WRITE_TIMEOUT_MS = 6_000L
    }
}
