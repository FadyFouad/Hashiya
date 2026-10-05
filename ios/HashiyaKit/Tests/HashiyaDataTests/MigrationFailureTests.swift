import Foundation
import HashiyaData
import HashiyaDatabase
import Testing

struct MigrationFailureTests {
    @Test func aMigrationErrorIsAMigrationFailure() {
        let error: any Error = HashiyaDatabase.MigrationError(underlying: CocoaError(.fileReadUnknown))
        #expect(error.isMigrationFailure)
    }

    @Test func otherErrorsAreNot() {
        #expect(!(HashiyaDatabase.OpenError.coordinationFailed as any Error).isMigrationFailure)
        #expect(!(CocoaError(.fileReadUnknown) as any Error).isMigrationFailure)
    }
}
