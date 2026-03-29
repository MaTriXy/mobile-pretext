import Foundation

// Simplified bidi metadata helper forked from pdf.js via Sebastian's text-layout.
// Classifies characters into bidi types, computes embedding levels, and maps
// them onto prepared segments for custom rendering.

enum BidiType: UInt8 {
    case L, R, AL, AN, EN, ES, ET, CS, ON, BN, B, S, WS, NSM
}

private let baseTypes: [BidiType] = [
    .BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.S,.B,.S,.WS,
    .B,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,
    .BN,.BN,.B,.B,.B,.S,.WS,.ON,.ON,.ET,.ET,.ET,.ON,
    .ON,.ON,.ON,.ON,.ON,.CS,.ON,.CS,.ON,.EN,.EN,.EN,
    .EN,.EN,.EN,.EN,.EN,.EN,.EN,.ON,.ON,.ON,.ON,.ON,
    .ON,.ON,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,
    .L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.ON,.ON,
    .ON,.ON,.ON,.ON,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,
    .L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,
    .L,.ON,.ON,.ON,.ON,.BN,.BN,.BN,.BN,.BN,.BN,.B,.BN,
    .BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,
    .BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,.BN,
    .BN,.CS,.ON,.ET,.ET,.ET,.ET,.ON,.ON,.ON,.ON,.L,.ON,
    .ON,.ON,.ON,.ON,.ET,.ET,.EN,.EN,.ON,.L,.ON,.ON,.ON,
    .EN,.L,.ON,.ON,.ON,.ON,.ON,.L,.L,.L,.L,.L,.L,.L,
    .L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,
    .L,.ON,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,
    .L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,.L,
    .L,.L,.L,.ON,.L,.L,.L,.L,.L,.L,.L,.L
]

private let arabicTypes: [BidiType] = [
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .CS,.AL,.ON,.ON,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,
    .NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AN,.AN,.AN,.AN,.AN,.AN,.AN,.AN,.AN,
    .AN,.ET,.AN,.AN,.AL,.AL,.AL,.NSM,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,
    .NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.NSM,.ON,.NSM,
    .NSM,.NSM,.NSM,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,
    .AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL,.AL
]

private func classifyChar(_ charCode: UInt16) -> BidiType {
    if charCode <= 0x00FF { return baseTypes[Int(charCode)] }
    if charCode >= 0x0590 && charCode <= 0x05F4 { return .R }
    if charCode >= 0x0600 && charCode <= 0x06FF { return arabicTypes[Int(charCode & 0xFF)] }
    if charCode >= 0x0700 && charCode <= 0x08AC { return .AL }
    return .L
}

func computeBidiLevels(_ str: String) -> [Int8]? {
    let utf16 = Array(str.utf16)
    let len = utf16.count
    if len == 0 { return nil }

    var types = [BidiType](repeating: .L, count: len)
    var numBidi = 0

    for i in 0..<len {
        let t = classifyChar(utf16[i])
        if t == .R || t == .AL || t == .AN { numBidi += 1 }
        types[i] = t
    }

    if numBidi == 0 { return nil }

    let startLevel: Int8 = Double(len) / Double(numBidi) < 0.3 ? 0 : 1
    var levels = [Int8](repeating: startLevel, count: len)

    let e: BidiType = (startLevel & 1) != 0 ? .R : .L
    let sor = e

    // W1-W7
    var lastType = sor
    for i in 0..<len {
        if types[i] == .NSM { types[i] = lastType }
        else { lastType = types[i] }
    }
    lastType = sor
    for i in 0..<len {
        let t = types[i]
        if t == .EN { types[i] = lastType == .AL ? .AN : .EN }
        else if t == .R || t == .L || t == .AL { lastType = t }
    }
    for i in 0..<len {
        if types[i] == .AL { types[i] = .R }
    }
    for i in 1..<(len - 1) {
        if types[i] == .ES && types[i - 1] == .EN && types[i + 1] == .EN {
            types[i] = .EN
        }
        if types[i] == .CS && (types[i - 1] == .EN || types[i - 1] == .AN) && types[i + 1] == types[i - 1] {
            types[i] = types[i - 1]
        }
    }
    for i in 0..<len {
        if types[i] != .EN { continue }
        var j = i - 1
        while j >= 0 && types[j] == .ET { types[j] = .EN; j -= 1 }
        j = i + 1
        while j < len && types[j] == .ET { types[j] = .EN; j += 1 }
    }
    for i in 0..<len {
        let t = types[i]
        if t == .WS || t == .ES || t == .ET || t == .CS { types[i] = .ON }
    }
    lastType = sor
    for i in 0..<len {
        let t = types[i]
        if t == .EN { types[i] = lastType == .L ? .L : .EN }
        else if t == .R || t == .L { lastType = t }
    }

    // N1-N2
    var i = 0
    while i < len {
        if types[i] != .ON { i += 1; continue }
        var end = i + 1
        while end < len && types[end] == .ON { end += 1 }
        let before: BidiType = i > 0 ? types[i - 1] : sor
        let after: BidiType = end < len ? types[end] : sor
        let bDir: BidiType = before != .L ? .R : .L
        let aDir: BidiType = after != .L ? .R : .L
        if bDir == aDir {
            for j in i..<end { types[j] = bDir }
        }
        i = end
    }
    for i in 0..<len {
        if types[i] == .ON { types[i] = e }
    }

    // I1-I2
    for i in 0..<len {
        let t = types[i]
        if (levels[i] & 1) == 0 {
            if t == .R { levels[i] += 1 }
            else if t == .AN || t == .EN { levels[i] += 2 }
        } else if t == .L || t == .AN || t == .EN {
            levels[i] += 1
        }
    }

    return levels
}

func computeSegmentLevels(normalized: String, segStarts: [Int]) -> [Int8]? {
    guard let bidiLevels = computeBidiLevels(normalized) else { return nil }

    var segLevels = [Int8](repeating: 0, count: segStarts.count)
    for i in 0..<segStarts.count {
        let charOffset = segStarts[i]
        if charOffset < bidiLevels.count {
            segLevels[i] = bidiLevels[charOffset]
        }
    }
    return segLevels
}
