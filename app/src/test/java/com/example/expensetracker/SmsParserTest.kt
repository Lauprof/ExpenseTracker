package com.example.expensetracker

import com.example.expensetracker.data.local.SpecialCategories
import com.example.expensetracker.data.parser.RegexSmsParser
import com.example.expensetracker.data.sms.RawSmsMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    private val parser = RegexSmsParser()

    @Test
    fun parseMpesaPaybill_extractsCorrectDetails() {
        val message = RawSmsMessage(
            id = "1",
            sender = "MPESA",
            body = "SAB1234567 Confirmed. Ksh1,500.00 paid to KPLC PREPAID on 10/2/25 at 2:30 PM. New M-PESA balance is Ksh4,200.00.",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNotNull(result)
        assertEquals("SAB1234567", result?.transactionCode)
        assertEquals(1500.0, result?.amount ?: 0.0, 0.01)
        assertEquals("EXPENSE", result?.type)
        assertEquals("Utilities & Bills", result?.category)
        assertEquals("KPLC PREPAID", result?.recipient)
    }

    @Test
    fun parseMpesaSendMoney_extractsCorrectDetails() {
        val message = RawSmsMessage(
            id = "2",
            sender = "MPESA",
            body = "SAB7654321 Confirmed. Ksh2,500.00 sent to JOHN DOE 0712345678 on 11/2/25 at 4:15 PM.",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNotNull(result)
        assertEquals("SAB7654321", result?.transactionCode)
        assertEquals(2500.0, result?.amount ?: 0.0, 0.01)
        assertEquals("EXPENSE", result?.type)
        assertEquals("Transfers & Out", result?.category)
        assertEquals("JOHN DOE 0712345678", result?.recipient)
    }

    @Test
    fun parseMpesaSupermarket_extractsShoppingCategory() {
        val message = RawSmsMessage(
            id = "3",
            sender = "MPESA",
            body = "SAB9876543 Confirmed. Ksh3,200.00 paid to CARREFOUR SUPERMARKET on 12/2/25.",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNotNull(result)
        assertEquals("SAB9876543", result?.transactionCode)
        assertEquals(3200.0, result?.amount ?: 0.0, 0.01)
        assertEquals("Shopping", result?.category)
        assertEquals("CARREFOUR SUPERMARKET", result?.recipient)
    }

    @Test
    fun parseMpesaIncome_extractsIncomeCategoryAndType() {
        val message = RawSmsMessage(
            id = "4",
            sender = "MPESA",
            body = "SAB3344556 Confirmed. You have received Ksh5,000.00 from JANE SMITH on 12/2/25.",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNotNull(result)
        assertEquals("SAB3344556", result?.transactionCode)
        assertEquals(5000.0, result?.amount ?: 0.0, 0.01)
        assertEquals("INCOME", result?.type)
        assertEquals("Income", result?.category)
        assertEquals("JANE SMITH", result?.recipient)
    }

    @Test
    fun parseMmfDeposit_categorizesAsInvestmentsAndTransfer() {
        val message = RawSmsMessage(
            id = "5",
            sender = "MPESA",
            body = "SAB8899001 Confirmed. Ksh10,000.00 paid to SANLAM MONEY MARKET FUND on 12/2/25 at 10:00 AM.",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNotNull(result)
        assertEquals("SAB8899001", result?.transactionCode)
        assertEquals(10000.0, result?.amount ?: 0.0, 0.01)
        assertEquals("TRANSFER", result?.type)
        assertEquals(SpecialCategories.INVESTMENTS, result?.category)
    }

    @Test
    fun parseBettingPlatform_categorizesAsExpenseAndEntertainment() {
        val message = RawSmsMessage(
            id = "10",
            sender = "MPESA",
            body = "SAB5566778 Confirmed. Ksh500.00 paid to SPORTPESA on 12/2/25 at 8:00 PM.",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNotNull(result)
        assertEquals("SAB5566778", result?.transactionCode)
        assertEquals(500.0, result?.amount ?: 0.0, 0.01)
        assertEquals("EXPENSE", result?.type)
        assertEquals("Entertainment", result?.category)
    }

    @Test
    fun parseBankToMpesaSelfTransfer_categorizesAsTransferAndExtractsRecipient() {
        val message = RawSmsMessage(
            id = "11",
            sender = "BANK",
            body = "You have successfully transferred KES 3,000.00 to MPESA for 254759924783 - LAWRENCE MBURIA KINYUA. MPESA REF: UJKNVBEZV1. Queries 0711068888. Cost: KES 0.00",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNotNull(result)
        assertEquals(3000.0, result?.amount ?: 0.0, 0.01)
        assertEquals("TRANSFER", result?.type)
        assertEquals(SpecialCategories.BANK_TRANSFER, result?.category)
        assertEquals("254759924783 - LAWRENCE MBURIA KINYUA", result?.recipient)
    }

    @Test
    fun parseZiidiMmfWithdrawalToMpesa_categorizesAsIncome() {
        val message = RawSmsMessage(
            id = "8",
            sender = "MPESA",
            body = "SAB9900112 Confirmed. You have received Ksh8,500.00 from ZIIDI MMF on 12/2/25 at 11:30 AM.",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNotNull(result)
        assertEquals("SAB9900112", result?.transactionCode)
        assertEquals(8500.0, result?.amount ?: 0.0, 0.01)
        assertEquals("INCOME", result?.type)
        assertEquals("Income", result?.category)
    }

    @Test
    fun parseBankTransferToMpesa_categorizesAsIncome() {
        val message = RawSmsMessage(
            id = "9",
            sender = "MPESA",
            body = "SAB4433221 Confirmed. You have received Ksh15,000.00 from EQUITY BANK on 12/2/25 at 2:00 PM.",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNotNull(result)
        assertEquals("SAB4433221", result?.transactionCode)
        assertEquals(15000.0, result?.amount ?: 0.0, 0.01)
        assertEquals("INCOME", result?.type)
        assertEquals("Income", result?.category)
    }

    @Test
    fun parseNonTransactionBankSms_returnsNull() {
        val message = RawSmsMessage(
            id = "6",
            sender = "EQUITY",
            body = "Dear Customer, your total account balance as at 12/02/2025 is KES 2,500,000.00. Thank you.",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNull(result)
    }

    @Test
    fun parsePromotionalMedicalCoverSms_returnsNull() {
        val message = RawSmsMessage(
            id = "12",
            sender = "PROMO",
            body = "Dear Client, what if there is a medical cover that allows you to enjoy comprehensive, affordable medical care and access to a wide panel of major hospitals & specialists, would you go for it? If YES, Bima Ya Mwananchi is the solution for you, from as little as KES 4,600. Please register here to find out more. https://tinyurl.com/38wfj79w STOP*456*9*5#",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNull(result)
    }

    @Test
    fun parseBankTransfer_ignoresBalanceAmountAndExtractsTransferAmount() {
        val message = RawSmsMessage(
            id = "7",
            sender = "EQUITY",
            body = "Equity Bank: Ksh5,000.00 transferred to 254712345678 - JOHN DOE. Available balance is Ksh1,500,000.00.",
            timestamp = System.currentTimeMillis()
        )

        val result = parser.parse(message)

        assertNotNull(result)
        assertEquals(5000.0, result?.amount ?: 0.0, 0.01)
    }

    @Test
    fun parseSmsWithoutTxCode_usesDeterministicTxCode() {
        val msg1 = RawSmsMessage(
            id = "100",
            sender = "EQUITY",
            body = "Ksh2,000.00 paid to QUICKMART on 12/2/25.",
            timestamp = 1700000000000L
        )
        // Restored SMS gets a new system ID (e.g. 500)
        val msg2 = RawSmsMessage(
            id = "500",
            sender = "EQUITY",
            body = "Ksh2,000.00 paid to QUICKMART on 12/2/25.",
            timestamp = 1700000000000L
        )

        val res1 = parser.parse(msg1)
        val res2 = parser.parse(msg2)

        assertNotNull(res1)
        assertNotNull(res2)
        assertEquals(res1?.transactionCode, res2?.transactionCode)
    }
}
