import SwiftUI
import SwiftData
import Combine

@MainActor
@Observable
final class SearchViewModel {
    var query = ""
    var results: [OplAthlete] = []
    var isSearching = false
    var addedSlugs: Set<String> = []
    var errorMessage: String?

    private var searchTask: Task<Void, Never>?
    private let modelContext: ModelContext
    private let squad: Squad

    init(squad: Squad, modelContext: ModelContext) {
        self.squad = squad
        self.modelContext = modelContext
        addedSlugs = Set(squad.athletes.map(\.slug))
    }

    func onQueryChanged() {
        searchTask?.cancel()
        let q = query
        guard q.count >= 2 else {
            results = []
            return
        }
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 400_000_000) // 400ms debounce
            guard !Task.isCancelled else { return }
            await performSearch(q)
        }
    }

    private func performSearch(_ q: String) async {
        isSearching = true
        defer { isSearching = false }
        do {
            results = try await OplApiService.shared.searchAthletes(query: q)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func addAthlete(_ oplAthlete: OplAthlete) {
        guard !addedSlugs.contains(oplAthlete.slug) else { return }

        let athlete = Athlete(
            name: oplAthlete.name,
            slug: oplAthlete.slug,
            country: oplAthlete.country,
            federation: oplAthlete.federation,
            bestSquatKg: oplAthlete.bestSquatKg,
            bestBenchKg: oplAthlete.bestBenchKg,
            bestDeadliftKg: oplAthlete.bestDeadliftKg,
            bestTotalKg: oplAthlete.bestTotalKg,
            weightClass: oplAthlete.weightClass,
            equipment: oplAthlete.equipment,
            lastCompDate: oplAthlete.lastCompDate,
            gender: oplAthlete.gender
        )
        athlete.squad = squad
        modelContext.insert(athlete)
        try? modelContext.save()
        addedSlugs.insert(oplAthlete.slug)

        // Auto-fetch history so PRs, federation, weight class and equipment
        // are populated correctly without needing a manual refresh.
        let slug = oplAthlete.slug
        Task { await fetchHistoryAfterAdd(slug: slug) }
    }

    private func fetchHistoryAfterAdd(slug: String) async {
        guard let (results, _) = try? await OplApiService.shared.fetchHistory(slug: slug) else { return }

        // Persist competition entries
        try? modelContext.delete(model: CompetitionEntry.self, where: #Predicate { $0.athleteSlug == slug })
        for result in results {
            let entry = CompetitionEntry(
                athleteSlug: slug,
                date: result.date,
                meetName: result.meetName,
                federation: result.federation,
                equipment: result.equipment,
                division: result.division,
                weightClassKg: result.weightClassKg,
                bodyweightKg: result.bodyweightKg,
                best3SquatKg: result.best3SquatKg,
                best3BenchKg: result.best3BenchKg,
                best3DeadliftKg: result.best3DeadliftKg,
                totalKg: result.totalKg,
                place: result.place,
                dots: result.dots,
                meetCountry: result.meetCountry,
                meetTown: result.meetTown
            )
            modelContext.insert(entry)
        }

        // Find athlete and update PRs + last comp details
        let descriptor = FetchDescriptor<Athlete>(predicate: #Predicate { $0.slug == slug })
        guard let athlete = try? modelContext.fetch(descriptor).first else { return }

        let prs = PrCalculator.calculate(from: results)
        athlete.bestSquatKg = prs.bestSquat
        athlete.bestBenchKg = prs.bestBench
        athlete.bestDeadliftKg = prs.bestDeadlift
        athlete.bestTotalKg = prs.bestTotal

        if let latest = results.first {
            if !latest.federation.isEmpty { athlete.federation = latest.federation }
            if !latest.weightClassKg.isEmpty { athlete.weightClass = latest.weightClassKg }
            if !latest.equipment.isEmpty { athlete.equipment = latest.equipment }
            athlete.lastCompDate = latest.date
        }

        try? modelContext.save()
    }
}
