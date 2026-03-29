import Foundation
import CoreText

// MARK: - CoreText Measurer

final class CoreTextMeasurer: TextMeasurer {
    private let ctFont: CTFont

    init(font: FontSpec) {
        self.ctFont = CTFontCreateWithName(font.name as CFString, font.size, nil)
    }

    func measureText(_ text: String) -> Double {
        let cfStr = text as CFString
        let range = CFRangeMake(0, CFStringGetLength(cfStr))
        let attrStr = CFAttributedStringCreateMutable(kCFAllocatorDefault, 0)!
        CFAttributedStringReplaceString(attrStr, CFRangeMake(0, 0), cfStr)
        CFAttributedStringSetAttribute(attrStr, range, kCTFontAttributeName, ctFont)
        let line = CTLineCreateWithAttributedString(attrStr)
        return CTLineGetTypographicBounds(line, nil, nil, nil)
    }
}

// MARK: - Measurement Cache (thread-safe)

final class MeasurementCache {
    private var caches: [FontSpec: [String: SegmentMetrics]] = [:]
    private let lock = NSLock()

    func getMetrics(segment: String, font: FontSpec, measurer: TextMeasurer) -> SegmentMetrics {
        lock.lock()
        defer { lock.unlock() }

        if let metrics = caches[font]?[segment] {
            return metrics
        }
        let width = measurer.measureText(segment)
        let metrics = SegmentMetrics(width: width, containsCJK: isCJK(segment))
        caches[font, default: [:]][segment] = metrics
        return metrics
    }

    func getGraphemeWidths(segment: String, font: FontSpec, measurer: TextMeasurer) -> [Double]? {
        lock.lock()
        defer { lock.unlock() }

        if let gw = caches[font]?[segment]?.graphemeWidths {
            return gw
        }

        let graphemes = segmentGraphemes(segment)
        guard graphemes.count > 1 else {
            ensureEntry(segment: segment, font: font, measurer: measurer)
            caches[font]![segment]!.graphemeWidths = nil
            return nil
        }

        let widths = graphemes.map { g -> Double in
            // getMetrics acquires lock, so call the unlocked version
            ensureEntry(segment: g, font: font, measurer: measurer)
            return caches[font]![g]!.width
        }
        ensureEntry(segment: segment, font: font, measurer: measurer)
        caches[font]![segment]!.graphemeWidths = widths
        return widths
    }

    func getGraphemePrefixWidths(segment: String, font: FontSpec, measurer: TextMeasurer) -> [Double]? {
        lock.lock()
        defer { lock.unlock() }

        if let pw = caches[font]?[segment]?.graphemePrefixWidths {
            return pw
        }

        let graphemes = segmentGraphemes(segment)
        guard graphemes.count > 1 else {
            ensureEntry(segment: segment, font: font, measurer: measurer)
            caches[font]![segment]!.graphemePrefixWidths = nil
            return nil
        }

        var prefixWidths: [Double] = []
        var prefix = ""
        for g in graphemes {
            prefix += g
            ensureEntry(segment: prefix, font: font, measurer: measurer)
            prefixWidths.append(caches[font]![prefix]!.width)
        }
        ensureEntry(segment: segment, font: font, measurer: measurer)
        caches[font]![segment]!.graphemePrefixWidths = prefixWidths
        return prefixWidths
    }

    func clear() {
        lock.lock()
        defer { lock.unlock() }
        caches.removeAll()
    }

    // Must be called while lock is held
    private func ensureEntry(segment: String, font: FontSpec, measurer: TextMeasurer) {
        if caches[font]?[segment] != nil { return }
        let width = measurer.measureText(segment)
        caches[font, default: [:]][segment] = SegmentMetrics(width: width, containsCJK: isCJK(segment))
    }
}

// Shared instance
let sharedMeasurementCache = MeasurementCache()
