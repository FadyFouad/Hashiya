import HashiyaData
import HashiyaTesting
import Testing

struct FakeCitationRepositoryTests {
    @Test @MainActor func recordsCallsAndAnswersAsScripted() async throws {
        let fake = FakeCitationRepository(export: CitationResult(text: "x", complete: false))

        #expect(try await fake.export(collectionID: 3) == CitationResult(text: "x", complete: false))
        #expect(try await fake.entry(openAlexID: "W1")?.complete == true)
        #expect(fake.exportCalls == [3])
        #expect(fake.entryCalls == ["W1"])

        fake.setFail(true)
        await #expect(throws: FakeCitationRepository.Failure.self) { try await fake.export(collectionID: nil) }
    }

    @Test @MainActor func aHeldExportWaitsForRelease() async throws {
        let fake = FakeCitationRepository()
        fake.holdExports()
        let export = Task { try await fake.export(collectionID: nil) }
        #expect(await eventually { fake.heldExports == 1 })

        fake.releaseExports()
        #expect(try await export.value.complete)
    }
}
