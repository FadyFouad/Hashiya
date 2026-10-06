@testable import HashiyaCitation
import Testing

struct PersonNameTests {
    @Test func familyIsTheLastWordAndInitialsTheRest() {
        #expect(personName("Ashish Vaswani") == PersonName(family: "Vaswani", initials: "A."))
        #expect(personName("Aidan N. Gomez") == PersonName(family: "Gomez", initials: "A. N."))
        #expect(personName("  Łukasz   Kaiser ") == PersonName(family: "Kaiser", initials: "Ł."))
    }

    @Test func hyphenatedGivenNamesKeepTheHyphen() {
        #expect(personName("Jean-Paul Sartre") == PersonName(family: "Sartre", initials: "J.-P."))
    }

    @Test func oneWordAndArabicNamesStayWhole() {
        #expect(personName("OpenAI") == PersonName(family: "OpenAI", initials: nil))
        #expect(personName("محمد عبد الله") == PersonName(family: "محمد عبد الله", initials: nil))
    }
}
