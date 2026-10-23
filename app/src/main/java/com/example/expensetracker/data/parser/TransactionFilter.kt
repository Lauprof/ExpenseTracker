package com.example.expensetracker.data.parser

object TransactionFilter {
    fun isNonTransactionMessage(body: String): Boolean {
        val lower = body.lowercase()

        // Check for marketing, promotional pitches, insurance ads, loan promos, and spam
        val promoKeywords = listOf(
            "dear client", "dear customer, what if", "what if", "would you go for", "solution for you",
            "from as little as", "register here", "click here", "tinyurl", "bit.ly",
            "dial *", "sms stop", "opt out", "win ", "jackpot", "congratulations",
            "qualify for a loan", "loan limit", "loan of up to", "get a loan", "apply now",
            "medical cover", "insurance", "bima ya mwananchi"
        )
        if (promoKeywords.any { lower.contains(it) }) return true

        val hasActionKeyword = lower.contains("paid to") || lower.contains("sent to") ||
                lower.contains("transferred") || lower.contains("debited") || lower.contains("credited") ||
                lower.contains("bought") || lower.contains("withdraw") || lower.contains("deposited") ||
                lower.contains("you have received") || lower.contains("received") || lower.contains("give ksh") ||
                lower.contains("purchase") || lower.contains("payment of") || lower.contains("confirmed") ||
                lower.contains("reversal of") || lower.contains("charged") || lower.contains("fee")

        // If it mentions balance, statement, or account summary without any transaction action keyword, it's not a transaction
        val isNonTransaction = (lower.contains("balance") || lower.contains("statement") || lower.contains("bal")) && !hasActionKeyword

        return isNonTransaction
    }
}
