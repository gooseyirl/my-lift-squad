import XCTest
@testable import MyLiftSquad

/// Tests for OplApiService's CSV parsing logic.
///
/// Column layout (0-indexed):
/// 0=Name 1=Sex 2=Event 3=Equipment 4=Age 5=AgeClass 6=BirthYearClass
/// 7=Division 8=BodyweightKg 9=WeightClassKg
/// 10–13=Squat1–4 14=Best3SquatKg
/// 15–18=Bench1–4 19=Best3BenchKg
/// 20–23=DL1–4   24=Best3DeadliftKg
/// 25=TotalKg 26=Place 27=Dots 28=Wilks 29=Glossbrenner 30=Goodlift
/// 31=Tested 32=Country 33=State 34=Federation 35=ParentFederation
/// 36=Date 37=MeetCountry 38=MeetState 39=MeetTown 40=MeetName 41=Sanctioned
final class OplCsvParserTests: XCTestCase {

    private var service: OplApiService!

    override func setUp() {
        super.setUp()
        service = OplApiService(baseURL: "http://unused", session: makeMockSession())
    }

    // MARK: - Helpers

    private let header =
        "Name,Sex,Event,Equipment,Age,AgeClass,BirthYearClass,Division," +
        "BodyweightKg,WeightClassKg," +
        "Squat1Kg,Squat2Kg,Squat3Kg,Squat4Kg,Best3SquatKg," +
        "Bench1Kg,Bench2Kg,Bench3Kg,Bench4Kg,Best3BenchKg," +
        "Deadlift1Kg,Deadlift2Kg,Deadlift3Kg,Deadlift4Kg,Best3DeadliftKg," +
        "TotalKg,Place,Dots,Wilks,Glossbrenner,Goodlift," +
        "Tested,Country,State,Federation,ParentFederation," +
        "Date,MeetCountry,MeetState,MeetTown,MeetName,Sanctioned"

    private func row(
        name: String = "Test Athlete",
        equipment: String = "Raw",
        division: String = "Open",
        bodyweight: String = "89.5",
        weightClass: String = "93",
        bestSquat: String = "200",
        bestBench: String = "130",
        bestDeadlift: String = "250",
        total: String = "580",
        place: String = "1",
        dots: String = "350.5",
        federation: String = "IPF",
        date: String = "2024-03-15",
        meetCountry: String = "GBR",
        meetTown: String = "Manchester",
        meetName: String = "British Open"
    ) -> String {
        var f = Array(repeating: "", count: 42)
        f[0]  = name;  f[3]  = equipment; f[7]  = division
        f[8]  = bodyweight; f[9]  = weightClass
        f[14] = bestSquat; f[19] = bestBench; f[24] = bestDeadlift
        f[25] = total; f[26] = place; f[27] = dots; f[34] = federation
        f[36] = date; f[37] = meetCountry; f[39] = meetTown; f[40] = meetName
        return f.joined(separator: ",")
    }

    private func csv(_ rows: String...) -> String {
        ([header] + rows).joined(separator: "\n")
    }

    // MARK: - parseCSVLine

    func testParseCSVLineSimpleFields() async {
        let result = await service.parseCSVLine("Alice,Raw,Open,90.0")
        XCTAssertEqual(result, ["Alice", "Raw", "Open", "90.0"])
    }

    func testParseCSVLineQuotedFieldContainingComma() async {
        let result = await service.parseCSVLine("Alice,\"Manchester, UK\",Open")
        XCTAssertEqual(result, ["Alice", "Manchester, UK", "Open"])
    }

    func testParseCSVLineEmptyFields() async {
        let result = await service.parseCSVLine("Alice,,Open,,")
        XCTAssertEqual(result, ["Alice", "", "Open", "", ""])
    }

    func testParseCSVLineSingleField() async {
        let result = await service.parseCSVLine("OnlyField")
        XCTAssertEqual(result, ["OnlyField"])
    }

    // MARK: - parseCSV — structure

    func testParseCSVEmptyStringReturnsEmpty() async {
        let result = await service.parseCSV("", slug: "test")
        XCTAssertTrue(result.isEmpty)
    }

    func testParseCSVHeaderOnlyReturnsEmpty() async {
        let result = await service.parseCSV(header, slug: "test")
        XCTAssertTrue(result.isEmpty)
    }

    func testParseCSVSkipsBlankLines() async {
        let input = csv(row(date: "2024-01-01", meetName: "Meet A"), "", row(date: "2023-01-01", meetName: "Meet B"))
        let result = await service.parseCSV(input, slug: "test")
        XCTAssertEqual(result.count, 2)
    }

    func testParseCSVSkipsRowWithNoDate() async {
        let result = await service.parseCSV(csv(row(date: "")), slug: "test")
        XCTAssertTrue(result.isEmpty)
    }

    func testParseCSVSkipsRowWithNoMeetName() async {
        let result = await service.parseCSV(csv(row(meetName: "")), slug: "test")
        XCTAssertTrue(result.isEmpty)
    }

    // MARK: - parseCSV — field mapping

    func testParseCSVMapsAllStandardFields() async {
        let result = await service.parseCSV(csv(row()), slug: "test")
        XCTAssertEqual(result.count, 1)
        let r = result[0]
        XCTAssertEqual(r.date, "2024-03-15")
        XCTAssertEqual(r.meetName, "British Open")
        XCTAssertEqual(r.federation, "IPF")
        XCTAssertEqual(r.equipment, "Raw")
        XCTAssertEqual(r.division, "Open")
        XCTAssertEqual(r.weightClassKg, "93")
        XCTAssertEqual(r.bodyweightKg, 89.5, accuracy: 0.001)
        XCTAssertEqual(r.best3SquatKg, 200.0, accuracy: 0.001)
        XCTAssertEqual(r.best3BenchKg, 130.0, accuracy: 0.001)
        XCTAssertEqual(r.best3DeadliftKg, 250.0, accuracy: 0.001)
        XCTAssertEqual(r.totalKg, 580.0, accuracy: 0.001)
        XCTAssertEqual(r.place, "1")
        XCTAssertEqual(r.dots, 350.5, accuracy: 0.001)
        XCTAssertEqual(r.meetCountry, "GBR")
        XCTAssertEqual(r.meetTown, "Manchester")
    }

    func testParseCSVMissingOptionalFieldsDefaultToZeroOrEmpty() async {
        let result = await service.parseCSV(csv(row(dots: "", meetTown: "")), slug: "test")
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].dots, 0)
        XCTAssertEqual(result[0].meetTown, "")
    }

    // MARK: - parseCSV — DQ filtering

    func testParseCSVFiltersDQEntries() async {
        let result = await service.parseCSV(csv(row(place: "DQ")), slug: "test")
        XCTAssertTrue(result.isEmpty, "DQ entries should be excluded from results")
    }

    func testParseCSVFiltersDDEntries() async {
        let result = await service.parseCSV(csv(row(place: "DD")), slug: "test")
        XCTAssertTrue(result.isEmpty)
    }

    func testParseCSVFiltersDNSEntries() async {
        let result = await service.parseCSV(csv(row(place: "DNS")), slug: "test")
        XCTAssertTrue(result.isEmpty)
    }

    func testParseCSVFiltersNSEntries() async {
        let result = await service.parseCSV(csv(row(place: "NS")), slug: "test")
        XCTAssertTrue(result.isEmpty)
    }

    func testParseCSVFiltersGEntries() async {
        let result = await service.parseCSV(csv(row(place: "G")), slug: "test")
        XCTAssertTrue(result.isEmpty)
    }

    func testParseCSVKeepsValidEntriesAlongsideDQ() async {
        let input = csv(
            row(place: "1", date: "2024-01-01", meetName: "Good Meet"),
            row(place: "DQ", date: "2023-01-01", meetName: "Bad Meet")
        )
        let result = await service.parseCSV(input, slug: "test")
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].meetName, "Good Meet")
    }

    // MARK: - parseCSV — lift values

    func testParseCSVNegativeSquatParsesAsNegativeValue() async {
        // iOS parseCSV stores the raw value; PrCalculator filters negatives
        let result = await service.parseCSV(csv(row(bestSquat: "-1")), slug: "test")
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].best3SquatKg, -1.0, accuracy: 0.001)
    }

    func testParseCSVEmptyLiftParsesAsZero() async {
        let result = await service.parseCSV(csv(row(bestSquat: "")), slug: "test")
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].best3SquatKg, 0.0)
    }

    func testParseCSVBenchOnlyMeetHasZeroSquatAndDeadlift() async {
        let result = await service.parseCSV(csv(row(bestSquat: "", bestDeadlift: "", total: "130")), slug: "test")
        XCTAssertEqual(result.count, 1)
        XCTAssertEqual(result[0].best3SquatKg, 0.0)
        XCTAssertEqual(result[0].best3BenchKg, 130.0, accuracy: 0.001)
        XCTAssertEqual(result[0].best3DeadliftKg, 0.0)
    }

    // MARK: - parseCSV — sorting

    func testParseCSVResultsSortedNewestFirst() async {
        let input = csv(
            row(date: "2022-06-01", meetName: "Oldest"),
            row(date: "2024-01-01", meetName: "Newest"),
            row(date: "2023-03-01", meetName: "Middle")
        )
        let result = await service.parseCSV(input, slug: "test")
        XCTAssertEqual(result.count, 3)
        XCTAssertEqual(result[0].meetName, "Newest")
        XCTAssertEqual(result[1].meetName, "Middle")
        XCTAssertEqual(result[2].meetName, "Oldest")
    }
}
