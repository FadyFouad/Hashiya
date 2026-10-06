/// Plain text, HTML for the rich pasteboard, and RTF for exported reference lists.
public enum Rendering {
    public static func plain(_ citation: StyledCitation) -> String {
        citation.runs.map(\.text).joined()
    }

    public static func html(_ citation: StyledCitation) -> String {
        citation.runs.map { run in
            let escaped = escapeHTML(run.text)
            return run.italic ? "<i>\(escaped)</i>" : escaped
        }.joined()
    }

    /// One paragraph per entry; APA entries get a 0.5-inch hanging indent and double spacing. Readable by Word and Pages.
    public static func rtf(_ entries: [StyledCitation], hangingIndent: Bool) -> String {
        var out = "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n"
        for entry in entries {
            out += hangingIndent ? "{\\pard\\fi-720\\li720\\sl480\\slmult1 " : "{\\pard "
            for run in entry.runs {
                out += run.italic ? "{\\i \(escapeRTF(run.text))}" : escapeRTF(run.text)
            }
            out += "\\par}\n"
        }
        return out + "}"
    }

    private static func escapeHTML(_ text: String) -> String {
        var out = ""
        for c in text {
            switch c {
            case "&": out += "&amp;"
            case "<": out += "&lt;"
            case ">": out += "&gt;"
            case "\"": out += "&quot;"
            default: out.append(c)
            }
        }
        return out
    }

    /// RTF is 7-bit: everything above U+007F is written as \uN? per UTF-16 unit (N signed 16-bit).
    private static func escapeRTF(_ text: String) -> String {
        var out = ""
        for unit in text.utf16 {
            switch unit {
            case 0x5C, 0x7B, 0x7D: out += "\\" + String(UnicodeScalar(UInt8(unit)))
            case 0x0A, 0x0D, 0x09: out += " "
            case 0x80...: out += "\\u\(Int16(bitPattern: unit))?"
            default: out += String(UnicodeScalar(UInt8(unit)))
            }
        }
        return out
    }
}
