import HashiyaNetwork
import Testing

struct RebuildAbstractTests {
    @Test func ordersWordsByPosition() {
        #expect(rebuildAbstract(["world": [1], "Hello": [0]]) == "Hello world")
    }

    @Test func repeatsWordsAtEveryPosition() {
        #expect(rebuildAbstract(["the": [0, 3], "cat": [1], "saw": [2], "dog": [4]]) == "the cat saw the dog")
    }

    @Test func missingOrEmptyIsNil() {
        #expect(rebuildAbstract(nil) == nil)
        #expect(rebuildAbstract([:]) == nil)
    }

    @Test func blankTextIsNil() {
        #expect(rebuildAbstract([" ": [0], "": [1]]) == nil)
        #expect(rebuildAbstract(["word": []]) == nil)
    }
}
