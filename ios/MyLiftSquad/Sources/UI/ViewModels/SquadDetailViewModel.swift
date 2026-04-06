import SwiftUI
import SwiftData

@MainActor
@Observable
final class SquadDetailViewModel {
    var athletes: [Athlete] = []
    var isRefreshing = false
    var errorMessage: String?

    // Athlete detail sheet
    var selectedAthlete: Athlete?
    var competitionHistory: [CompetitionResult] = []
    var isLoadingHistory = false
    var showAthleteDetail = false

    // Max favourites snackbar
    var showMaxFavsMessage = false

    private var squad: Squad
    private let modelContext: ModelContext

    init(squad: Squad, modelContext: ModelContext) {
        self.squad = squad
        self.modelContext = modelContext
    }

    func loadAthletes() {
        athletes = squad.athletes.sorted { $0.name < $1.name }
    }

    func showDetail(for athlete: Athlete) {
        selectedAthlete = athlete
        showAthleteDetail = true
        loadCachedHistory(for: athlete)
    }

    func refreshSelectedAthlete() {
        guard let athlete = selectedAthlete else { return }
        Task { await fetchFreshHistory(for: athlete) }
    }

    private func loadCachedHistory(for athlete: Athlete) {
        let slug = athlete.slug
        let descriptor = FetchDescriptor<CompetitionEntry>(
            predicate: #Predicate { $0.athleteSlug == slug },
            sortBy: [SortDescriptor(\.date, order: .reverse)]
        )
        let cached = (try? modelContext.fetch(descriptor)) ?? []
        competitionHistory = cached.map { entry in
            CompetitionResult(
                date: entry.date,
                meetName: entry.meetName,
                federation: entry.federation,
                equipment: entry.equipment,
                division: entry.division,
                weightClassKg: entry.weightClassKg,
                bodyweightKg: entry.bodyweightKg,
                best3SquatKg: entry.best3SquatKg,
                best3BenchKg: entry.best3BenchKg,
                best3DeadliftKg: entry.best3DeadliftKg,
                totalKg: entry.totalKg,
                place: entry.place,
                dots: entry.dots,
                meetCountry: entry.meetCountry,
                meetTown: entry.meetTown
            )
        }
    }

    private func fetchFreshHistory(for athlete: Athlete) async {
        isLoadingHistory = true
        defer { isLoadingHistory = false }

        let slug = athlete.slug
        do {
            let (fresh, _) = try await OplApiService.shared.fetchHistory(slug: slug)

            // Only update if we got results — never overwrite good data with empty
            guard !fresh.isEmpty else { return }

            competitionHistory = fresh

            // Persist to DB
            try modelContext.delete(model: CompetitionEntry.self, where: #Predicate { $0.athleteSlug == slug })
            for result in fresh {
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

            // Update last comp details from most recent entry
            if let latest = fresh.first {
                if !latest.federation.isEmpty { athlete.federation = latest.federation }
                if !latest.weightClassKg.isEmpty { athlete.weightClass = latest.weightClassKg }
                if !latest.equipment.isEmpty { athlete.equipment = latest.equipment }
                athlete.lastCompDate = latest.date
            }

            // Recalculate PRs from all entries including single-discipline competitions.
            // OPL stores bomb-outs as negative values — exclude those and disqualified results.
            let invalidPlaces: Set<String> = ["DQ", "DD", "DNS", "NS", "G"]
            let valid = fresh.filter { !invalidPlaces.contains($0.place) }
            let bestSquat = valid.map(\.best3SquatKg).filter { $0 > 0 }.max() ?? 0
            let bestBench = valid.map(\.best3BenchKg).filter { $0 > 0 }.max() ?? 0
            let bestDeadlift = valid.map(\.best3DeadliftKg).filter { $0 > 0 }.max() ?? 0
            let bestTotal = valid.map(\.totalKg).filter { $0 > 0 }.max() ?? 0
            athlete.bestSquatKg = bestSquat
            athlete.bestBenchKg = bestBench
            athlete.bestDeadliftKg = bestDeadlift
            athlete.bestTotalKg = bestTotal

            try? modelContext.save()
        } catch {
            // Keep cached data visible on error — only report if we had nothing to show
            if competitionHistory.isEmpty {
                errorMessage = "Failed to load history: \(error.localizedDescription)"
            }
        }
    }

    func refreshAll() async {
        isRefreshing = true
        defer { isRefreshing = false }
        for athlete in athletes {
            await fetchFreshHistory(for: athlete)
        }
        loadAthletes()
    }

    func toggleFavourite(_ athlete: Athlete) {
        if !athlete.isFavourite {
            // Check max 3 favourites
            let favDescriptor = FetchDescriptor<Athlete>(
                predicate: #Predicate { $0.isFavourite }
            )
            let count = (try? modelContext.fetch(favDescriptor).count) ?? 0
            if count >= 3 {
                showMaxFavsMessage = true
                return
            }
        }
        athlete.isFavourite.toggle()
        try? modelContext.save()
        loadAthletes()
    }

    func removeAthlete(_ athlete: Athlete) {
        modelContext.delete(athlete)
        try? modelContext.save()
        loadAthletes()
    }
}
