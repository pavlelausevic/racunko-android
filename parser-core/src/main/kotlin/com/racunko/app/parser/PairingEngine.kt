package com.racunko.app.parser

/** A remembered, processed bill — the parser-layer view of a Room record. */
data class StoredBill(
    /** Primary record key: digits-only RO. */
    val roKey: String,
    /** RO without its 2-digit model prefix (e.g. 97040255500012 -> 040255500012). */
    val altKey: String,
    val provider: String,
    val address: String,
    val month: MonthYear?,
    val amount: Long?,
    /** IPS QR R field digits (18). */
    val recipientAccount: String,
    /** Final file name WITHOUT extension, e.g. sz_DS99_jun26_1070. */
    val name: String,
    val paired: Boolean
) {
    val keys: List<String> get() = listOf(roKey, altKey).filter { it.isNotEmpty() }.distinct()

    companion object {
        fun altKeyOf(roDigits: String): String =
            if (roDigits.length > 8) roDigits.substring(2) else roDigits
    }
}

sealed class PairResult {
    data class Matched(val bill: StoredBill, val layer: Int) : PairResult()
    data class Candidates(val bills: List<StoredBill>) : PairResult()
    data object None : PairResult()
}

/**
 * Layered confirmation pairing (§5.9):
 *  1. payment reference (poziv na broj): exact or suffix match against stored keys
 *  2. recipient account (18-digit normalized) + amount, unique unpaired hit
 *  3. manual (handled in the UI)
 */
object PairingEngine {

    fun pair(fields: ConfirmationFields, bills: List<StoredBill>): PairResult {
        // Layer 1 — prefer unpaired bills, then any
        for (preferUnpaired in listOf(true, false)) {
            val pool = if (preferUnpaired) bills.filter { !it.paired } else bills
            for (candidate in fields.referenceCandidates) {
                for (bill in pool) {
                    for (key in bill.keys) {
                        if (key.length < 8) continue
                        if (candidate == key || candidate.endsWith(key) || key.endsWith(candidate)) {
                            return PairResult.Matched(bill, 1)
                        }
                    }
                }
            }
        }

        // Layer 2 — recipient account + amount, unpaired only. When a bank
        // template identified THE transaction amount, match on it exclusively
        // (v1.2 Change 3) so balances/fees can't shortlist a different bill.
        val amounts = fields.templatedAmount?.let { setOf(it) } ?: fields.amounts
        val seen = mutableSetOf<String>()
        val hits = mutableListOf<StoredBill>()
        for (bill in bills) {
            if (bill.paired || bill.name in seen) continue
            if (bill.recipientAccount.length != 18) continue
            if (bill.recipientAccount in fields.accounts &&
                bill.amount != null && bill.amount in amounts
            ) {
                hits.add(bill)
                seen.add(bill.name)
            }
        }
        return when {
            hits.size == 1 -> PairResult.Matched(hits[0], 2)
            hits.size > 1 -> PairResult.Candidates(hits)
            else -> PairResult.None
        }
    }
}
