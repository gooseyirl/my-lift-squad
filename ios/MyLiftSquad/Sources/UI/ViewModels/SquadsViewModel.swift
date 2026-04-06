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
        let squad = Squad(name: trimmed)
        modelContext.insert(squad)
        try? modelContext.save()
        newSquadName = ""
        showNewSquadDialog = false
        loadData()
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
        favouriteHistory = []
        showFavouriteDetail = true
        Task { await loadFavouriteHistory(for: athlete) }
    }

    private func loadFavouriteHistory(for athlete: Athlete) async {
        isFavouriteHistoryLoading = true
        defer { isFavouriteHistoryLoading = false }
        guard let (results, _) = try? await OplApiService.shared.fetchHistory(slug: athlete.slug) else { return }
        favouriteHistory = results
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
