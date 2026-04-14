import Foundation

enum PrCalculator {

    private static let invalidPlaces: Set<String> = ["DQ", "DD", "DNS", "NS", "G"]

    struct Result {
        let bestSquat: Double
        let bestBench: Double
        let bestDeadlift: Double
        let bestTotal: Double
    }

    static func calculate(from results: [CompetitionResult]) -> Result {
        let valid = results.filter { !invalidPlaces.contains($0.place) }
        return Result(
            bestSquat: valid.map(\.best3SquatKg).filter { $0 > 0 }.max() ?? 0,
            bestBench: valid.map(\.best3BenchKg).filter { $0 > 0 }.max() ?? 0,
            bestDeadlift: valid.map(\.best3DeadliftKg).filter { $0 > 0 }.max() ?? 0,
            bestTotal: valid.map(\.totalKg).filter { $0 > 0 }.max() ?? 0
        )
    }
}
