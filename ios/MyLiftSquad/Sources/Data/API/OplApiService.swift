import Foundation

struct SearchPage {
    let athletes: [OplAthlete]
    let nextMenStart: Int?
    let nextWomenStart: Int?
    var hasMore: Bool { nextMenStart != nil || nextWomenStart != nil }
}

actor OplApiService {
    static let shared = OplApiService()
    private let baseURL = "https://www.openpowerlifting.org/api"

    private init() {}

    // MARK: - Search

    /// Search for athletes. Pass nextMenStart/nextWomenStart from a previous SearchPage
    /// to load more results. A nil start means that gender path is exhausted and will be skipped.
    func searchAthletes(query: String, menStart: Int? = 0, womenStart: Int? = 0) async throws -> SearchPage {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else { return SearchPage(athletes: [], nextMenStart: nil, nextWomenStart: nil) }

        let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed

        async let menResult: ([OplAthlete], Int?) = menStart != nil
            ? searchGender("men", query: encoded, startAt: menStart!)
            : ([], nil)
        async let womenResult: ([OplAthlete], Int?) = womenStart != nil
            ? searchGender("women", query: encoded, startAt: womenStart!)
            : ([], nil)

        let (men, nextMenStart) = try await menResult
        let (women, nextWomenStart) = try await womenResult

        var seen = Set<String>()
        var combined: [OplAthlete] = []
        for athlete in (men + women) {
            if !seen.contains(athlete.slug) {
                seen.insert(athlete.slug)
                combined.append(athlete)
            }
        }
        return SearchPage(athletes: combined, nextMenStart: nextMenStart, nextWomenStart: nextWomenStart)
    }

    /// Search a specific gender path, doing up to 3 iterations from startAt.
    /// Returns the athletes found and the next start position (nil if exhausted).
    private func searchGender(_ gender: String, query: String, startAt: Int) async throws -> ([OplAthlete], Int?) {
        let queryLower = query.removingPercentEncoding?.lowercased() ?? query.lowercased()
        var accumulated: [String: OplAthlete] = [:]
        var start = startAt
        var nextStart: Int? = nil

        for _ in 0..<3 {
            let searchURL = "\(baseURL)/search/rankings/\(gender)?q=\(query)&start=\(start)&lang=en&units=kg"
            guard let url = URL(string: searchURL) else { break }

            let (searchData, _) = try await URLSession.shared.data(from: url)
            guard let json = try? JSONSerialization.jsonObject(with: searchData) as? [String: Any],
                  let nextIndex = json["next_index"] as? Int else { break }

            let rankURL = "\(baseURL)/rankings/\(gender)?start=\(nextIndex)&end=\(nextIndex + 24)&lang=en&units=kg"
            guard let rUrl = URL(string: rankURL) else { break }

            let (rankData, _) = try await URLSession.shared.data(from: rUrl)
            guard let rankJson = try? JSONSerialization.jsonObject(with: rankData) as? [String: Any],
                  let rows = rankJson["rows"] as? [[Any]] else { break }

            for row in rows {
                guard row.count >= 24,
                      let name = row[2] as? String,
                      let slug = row[3] as? String,
                      name.lowercased().contains(queryLower),
                      accumulated[slug] == nil else { continue }

                accumulated[slug] = OplAthlete(
                    name: name,
                    slug: slug,
                    country: row[6] as? String ?? "",
                    federation: row[8] as? String ?? "",
                    bestSquatKg: parseDouble(row[19]),
                    bestBenchKg: parseDouble(row[20]),
                    bestDeadliftKg: parseDouble(row[21]),
                    bestTotalKg: parseDouble(row[22]),
                    weightClass: row[18] as? String ?? "",
                    equipment: row[14] as? String ?? "",
                    lastCompDate: row[9] as? String ?? "",
                    gender: gender == "men" ? "M" : "F"
                )
            }

            start = nextIndex + 1
            nextStart = start
        }

        return (Array(accumulated.values), nextStart)
    }

    // MARK: - Competition History

    func fetchHistory(slug: String) async throws -> ([CompetitionResult], OplAthlete?) {
        let csvURL = "\(baseURL)/liftercsv/\(slug)"
        guard let url = URL(string: csvURL) else { return ([], nil) }

        let (data, _) = try await URLSession.shared.data(from: url)
        guard let csvString = String(data: data, encoding: .utf8) else { return ([], nil) }

        let results = parseCSV(csvString, slug: slug)
        let prAthlete = buildAthletePRs(from: results, slug: slug)
        return (results, prAthlete)
    }

    // MARK: - CSV Parsing
    //
    // Column indices (0-based), matching Android OplApiService:
    //  0=Name  1=Sex  2=Event  3=Equipment  4=Age  5=AgeClass  6=BirthYearClass
    //  7=Division  8=BodyweightKg  9=WeightClassKg
    //  10=Squat1  11=Squat2  12=Squat3  13=Squat4  14=Best3SquatKg
    //  15=Bench1  16=Bench2  17=Bench3  18=Bench4  19=Best3BenchKg
    //  20=DL1     21=DL2     22=DL3     23=DL4     24=Best3DeadliftKg
    //  25=TotalKg  26=Place  27=Dots  28=Wilks  29=Glossbrenner  30=Goodlift
    //  31=Tested  32=Country  33=State  34=Federation  35=ParentFederation
    //  36=Date  37=MeetCountry  38=MeetState  39=MeetTown  40=MeetName  41=Sanctioned

    private func parseCSV(_ csv: String, slug: String) -> [CompetitionResult] {
        let lines = csv.components(separatedBy: "\n")
        guard lines.count >= 2 else { return [] }

        let invalidPlaces: Set<String> = ["DQ", "DD", "DNS", "NS", "G"]

        return lines.dropFirst().compactMap { line in
            guard !line.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
            let f = parseCSVLine(line)

            let date = f.count > 36 ? f[36] : ""
            let meetName = f.count > 40 ? f[40] : ""
            guard !date.isEmpty, !meetName.isEmpty else { return nil }

            let place = f.count > 26 ? f[26] : ""
            guard !invalidPlaces.contains(place) else { return nil }

            return CompetitionResult(
                date: date,
                meetName: meetName,
                federation: f.count > 34 ? f[34] : "",
                equipment: f.count > 3  ? f[3]  : "",
                division:  f.count > 7  ? f[7]  : "",
                weightClassKg: f.count > 9  ? f[9]  : "",
                bodyweightKg:  f.count > 8  ? Double(f[8])  ?? 0 : 0,
                best3SquatKg:  f.count > 14 ? Double(f[14]) ?? 0 : 0,
                best3BenchKg:  f.count > 19 ? Double(f[19]) ?? 0 : 0,
                best3DeadliftKg: f.count > 24 ? Double(f[24]) ?? 0 : 0,
                totalKg:       f.count > 25 ? Double(f[25]) ?? 0 : 0,
                place: place,
                dots:          f.count > 27 ? Double(f[27]) ?? 0 : 0,
                meetCountry:   f.count > 37 ? f[37] : "",
                meetTown:      f.count > 39 ? f[39] : ""
            )
        }.sorted { $0.date > $1.date }
    }

    private func parseCSVLine(_ line: String) -> [String] {
        var fields: [String] = []
        var current = ""
        var inQuotes = false

        for char in line {
            if char == "\"" {
                inQuotes.toggle()
            } else if char == "," && !inQuotes {
                fields.append(current)
                current = ""
            } else {
                current.append(char)
            }
        }
        fields.append(current)
        return fields
    }

    private func buildAthletePRs(from results: [CompetitionResult], slug: String) -> OplAthlete? {
        guard let first = results.first else { return nil }

        var bestSquat: Double = 0
        var bestBench: Double = 0
        var bestDeadlift: Double = 0
        var bestTotal: Double = 0

        for result in results {
            if result.best3SquatKg > bestSquat { bestSquat = result.best3SquatKg }
            if result.best3BenchKg > bestBench { bestBench = result.best3BenchKg }
            if result.best3DeadliftKg > bestDeadlift { bestDeadlift = result.best3DeadliftKg }
            if result.totalKg > bestTotal { bestTotal = result.totalKg }
        }

        return OplAthlete(
            name: "",
            slug: slug,
            country: "",
            federation: first.federation,
            bestSquatKg: bestSquat,
            bestBenchKg: bestBench,
            bestDeadliftKg: bestDeadlift,
            bestTotalKg: bestTotal,
            weightClass: first.weightClassKg,
            equipment: first.equipment,
            lastCompDate: first.date,
            gender: ""
        )
    }

    // MARK: - Helpers

    private func parseDouble(_ value: Any) -> Double {
        if let d = value as? Double { return d }
        if let s = value as? String { return Double(s) ?? 0 }
        if let i = value as? Int { return Double(i) }
        return 0
    }
}
