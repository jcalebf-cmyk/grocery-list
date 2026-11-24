package com.grocerylist.app.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInStateTest {
    private val creating = SignInState(creatingAccount = true, name = "Josh", email = "j@example.com", password = "secret12")

    @Test fun canCreateOnlyWhenPasswordsMatch() {
        assertFalse(creating.canSubmit) // confirmation empty
        assertFalse(creating.copy(confirmPassword = "secret13").canSubmit)
        assertTrue(creating.copy(confirmPassword = "secret12").canSubmit)
    }

    @Test fun noMismatchErrorWhileStillTypingTheConfirmation() {
        assertFalse(creating.copy(confirmPassword = "sec").showMismatch)
    }

    @Test fun mismatchErrorOnceItCantMatch() {
        assertTrue(creating.copy(confirmPassword = "sex").showMismatch) // diverged
        assertTrue(creating.copy(confirmPassword = "secret13").showMismatch) // same length, different
        assertTrue(creating.copy(confirmPassword = "secret123").showMismatch) // longer
    }

    @Test fun mismatchErrorAfterSubmitEvenIfPartial() {
        assertTrue(creating.copy(confirmPassword = "sec", submittedMismatch = true).showMismatch)
    }

    @Test fun shortPasswordBlocksCreation() {
        val short = creating.copy(password = "1234567", confirmPassword = "1234567")
        assertTrue(short.passwordTooShort)
        assertFalse(short.canSubmit)
        val enough = creating.copy(password = "12345678", confirmPassword = "12345678")
        assertFalse(enough.passwordTooShort)
        assertTrue(enough.canSubmit)
    }

    @Test fun accountsFromBeforeTheLongerMinimumCanStillSignIn() {
        val signingIn = SignInState(email = "j@example.com", password = "secret")
        assertTrue(signingIn.canSubmit)
        assertFalse(signingIn.passwordTooShort)
    }

    @Test fun signInIgnoresConfirmation() {
        val signingIn = SignInState(email = "j@example.com", password = "abc")
        assertTrue(signingIn.canSubmit)
        assertFalse(signingIn.passwordTooShort)
        assertFalse(signingIn.showMismatch)
    }
}
