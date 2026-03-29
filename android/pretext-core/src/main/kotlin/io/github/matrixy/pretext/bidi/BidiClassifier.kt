package io.github.matrixy.pretext.bidi

/**
 * Simplified bidi metadata helper for the rich prepareWithSegments() path.
 * Ported from bidi.ts. Classifies characters into bidi types, computes
 * embedding levels, and maps them onto prepared segments for custom rendering.
 */

enum class BidiType {
    L, R, AL, AN, EN, ES, ET, CS, ON, BN, B, S, WS, NSM
}

// 256 entries for codepoints 0x00..0xFF
val baseTypes: Array<BidiType> = arrayOf(
    BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN,
    BidiType.BN, BidiType.S, BidiType.B, BidiType.S, BidiType.WS, BidiType.B, BidiType.BN, BidiType.BN,
    BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN,
    BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.B, BidiType.B, BidiType.B, BidiType.S,
    BidiType.WS, BidiType.ON, BidiType.ON, BidiType.ET, BidiType.ET, BidiType.ET, BidiType.ON, BidiType.ON,
    BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.CS, BidiType.ON, BidiType.CS, BidiType.ON,
    BidiType.EN, BidiType.EN, BidiType.EN, BidiType.EN, BidiType.EN, BidiType.EN, BidiType.EN, BidiType.EN,
    BidiType.EN, BidiType.EN, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON,
    BidiType.ON, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON,
    BidiType.ON, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.BN,
    BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.B, BidiType.BN, BidiType.BN,
    BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN,
    BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN,
    BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN, BidiType.BN,
    BidiType.CS, BidiType.ON, BidiType.ET, BidiType.ET, BidiType.ET, BidiType.ET, BidiType.ON, BidiType.ON,
    BidiType.ON, BidiType.ON, BidiType.L, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON,
    BidiType.ET, BidiType.ET, BidiType.EN, BidiType.EN, BidiType.ON, BidiType.L, BidiType.ON, BidiType.ON,
    BidiType.ON, BidiType.EN, BidiType.L, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON, BidiType.ON,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.ON,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.ON,
    BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L, BidiType.L
)

// 256 entries for Arabic block 0x0600..0x06FF
val arabicTypes: Array<BidiType> = arrayOf(
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.CS, BidiType.AL, BidiType.ON, BidiType.ON,
    BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM,
    BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM,
    BidiType.NSM, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AN, BidiType.AN, BidiType.AN, BidiType.AN, BidiType.AN, BidiType.AN, BidiType.AN, BidiType.AN,
    BidiType.AN, BidiType.AN, BidiType.ET, BidiType.AN, BidiType.AN, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.NSM, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM,
    BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM,
    BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.NSM, BidiType.ON, BidiType.NSM, BidiType.NSM, BidiType.NSM,
    BidiType.NSM, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL,
    BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL, BidiType.AL
)

fun classifyChar(charCode: Int): BidiType {
    if (charCode <= 0x00FF) return baseTypes[charCode]
    if (charCode in 0x0590..0x05F4) return BidiType.R
    if (charCode in 0x0600..0x06FF) return arabicTypes[charCode and 0xFF]
    if (charCode in 0x0700..0x08AC) return BidiType.AL
    return BidiType.L
}

fun computeBidiLevels(str: String): ByteArray? {
    val len = str.length
    if (len == 0) return null

    val types = Array(len) { BidiType.L }
    var numBidi = 0

    for (i in 0 until len) {
        val t = classifyChar(str[i].code)
        if (t == BidiType.R || t == BidiType.AL || t == BidiType.AN) numBidi++
        types[i] = t
    }

    if (numBidi == 0) return null

    val startLevel = if (numBidi.toFloat() / len < 0.3f) 0 else 1
    val levels = ByteArray(len) { startLevel.toByte() }

    val e: BidiType = if ((startLevel and 1) != 0) BidiType.R else BidiType.L
    val sor = e

    // W1: NSM
    var lastType: BidiType = sor
    for (i in 0 until len) {
        if (types[i] == BidiType.NSM) types[i] = lastType
        else lastType = types[i]
    }

    // W2: EN after AL -> AN
    lastType = sor
    for (i in 0 until len) {
        val t = types[i]
        if (t == BidiType.EN) types[i] = if (lastType == BidiType.AL) BidiType.AN else BidiType.EN
        else if (t == BidiType.R || t == BidiType.L || t == BidiType.AL) lastType = t
    }

    // W3: AL -> R
    for (i in 0 until len) {
        if (types[i] == BidiType.AL) types[i] = BidiType.R
    }

    // W4: ES between EN -> EN; CS between same -> same
    for (i in 1 until len - 1) {
        if (types[i] == BidiType.ES && types[i - 1] == BidiType.EN && types[i + 1] == BidiType.EN) {
            types[i] = BidiType.EN
        }
        if (types[i] == BidiType.CS &&
            (types[i - 1] == BidiType.EN || types[i - 1] == BidiType.AN) &&
            types[i + 1] == types[i - 1]
        ) {
            types[i] = types[i - 1]
        }
    }

    // W5: ET adjacent to EN -> EN
    for (i in 0 until len) {
        if (types[i] != BidiType.EN) continue
        var j = i - 1
        while (j >= 0 && types[j] == BidiType.ET) { types[j] = BidiType.EN; j-- }
        j = i + 1
        while (j < len && types[j] == BidiType.ET) { types[j] = BidiType.EN; j++ }
    }

    // W6: remaining separators/terminators -> ON
    for (i in 0 until len) {
        val t = types[i]
        if (t == BidiType.WS || t == BidiType.ES || t == BidiType.ET || t == BidiType.CS) types[i] = BidiType.ON
    }

    // W7: EN after L -> L
    lastType = sor
    for (i in 0 until len) {
        val t = types[i]
        if (t == BidiType.EN) types[i] = if (lastType == BidiType.L) BidiType.L else BidiType.EN
        else if (t == BidiType.R || t == BidiType.L) lastType = t
    }

    // N1-N2: resolve neutrals
    var i = 0
    while (i < len) {
        if (types[i] != BidiType.ON) { i++; continue }
        var end = i + 1
        while (end < len && types[end] == BidiType.ON) end++
        val before: BidiType = if (i > 0) types[i - 1] else sor
        val after: BidiType = if (end < len) types[end] else sor
        val bDir: BidiType = if (before != BidiType.L) BidiType.R else BidiType.L
        val aDir: BidiType = if (after != BidiType.L) BidiType.R else BidiType.L
        if (bDir == aDir) {
            for (j in i until end) types[j] = bDir
        }
        i = end
    }
    for (idx in 0 until len) {
        if (types[idx] == BidiType.ON) types[idx] = e
    }

    // I1-I2: resolve implicit levels
    for (idx in 0 until len) {
        val t = types[idx]
        if ((levels[idx].toInt() and 1) == 0) {
            if (t == BidiType.R) levels[idx]++
            else if (t == BidiType.AN || t == BidiType.EN) levels[idx] = (levels[idx] + 2).toByte()
        } else if (t == BidiType.L || t == BidiType.AN || t == BidiType.EN) {
            levels[idx]++
        }
    }

    return levels
}

fun computeSegmentLevels(normalized: String, segStarts: IntArray): ByteArray? {
    val bidiLevels = computeBidiLevels(normalized) ?: return null

    val segLevels = ByteArray(segStarts.size)
    for (i in segStarts.indices) {
        segLevels[i] = bidiLevels[segStarts[i]]
    }
    return segLevels
}
