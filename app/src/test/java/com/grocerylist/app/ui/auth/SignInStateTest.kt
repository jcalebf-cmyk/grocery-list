package com.grocerylist.app.ui.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignInStateTest {
    private val creating = SignInState(creatingAccount = true, name = "Josh", email = "j@example.com", password = "secret1")

    @Test fun canCreateOnlyWhenPasswordsMatch() {
        assertFalse(creating.canSubmit) // confirmation empty
        assertFalse(creating.copy(confirmPassword = "secret2").canSubmit)
        assertTrue(creating.copy(confirmPassword = "secret1").canSubmit)
    }

    @Test fun noMismatchErrorWhileStillTypingTheConfirmation() {
        assertFalse(creating.copy(confirmPassword = "sec").showMismatch)
    }

    @Test fun mismatchErrorOnceItCantMatch() {
        assertTrue(creating.copy(confirmPassword = "sex").showMismatch) // diverged
        assertTrue(creating.copy(confirmPassword = "secret2").showMismatch) // same length, different
        assertTrue(creating.copy(confirmPassword = "secret12").showMismatch) // longer
    }

    @Test fun mismatchErrorAfterSubmitEvenIfPartial() {
        assertTrue(creating.copy(confirmPassword = "sec", submittedMismatch = true).showMismatch)
    }

    @Test fun shortPasswordBlocksCreation() {
        val short = creating.copy(password = "abc", confirmPassword = "abc")
        assertTrue(short.passwordTooShort)
        assertFalse(short.canSubmit)
    }

    @Test fun signInIgnoresConfirmation() {
        val signingIn = SignInState(email = "j@example.com", password = "abc")
        assertTrue(signingIn.canSubmit)
        assertFalse(signingIn.passwordTooShort)
        assertFalse(signingIn.showMismatch)
    }
}
