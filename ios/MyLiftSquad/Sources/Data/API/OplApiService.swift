import Foundation

actor OplApiService {
    static let shared = OplApiService()
    private let baseURL = "https://www.openpowerlifting.org/api"

    private init() {}

    // MARK: - Search

    func searchAthletes(query: String) async throws -> [OplAthlete] {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard trimmed.count >= 2 else { return [] }

        let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed

        async let menResults = searchGender("men", query: encoded)
        async let womenResults = searchGender("women", query: encoded)

        let (men, women) = try await (menResults, womenResults)

        var seen = Set<String>()
        var combined: [OplAthlete] = []
        for athlete in (men + women) {
            if !seen.contains(athlete.slug) {
                seen.insert(athlete.slug)
                combined.append(athlete)
            }
        }
        return combined
    }

    private func searchGender(_ gender: String, query: String) async throws -> [OplAthlete] {
        // Step 1: get start index
        let searchURL = "\(baseURL)/search/rankings/\(gender)?q=\(query)&start=0&lang=en&units=kg"
        guard let url = URL(string: searchURL) else { return [] }

        let (searchData, _) = try await URLSession.shared.data(from: url)
        guard let json = try? JSONSerialization.jsonObject(with: searchData) as? [String: Any],
              let nextIndex = json["next_index"] as? Int else {
            return []
        }

        // Step 2: get rankings at that index
        let endIndex = nextIndex + 24
        let rankURL = "\(baseURL)/rankings/\(gender)?start=\(nextIndex)&end=\(endIndex)&lang=en&units=kg"
        guard let rUrl = URL(string: rankURL) else { return [] }

        let (rankData, _) = try await URLSession.shared.data(from: rUrl)
        guard let rankJson = try? JSONSerialization.jsonObject(with: rankData) as? [String: Any],
              let rows = rankJson["rows"] as? [[Any]] else {
            return []
        }

        let queryLower = query.removingPercentEncoding?.lowercased() ?? query.lowercased()
        return rows.compactMap { row in
            guard row.count >= 24,
                  let name = row[2] as? String,
                  let slug = row[3] as? String else { return nil }

            guard name.lowercased().contains(queryLower) else { return nil }

            let country = row[6] as? String ?? ""
            let federation = row[8] as? String ?? ""
            let lastDate = row[9] as? String ?? ""
            let equipment = row[14] as? String ?? ""
            let weightClass = row[18] as? String ?? ""
            let squat = parseDouble(row[19])
            let bench = parseDouble(row[20])
            let deadlift = parseDouble(row[21])
            let total = parseDouble(row[22])
            let genderStr = gender == "men" ? "M" : "F"

            return OplAthlete(
                name: name,
                slug: slug,
                country: country,
                federation: federation,
                bestSquatKg: squat,
                bestBenchKg: bench,
                bestDeadliftKg: deadlift,
                bestTotalKg: total,
                weightClass: weightClass,
                equipment: equipment,
                lastCompDate: lastDate,
                gender: genderStr
            )
        }
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

    private func parseCSV(_ csv: String, slug: String) -> [CompetitionResult] {
        var lines = csv.components(separatedBy: "\n")
        guard !lines.isEmpty else { return [] }

        lines.removeFirst() // header
        let invalidPlaces: Set<String> = ["DQ", "DD", "DNS", "NS", "G"]

        return lines.compactMap { line in
            let fields = parseCSVLine(line)
            guard fields.count >= 34 else { return nil }

            let place = fields[17]
            guard !invalidPlaces.contains(place) else { return nil }

            let date = fields[28]
            let meetName = fields[33]
            guard !date.isEmpty, !meetName.isEmpty else { return nil }

            return CompetitionResult(
                date: date,
                meetName: meetName,
                federation: fields[26],
                equipment: fields[3],
                division: fields[7],
                weightClassKg: fields[9],
                bodyweightKg: Double(fields[8]) ?? 0,
                best3SquatKg: Double(fields[12]) ?? 0,
                best3BenchKg: Double(fields[16]) ?? 0,
                best3DeadliftKg: Double(fields[20]) ?? 0,
                totalKg: Double(fields[21]) ?? 0,
                place: place,
                dots: Double(fields[23]) ?? 0,
                meetCountry: fields[29],
                meetTown: fields[31]
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
