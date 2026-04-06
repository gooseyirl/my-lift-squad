import Foundation

struct OplAthlete {
    let name: String
    let slug: String
    let country: String
    let federation: String
    let bestSquatKg: Double
    let bestBenchKg: Double
    let bestDeadliftKg: Double
    let bestTotalKg: Double
    let weightClass: String
    let equipment: String
    let lastCompDate: String
    let gender: String
}

struct CompetitionResult: Identifiable {
    let id = UUID()
    let date: String
    let meetName: String
    let federation: String
    let equipment: String
    let division: String
    let weightClassKg: String
    let bodyweightKg: Double
    let best3SquatKg: Double
    let best3BenchKg: Double
    let best3DeadliftKg: Double
    let totalKg: Double
    let place: String
    let dots: Double
    let meetCountry: String
    let meetTown: String
}
