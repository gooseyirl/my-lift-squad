/// A shared, main-actor-bound guard that prevents concurrent history fetches
/// for the same athlete slug across all view models.
///
/// Without this, `SearchViewModel.fetchHistoryAfterAdd` and
/// `SquadDetailViewModel.fetchFreshHistory` can both be in-flight for the
/// same slug simultaneously. Both do delete-then-insert, so if they interleave
/// (both delete, both fetch, both insert) the result is duplicate entries.
@MainActor
final class HistoryFetchGuard {
    static let shared = HistoryFetchGuard()
    private var inFlight: Set<String> = []

    private init() {}

    /// Returns `true` and marks `slug` as in-flight if no fetch is already running.
    /// Returns `false` (and does nothing) if a fetch for `slug` is already active.
    func acquire(_ slug: String) -> Bool {
        guard !inFlight.contains(slug) else { return false }
        inFlight.insert(slug)
        return true
    }

    /// Marks a slug as no longer in-flight.
    func release(_ slug: String) {
        inFlight.remove(slug)
    }
}
