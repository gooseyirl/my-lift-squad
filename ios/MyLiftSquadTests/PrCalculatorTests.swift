import XCTest
@testable import MyLiftSquad

final class PrCalculatorTests: XCTestCase {

    private func makeResult(
        squat: Double = 0,
        bench: Double = 0,
        deadlift: Double = 0,
        total: Double = 0,
        place: String = "1"
    ) -> CompetitionResult {
        CompetitionResult(
            date: "2024-01-01",
            meetName: "Test Meet",
            federation: "IPF",
            equipment: "Raw",
            division: "Open",
            weightClassKg: "93",
            bodyweightKg: 90.0,
            best3SquatKg: squat,
            best3BenchKg: bench,
            best3DeadliftKg: deadlift,
            totalKg: total,
            place: place,
            dots: 0,
            meetCountry: "GBR",
            meetTown: ""
        )
    }

    func testBestTotalIncludedInPrCalculation() {
        let results = [
            makeResult(squat: 200, bench: 130, deadlift: 250, total: 580),
            makeResult(squat: 210, bench: 135, deadlift: 260, total: 605)
        ]
        let prs = PrCalculator.calculate(from: results)
        XCTAssertEqual(prs.bestTotal, 605)
    }

    func testNewCompetitionUpdatesAllPrs() {
        let results = [
            makeResult(squat: 200, bench: 130, deadlift: 250, total: 580),
            makeResult(squat: 215, bench: 140, deadlift: 270, total: 625)
        ]
        let prs = PrCalculator.calculate(from: results)
        XCTAssertEqual(prs.bestSquat, 215)
        XCTAssertEqual(prs.bestBench, 140)
        XCTAssertEqual(prs.bestDeadlift, 270)
        XCTAssertEqual(prs.bestTotal, 625)
    }

    func testDisqualifiedEntriesExcludedFromPrs() {
        let results = [
            makeResult(squat: 200, bench: 130, deadlift: 250, total: 580, place: "1"),
            makeResult(squat: 300, bench: 200, deadlift: 350, total: 850, place: "DQ"),
            makeResult(squat: 300, bench: 200, deadlift: 350, total: 850, place: "DD"),
            makeResult(place: "DNS"),
            makeResult(place: "NS"),
            makeResult(place: "G")
        ]
        let prs = PrCalculator.calculate(from: results)
        XCTAssertEqual(prs.bestSquat, 200)
        XCTAssertEqual(prs.bestBench, 130)
        XCTAssertEqual(prs.bestDeadlift, 250)
        XCTAssertEqual(prs.bestTotal, 580)
    }

    func testNegativeValuesFromBombOutsExcluded() {
        let results = [
            makeResult(squat: 200, bench: 130, deadlift: 250, total: 580, place: "1"),
            makeResult(squat: -1, bench: 130, deadlift: 250, total: 0, place: "2")
        ]
        let prs = PrCalculator.calculate(from: results)
        XCTAssertEqual(prs.bestSquat, 200)
    }

    func testEmptyResultsReturnZeroPrs() {
        let prs = PrCalculator.calculate(from: [])
        XCTAssertEqual(prs.bestSquat, 0)
        XCTAssertEqual(prs.bestBench, 0)
        XCTAssertEqual(prs.bestDeadlift, 0)
        XCTAssertEqual(prs.bestTotal, 0)
    }
}
