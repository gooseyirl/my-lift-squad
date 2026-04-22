import SwiftUI
import SwiftData

@MainActor
@Observable
final class SquadsViewModel {
    var squads: [Squad] = []
    var favourites: [Athlete] = []
    var showNewSquadDialog = false
    var newSquadName = ""
    var errorMessage: String?
    var showQuote = false
    var currentQuote = ""

    // Rename squad
    var showRenameDialog = false
    var squadToRename: Squad?
    var renameNewName = ""
    var renameErrorMessage: String?

    // Import squad
    var showImportDialog = false
    var importCode = ""
    var isImporting = false
    var importProgress: String?
    var importError: String?
    var importedSquadName: String?

    // Favourite athlete detail sheet
    var selectedFavourite: Athlete?
    var favouriteHistory: [CompetitionResult] = []
    var isFavouriteHistoryLoading = false
    var showFavouriteDetail = false

    private var usedQuoteIndices: [Int] = []
    private let modelContext: ModelContext

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
        loadQuotePrefs()
    }

    func loadData() {
        do {
            let squadDescriptor = FetchDescriptor<Squad>(
                predicate: #Predicate { !$0.isSystem },
                sortBy: [SortDescriptor(\.name)]
            )
            squads = try modelContext.fetch(squadDescriptor)

            let favDescriptor = FetchDescriptor<Athlete>(
                predicate: #Predicate { $0.isFavourite },
                sortBy: [SortDescriptor(\.name)]
            )
            let allFavs = try modelContext.fetch(favDescriptor)
            favourites = Array(allFavs.prefix(3))
        } catch {
            errorMessage = "Failed to load data: \(error.localizedDescription)"
        }
    }

    func createSquad() {
        let trimmed = newSquadName.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }

        let allSquads = FetchDescriptor<Squad>(predicate: #Predicate { !$0.isSystem })
        let existing = (try? modelContext.fetch(allSquads)) ?? []
        guard !existing.contains(where: { $0.name.lowercased() == trimmed.lowercased() }) else {
            errorMessage = "A squad with this name already exists"
            return
        }

        let squad = Squad(name: trimmed)
        modelContext.insert(squad)
        try? modelContext.save()
        newSquadName = ""
        errorMessage = nil
        showNewSquadDialog = false
        loadData()
    }

    func importSquad() {
        let code = importCode.trimmingCharacters(in: .whitespaces).uppercased()
        guard code.count == 6 else {
            importError = "Enter a 6-character code"
            return
        }
        Task {
            isImporting = true
            importError = nil
            importProgress = "Fetching squad..."
            defer {
                isImporting = false
                importProgress = nil
            }
            do {
                let shared = try await ShareApiService.shared.importSquad(code: code)

                let allSquads = FetchDescriptor<Squad>(predicate: #Predicate { !$0.isSystem })
                let existing = (try? modelContext.fetch(allSquads)) ?? []
                guard !existing.contains(where: { $0.name.lowercased() == shared.name.lowercased() }) else {
                    importError = "You already have a squad named \"\(shared.name)\""
                    return
                }

                let squad = Squad(name: shared.name)
                modelContext.insert(squad)
                try? modelContext.save()

                let total = shared.athletes.count
                for (index, ref) in shared.athletes.enumerated() {
                    importProgress = "Fetching data for \(ref.name) (\(index + 1) of \(total))..."
                    let athlete = Athlete(name: ref.name, slug: ref.slug, country: "", federation: "")
                    athlete.squad = squad
                    modelContext.insert(athlete)
                    try? modelContext.save()

                    if let (results, _) = try? await OplApiService.shared.fetchHistory(slug: ref.slug),
                       !results.isEmpty {
                        let slug = ref.slug
                        for result in results {
                            modelContext.insert(CompetitionEntry(
                                athleteSlug: slug, date: result.date, meetName: result.meetName,
                                federation: result.federation, equipment: result.equipment,
                                division: result.division, weightClassKg: result.weightClassKg,
                                bodyweightKg: result.bodyweightKg, best3SquatKg: result.best3SquatKg,
                                best3BenchKg: result.best3BenchKg, best3DeadliftKg: result.best3DeadliftKg,
                                totalKg: result.totalKg, place: result.place, dots: result.dots,
                                meetCountry: result.meetCountry, meetTown: result.meetTown
                            ))
                        }
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

                let squadName = shared.name
                showImportDialog = false
                importCode = ""
                loadData()
                importedSquadName = squadName
                Task {
                    try? await Task.sleep(nanoseconds: 3_000_000_000)
                    importedSquadName = nil
                }
            } catch {
                importError = error.localizedDescription
            }
        }
    }

    func clearImportDialog() {
        showImportDialog = false
        importCode = ""
        importError = nil
    }

    func beginRename(_ squad: Squad) {
        squadToRename = squad
        renameNewName = squad.name
        renameErrorMessage = nil
        showRenameDialog = true
    }

    func confirmRename() {
        guard let squad = squadToRename else { return }
        let trimmed = renameNewName.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }

        let allSquads = FetchDescriptor<Squad>(predicate: #Predicate { !$0.isSystem })
        let existing = (try? modelContext.fetch(allSquads)) ?? []
        guard !existing.contains(where: { $0.name.lowercased() == trimmed.lowercased() && $0.id != squad.id }) else {
            renameErrorMessage = "A squad with this name already exists"
            return
        }

        squad.name = trimmed
        try? modelContext.save()
        renameErrorMessage = nil
        showRenameDialog = false
        squadToRename = nil
        renameNewName = ""
        loadData()
    }

    func clearRenameDialog() {
        showRenameDialog = false
        squadToRename = nil
        renameNewName = ""
        renameErrorMessage = nil
    }

    func deleteSquad(_ squad: Squad) {
        modelContext.delete(squad)
        try? modelContext.save()
        loadData()
    }

    func unfavourite(_ athlete: Athlete) {
        athlete.isFavourite = false
        try? modelContext.save()
        loadData()
    }

    func showDetail(for athlete: Athlete) {
        selectedFavourite = athlete
        showFavouriteDetail = true
        loadCachedFavouriteHistory(for: athlete)
    }

    func refreshFavouriteAthlete() {
        guard let athlete = selectedFavourite else { return }
        Task {
            isFavouriteHistoryLoading = true
            defer { isFavouriteHistoryLoading = false }
            guard let (results, _) = try? await OplApiService.shared.fetchHistory(slug: athlete.slug),
                  !results.isEmpty else { return }
            favouriteHistory = results
        }
    }

    private func loadCachedFavouriteHistory(for athlete: Athlete) {
        let slug = athlete.slug
        let descriptor = FetchDescriptor<CompetitionEntry>(
            predicate: #Predicate { $0.athleteSlug == slug },
            sortBy: [SortDescriptor(\.date, order: .reverse)]
        )
        let cached = (try? modelContext.fetch(descriptor)) ?? []
        favouriteHistory = cached.map {
            CompetitionResult(
                date: $0.date, meetName: $0.meetName, federation: $0.federation,
                equipment: $0.equipment, division: $0.division, weightClassKg: $0.weightClassKg,
                bodyweightKg: $0.bodyweightKg, best3SquatKg: $0.best3SquatKg,
                best3BenchKg: $0.best3BenchKg, best3DeadliftKg: $0.best3DeadliftKg,
                totalKg: $0.totalKg, place: $0.place, dots: $0.dots,
                meetCountry: $0.meetCountry, meetTown: $0.meetTown
            )
        }
    }

    // MARK: - Quotes

    func nextQuote() {
        if usedQuoteIndices.count >= motivationalQuotes.count {
            usedQuoteIndices = []
        }
        var available = motivationalQuotes.indices.filter { !usedQuoteIndices.contains($0) }
        if available.isEmpty { available = Array(motivationalQuotes.indices) }
        let idx = available.randomElement() ?? 0
        usedQuoteIndices.append(idx)
        currentQuote = motivationalQuotes[idx]
        saveQuotePrefs()
    }

    private func loadQuotePrefs() {
        let saved = UserDefaults.standard.string(forKey: "quote_remaining") ?? ""
        usedQuoteIndices = saved.split(separator: ",").compactMap { Int($0) }
        showQuote = UserDefaults.standard.bool(forKey: "donated")
        if showQuote && currentQuote.isEmpty {
            nextQuote()
        }
    }

    private func saveQuotePrefs() {
        let str = usedQuoteIndices.map(String.init).joined(separator: ",")
        UserDefaults.standard.set(str, forKey: "quote_remaining")
    }
}
