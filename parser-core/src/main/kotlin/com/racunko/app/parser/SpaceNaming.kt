package com.racunko.app.parser

/**
 * v1.5.2 Change B1: a sub-label bound to a specific space of an address —
 * `SG26-G1` for the garage with IDENT 0614276. Bound once (manually or via
 * „Zapamti za ovaj prostor"), applied automatically every month after.
 */
data class SpaceBinding(
    /** Canonical spaceId (leading zeros stripped) this sub-label belongs to. */
    val spaceId: String,
    /** The address label it extends (display/scoping; the spaceId is the key). */
    val addressLabel: String,
    /** Short filesystem-safe tag: G1, STAN, LOKAL. */
    val subLabel: String
)

/**
 * Multi-space naming (v1.5.2 Change B): address token `ADDRESS` or
 * `ADDRESS-SUB`, sub-label resolution from bindings, and the collision test
 * that replaces the silent `_2` suffix for bills.
 */
object SpaceNaming {

    /** Filesystem-safe sub-label: uppercased, alphanumeric only, short. */
    fun sanitizeSub(sub: String): String =
        sub.uppercase().filter { it.isLetterOrDigit() }.take(8)

    /** `SG26` or `SG26-G1`; an empty/blank sub keeps the plain label. */
    fun addressToken(label: String, subLabel: String?): String {
        if (label.isEmpty()) return label
        val sub = subLabel?.let { sanitizeSub(it) }.orEmpty()
        return if (sub.isEmpty()) label else "$label-$sub"
    }

    /**
     * The bound sub-label for a parsed bill's space, or null. Matching is by
     * canonical spaceId; the stored addressLabel must agree when both sides
     * carry one (guards against reusing a tag across different addresses).
     */
    fun subFor(spaceId: String?, addressLabel: String, bindings: List<SpaceBinding>): String? {
        val id = SpaceId.canonical(spaceId) ?: return null
        return bindings.firstOrNull { binding ->
            SpaceId.canonical(binding.spaceId) == id &&
                (binding.addressLabel.isEmpty() || addressLabel.isEmpty() ||
                    binding.addressLabel == addressLabel)
        }?.subLabel?.let { sanitizeSub(it) }?.takeIf { it.isNotEmpty() }
    }

    /**
     * Change B2: true when the finished name would shadow an EXISTING different
     * file — the caller must ask for a space tag instead of silently suffixing
     * `_2`. Reprocessing the same file (target == its current name) is not a
     * collision.
     */
    fun collides(base: String, ext: String, existingNames: Set<String>, currentName: String?): Boolean {
        val target = "$base$ext"
        return target != currentName && target in existingNames
    }
}
