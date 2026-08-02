package com.racunko.app.parser.tools

import com.racunko.app.parser.AccountChecksum

/**
 * Tiny CLI used by contributors to mint a synthetic-but-checksum-valid account
 * for fixtures (CONTRIBUTING.md / ADDING_A_TEMPLATE.md):
 *
 *   ./gradlew :parser-core:run --args="mkaccount 190 99870"   # → 190000000009987010
 */
fun main(args: Array<String>) {
    if (args.size >= 3 && args[0] == "mkaccount") {
        val bank = args[1]
        val body = args[2]
        val account = AccountChecksum.build(bank, body)
        require(AccountChecksum.isValid(account)) { "internal error: produced invalid account" }
        println(account)
    } else {
        println("usage: mkaccount <bank3> <body>   e.g. mkaccount 190 99870")
    }
}
