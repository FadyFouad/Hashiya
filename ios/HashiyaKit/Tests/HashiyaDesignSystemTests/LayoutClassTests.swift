import HashiyaDesignSystem
import Testing

/// The breakpoints match Android's: 600, 840 and 1200pt.
struct LayoutClassTests {
    @Test(arguments: [
        (320.0, LayoutClass.compact), (599.0, .compact),
        (600.0, .medium), (839.0, .medium),
        (840.0, .expanded), (1199.0, .expanded),
        (1200.0, .large), (1376.0, .large),
    ])
    func widthsMapToTheBreakpoints(width: Double, expected: LayoutClass) {
        #expect(LayoutClass(width: width) == expected)
    }

    @Test func classesAreOrdered() {
        #expect(LayoutClass.compact < .medium)
        #expect(LayoutClass.medium < .expanded)
        #expect(LayoutClass.expanded < .large)
    }
}
