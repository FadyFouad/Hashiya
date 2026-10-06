@testable import HashiyaCitation
import Testing

struct RenderingTests {
    private let citation = StyledCitation([Run("A & B <x> \"q\". "), Run("Journal", italic: true), Run(", 1.")])

    @Test func plainJoinsTheRuns() {
        #expect(Rendering.plain(citation) == "A & B <x> \"q\". Journal, 1.")
    }

    @Test func htmlEscapesAndItalicises() {
        #expect(Rendering.html(citation) == "A &amp; B &lt;x&gt; &quot;q&quot;. <i>Journal</i>, 1.")
    }

    @Test func rtfEscapesControlCharactersAndWritesUnicode() {
        let rtf = Rendering.rtf([StyledCitation([Run("a\\b{c}"), Run("ع😀", italic: true)])], hangingIndent: false)
        #expect(rtf ==
            "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n" +
            "{\\pard a\\\\b\\{c\\}{\\i \\u1593?\\u-10179?\\u-8704?}\\par}\n" +
            "}")
    }

    @Test func rtfHangingIndentIsOnlyForApa() {
        let one = [StyledCitation([Run("x")])]
        #expect(Rendering.rtf(one, hangingIndent: true).contains("{\\pard\\fi-720\\li720\\sl480\\slmult1 x\\par}"))
        let ieee = Rendering.rtf(one, hangingIndent: false)
        #expect(ieee.contains("{\\pard x\\par}"))
        #expect(!ieee.contains("\\sl"))
    }

    @Test func anEmptyListIsAValidEmptyDocument() {
        #expect(Rendering.rtf([], hangingIndent: true) == "{\\rtf1\\ansi\\deff0{\\fonttbl{\\f0 Times New Roman;}}\\f0\\fs24\n}")
    }

    @Test func lineBreaksAndTabsBecomeASingleSpace() {
        let rtf = Rendering.rtf([StyledCitation([Run("Deep\nLearning\tnow")])], hangingIndent: false)
        #expect(rtf.contains("{\\pard Deep Learning now\\par}"))
    }
}
