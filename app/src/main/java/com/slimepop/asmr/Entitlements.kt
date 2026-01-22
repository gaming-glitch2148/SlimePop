package com.slimepop.asmr

data class Entitlements(
    val adsRemoved: Boolean,
    val ownedProducts: Set<String>,
    val ownedContent: Set<String>
) {
    fun ownsSkin(id: String) = ownedContent.contains(id)
    fun ownsSound(id: String) = ownedContent.contains(id)
}

object EntitlementResolver {
    fun resolveFromOwnedProducts(owned: Set<String>): Entitlements {
        val adsRemoved = owned.contains(Catalog.REMOVE_ADS)

        val ownedContent = mutableSetOf<String>()

        ownedContent.add("skin_001")
        ownedContent.add("sound_001")

        owned.filter { it.startsWith("skin_") || it.startsWith("sound_") }
            .forEach { ownedContent.add(it) }

        owned.filter { it.startsWith("bundle_") }
            .forEach { b -> ownedContent.addAll(BundleGrants.grantsFor(b)) }

        return Entitlements(
            adsRemoved = adsRemoved,
            ownedProducts = owned,
            ownedContent = ownedContent
        )
    }

    fun ownedSetFromCsv(csv: String): Set<String> =
        csv.split(",").mapNotNull { it.trim().takeIf { t -> t.isNotEmpty() } }.toSet()
}