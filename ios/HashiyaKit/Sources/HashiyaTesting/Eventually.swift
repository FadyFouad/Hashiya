/// Waits (up to `timeout`) until `condition` holds; returns whether it did. For state that arrives
/// through an observation stream.
@MainActor
public func eventually(timeout: Duration = .seconds(3), _ condition: @MainActor () -> Bool) async -> Bool {
    let clock = ContinuousClock()
    let deadline = clock.now + timeout
    while !condition() {
        if clock.now >= deadline { return false }
        try? await Task.sleep(for: .milliseconds(2))
    }
    return true
}
