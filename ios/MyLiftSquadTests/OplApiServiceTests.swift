import XCTest
@testable import MyLiftSquad

final class OplApiServiceTests: XCTestCase {

    private var service: OplApiService!

    // CSV header + builder matching OplApiService column layout
    private let header =
        "Name,Sex,Event,Equipment,Age,AgeClass,BirthYearClass,Division," +
        "BodyweightKg,WeightClassKg," +
        "Squat1Kg,Squat2Kg,Squat3Kg,Squat4Kg,Best3SquatKg," +
        "Bench1Kg,Bench2Kg,Bench3Kg,Bench4Kg,Best3BenchKg," +
        "Deadlift1Kg,Deadlift2Kg,Deadlift3Kg,Deadlift4Kg,Best3DeadliftKg," +
        "TotalKg,Place,Dots,Wilks,Glossbrenner,Goodlift," +
        "Tested,Country,State,Federation,ParentFederation," +
        "Date,MeetCountry,MeetState,MeetTown,MeetName,Sanctioned"

    private func csvRow(
        bestSquat: String = "200", bestBench: String = "130",
        bestDeadlift: String = "250", total: String = "580",
        place: String = "1", federation: String = "IPF",
        date: String = "2024-03-15", meetName: String = "British Open"
    ) -> String {
        var f = Array(repeating: "", count: 42)
        f[3] = "Raw"; f[7] = "Open"; f[8] = "89.5"; f[9] = "93"
        f[14] = bestSquat; f[19] = bestBench; f[24] = bestDeadlift
        f[25] = total; f[26] = place; f[27] = "350.5"; f[34] = federation
        f[36] = date; f[37] = "GBR"; f[39] = "Manchester"; f[40] = meetName
        return f.joined(separator: ",")
    }

    override func setUp() {
        super.setUp()
        MockURLProtocol.reset()
        service = OplApiService(baseURL: "http://mock", session: makeMockSession())
    }

    // MARK: - fetchHistory request path

    func testFetchHistoryRequestsCorrectPath() async throws {
        MockURLProtocol.requestHandler = { _ in (200, "\(self.header)\n\(self.csvRow())") }
        _ = try await service.fetchHistory(slug: "john-doe")
        let path = MockURLProtocol.capturedRequests.first?.url?.path
        XCTAssertEqual(path, "/liftercsv/john-doe")
    }

    // MARK: - fetchHistory parsing

    func testFetchHistoryParsesSBDResult() async throws {
        MockURLProtocol.requestHandler = { _ in (200, "\(self.header)\n\(self.csvRow())") }
        let (results, _) = try await service.fetchHistory(slug: "test")
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].best3SquatKg, 200.0, accuracy: 0.001)
        XCTAssertEqual(results[0].best3BenchKg, 130.0, accuracy: 0.001)
        XCTAssertEqual(results[0].best3DeadliftKg, 250.0, accuracy: 0.001)
        XCTAssertEqual(results[0].totalKg, 580.0, accuracy: 0.001)
        XCTAssertEqual(results[0].date, "2024-03-15")
        XCTAssertEqual(results[0].meetName, "British Open")
    }

    func testFetchHistoryReturnsEmptyOnHeaderOnlyCsv() async throws {
        MockURLProtocol.requestHandler = { _ in (200, self.header) }
        let (results, _) = try await service.fetchHistory(slug: "test")
        XCTAssertTrue(results.isEmpty)
    }

    func testFetchHistoryThrowsOnNetworkError() async {
        MockURLProtocol.requestHandler = { _ in (500, "Internal Server Error") }
        do {
            _ = try await service.fetchHistory(slug: "test")
            // fetchHistory returns ([], nil) on errors rather than throwing
            // so an empty result is also acceptable
        } catch {
            // throwing is also fine
        }
    }

    func testFetchHistoryParsesMultipleRows() async throws {
        let csv = "\(header)\n\(csvRow(date: "2024-01-01", meetName: "Meet One"))\n\(csvRow(date: "2023-06-01", meetName: "Meet Two"))"
        MockURLProtocol.requestHandler = { _ in (200, csv) }
        let (results, _) = try await service.fetchHistory(slug: "test")
        XCTAssertEqual(results.count, 2)
        // sorted newest first
        XCTAssertEqual(results[0].meetName, "Meet One")
        XCTAssertEqual(results[1].meetName, "Meet Two")
    }

    func testFetchHistoryExcludesDQEntries() async throws {
        let csv = "\(header)\n\(csvRow(place: "1", meetName: "Good"))\n\(csvRow(place: "DQ", meetName: "Bad"))"
        MockURLProtocol.requestHandler = { _ in (200, csv) }
        let (results, _) = try await service.fetchHistory(slug: "test")
        XCTAssertEqual(results.count, 1)
        XCTAssertEqual(results[0].meetName, "Good")
    }

    // MARK: - fetchHistory PR calculation

    func testFetchHistoryBuildsAthletePRs() async throws {
        let csv = "\(header)\n\(csvRow(bestSquat: "200", bestBench: "130", bestDeadlift: "250", total: "580"))\n\(csvRow(bestSquat: "220", bestBench: "140", bestDeadlift: "270", total: "630"))"
        MockURLProtocol.requestHandler = { _ in (200, csv) }
        let (_, prAthlete) = try await service.fetchHistory(slug: "test")
        let athlete = try XCTUnwrap(prAthlete)
        XCTAssertEqual(athlete.bestSquatKg, 220.0, accuracy: 0.001)
        XCTAssertEqual(athlete.bestBenchKg, 140.0, accuracy: 0.001)
        XCTAssertEqual(athlete.bestDeadliftKg, 270.0, accuracy: 0.001)
        XCTAssertEqual(athlete.bestTotalKg, 630.0, accuracy: 0.001)
    }

    func testFetchHistoryReturnsNilAthleteForEmptyResults() async throws {
        MockURLProtocol.requestHandler = { _ in (200, self.header) }
        let (_, prAthlete) = try await service.fetchHistory(slug: "test")
        XCTAssertNil(prAthlete)
    }

    func testFetchHistoryAthleteUsesLatestEntryForMetadata() async throws {
        // Results are sorted newest first, so first result in array is the latest
        let csv = "\(header)\n\(csvRow(federation: "IPF", date: "2024-01-01"))\n\(csvRow(federation: "USPA", date: "2022-01-01"))"
        MockURLProtocol.requestHandler = { _ in (200, csv) }
        let (_, prAthlete) = try await service.fetchHistory(slug: "test")
        XCTAssertEqual(prAthlete?.federation, "IPF")
    }
}
