import Foundation
import HashiyaModel
import HashiyaNetwork

/// What OpenAlex said about each DOI. `notChecked` couldn't be asked: no connection, or a lookup that failed.
public struct DOIMatches: Equatable, Sendable {
    public var found: [String: Paper] = [:]
    public var notFound: Set<String> = []
    public var notChecked: Set<String> = []

    public init(found: [String: Paper] = [:], notFound: Set<String> = [], notChecked: Set<String> = []) {
        self.found = found
        self.notFound = notFound
        self.notChecked = notChecked
    }

    /// Whether every DOI in `dois` has an answer here, so a resumed import can reuse these lookups.
    func covers(_ dois: [String]) -> Bool {
        dois.allSatisfy { found[$0] != nil || notFound.contains($0) || notChecked.contains($0) }
    }
}

/// Matches DOIs to OpenAlex works (spec §6). Batches of `doisPerRequest` go out as import calls, which the quota routes on
/// its import share; once it has no metered route left (`dailyLimit`), the rest are looked up one at a time with free single
/// lookups. Android's `OpenAlexDoiMatcher`.
public struct DOIMatcher: Sendable {
    /// DOIs per request: the docs disagree between 50 and 100 values per OR filter; 50 is safe, and below per_page's 100.
    public static let doisPerRequest = 50
    /// Between requests, under the keyless limit of 10 per second.
    public static let requestSpacing: Duration = .milliseconds(100)

    private let openAlex: any OpenAlexLookupService
    private let pause: @Sendable (Duration) async -> Void

    public init(openAlex: any OpenAlexLookupService, pause: @escaping @Sendable (Duration) async -> Void = { try? await Task.sleep(for: $0) }) {
        self.openAlex = openAlex
        self.pause = pause
    }

    public func match(_ dois: [String]) async -> DOIMatches {
        var seen = Set<String>()
        let unique = dois.filter { seen.insert($0).inserted }
        let canFilter: (String) -> Bool = { doi in !doi.contains("|") && !doi.contains(",") && !doi.contains(where: \.isWhitespace) }
        let filterable = unique.filter(canFilter)
        var singles = unique.filter { !canFilter($0) }
        let batches = stride(from: 0, to: filterable.count, by: Self.doisPerRequest).map {
            Array(filterable[$0..<min($0 + Self.doisPerRequest, filterable.count)])
        }
        var result = DOIMatches()
        var stopped = false
        var singlesOnly = false
        var sent = 0

        func spaced() async {
            if sent > 0 { await pause(Self.requestSpacing) }
            sent += 1
        }

        func record(_ batch: [String], _ works: [NetworkWork]) {
            for work in works {
                guard let doi = work.doi.flatMap(normalizeDOI), batch.contains(doi), result.found[doi] == nil else { continue }
                result.found[doi] = work.asPaper()
            }
            result.notFound.formUnion(batch.filter { result.found[$0] == nil })
        }

        for batch in batches {
            if stopped {
                result.notChecked.formUnion(batch)
                continue
            }
            if singlesOnly {
                singles += batch
                continue
            }
            await spaced()
            do {
                let filter = "doi:" + batch.joined(separator: "|")
                record(batch, try await openAlex.importWorks(filter: filter, perPage: Self.doisPerRequest).results)
            } catch NetworkFailure.dailyLimit(resetAt: _) {
                // No metered route left for imports today: finish with free single lookups.
                singlesOnly = true
                singles += batch
            } catch NetworkFailure.connectivity {
                stopped = true
                result.notChecked.formUnion(batch)
            } catch is CancellationError {
                stopped = true
                result.notChecked.formUnion(batch)
            } catch {
                result.notChecked.formUnion(batch)
            }
        }
        for doi in singles {
            if stopped || Task.isCancelled {
                result.notChecked.insert(doi)
                continue
            }
            await spaced()
            do {
                record([doi], try await openAlex.work(id: "doi:" + doi).map { [$0] } ?? [])
            } catch NetworkFailure.connectivity {
                stopped = true
                result.notChecked.insert(doi)
            } catch is CancellationError {
                stopped = true
                result.notChecked.insert(doi)
            } catch {
                result.notChecked.insert(doi)
            }
        }
        return result
    }
}
