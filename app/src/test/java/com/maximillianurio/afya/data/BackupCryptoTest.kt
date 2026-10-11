package com.maximillianurio.afya.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCryptoTest {

    private val plain = """{"app":"afya","schema":4,"readings":[{"ts":1,"grams":123700.0}]}"""

    @Test
    fun aSealedBackupOpensWithItsPassphrase() {
        val sealed = BackupCrypto.seal(plain, "correct horse")
        assertTrue(BackupCrypto.isSealed(sealed))
        assertFalse(sealed.contains("123700"))
        assertEquals(plain, BackupCrypto.open(sealed, "correct horse"))
    }

    @Test(expected = BackupCrypto.WrongPassphrase::class)
    fun theWrongPassphraseIsRefused() {
        BackupCrypto.open(BackupCrypto.seal(plain, "correct horse"), "battery staple")
    }

    @Test
    fun aPlainBackupIsNotMistakenForASealedOne() {
        assertFalse(BackupCrypto.isSealed(plain))
        assertFalse(BackupCrypto.isSealed("not json at all"))
    }

    @Test
    fun twoSealsOfTheSameBackupDiffer() {
        // Fresh salt and nonce every time: two files never reveal that they hold the same history.
        val a = BackupCrypto.seal(plain, "pass")
        val b = BackupCrypto.seal(plain, "pass")
        assertTrue(a != b)
    }
}
