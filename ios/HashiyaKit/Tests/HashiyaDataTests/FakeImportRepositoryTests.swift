import Foundation
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

struct FakeImportRepositoryTests {
    @Test func itBehavesLikeTheRealOneForTheScreen() async {
        let repository = FakeImportRepository()
        let started = await repository.start(fileName: "thesis.bib", data: Data("x".utf8))
        #expect(started.state == .analyzing)
        #expect(started.collectionName == "thesis")
        #expect(await repository.start(fileName: "other.bib", data: Data()) == started)

        var ready = started
        ready.state = .ready
        repository.setJob(ready)
        await repository.confirm(jobID: started.id, addToCollection: false)
        var iterator = repository.observeCurrentJob().makeAsyncIterator()
        let importing = await iterator.next()
        #expect(importing??.state == .importing)
        #expect(importing??.addToCollection == false)
        #expect(repository.confirms == [.init(jobID: started.id, addToCollection: false)])

        await repository.cancel(jobID: started.id)
        await repository.dismiss(jobID: started.id)
        var after = repository.observeCurrentJob().makeAsyncIterator()
        #expect(await after.next() == .some(nil))
    }
}
