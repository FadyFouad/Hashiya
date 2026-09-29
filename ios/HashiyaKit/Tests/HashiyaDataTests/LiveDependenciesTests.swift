import HashiyaData
import Testing

struct LiveDependenciesTests {
    @Test func readsTheBuiltInKey() {
        #expect(LiveDependencies.builtInAPIKey(from: "abc123") == "abc123")
        #expect(LiveDependencies.builtInAPIKey(from: " abc123 ") == "abc123")
    }

    @Test func missingEmptyOrUnexpandedMeansNoKey() {
        #expect(LiveDependencies.builtInAPIKey(from: nil) == nil)
        #expect(LiveDependencies.builtInAPIKey(from: "") == nil)
        #expect(LiveDependencies.builtInAPIKey(from: "   ") == nil)
        #expect(LiveDependencies.builtInAPIKey(from: "$(OPENALEX_API_KEY)") == nil)
        #expect(LiveDependencies.builtInAPIKey(from: 42) == nil)
    }
}
