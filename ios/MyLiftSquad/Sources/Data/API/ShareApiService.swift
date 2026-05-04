import Foundation

struct AthleteRef: Codable {
    let name: String
    let slug: String
}

struct SharedSquad: Codable {
    let name: String
    let athletes: [AthleteRef]
}

struct SharedBundle: Decodable {
    let squads: [SharedSquad]
}

actor ShareApiService {
    static let shared = ShareApiService()
    private let baseURL: String
    private let session: URLSession

    private init() {
        self.baseURL = "https://myliftsquad-api.gooseyirl.workers.dev"
        self.session = .shared
    }

    init(baseURL: String, session: URLSession) {
        self.baseURL = baseURL
        self.session = session
    }

    // ── Single squad ──────────────────────────────────────────────────

    func shareSquad(name: String, athletes: [AthleteRef]) async throws -> String {
        guard let url = URL(string: "\(baseURL)/squads") else { throw URLError(.badURL) }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(SharedSquad(name: name, athletes: athletes))

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 201 else {
            throw URLError(.badServerResponse)
        }
        struct CodeResponse: Decodable { let code: String }
        return try JSONDecoder().decode(CodeResponse.self, from: data).code
    }

    /// Returns nil if the code was not found (404), throws on other errors.
    func tryImportSquad(code: String) async throws -> SharedSquad? {
        guard let url = URL(string: "\(baseURL)/squads/\(code.uppercased())") else { throw URLError(.badURL) }
        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        if http.statusCode == 404 { return nil }
        guard http.statusCode == 200 else { throw URLError(.badServerResponse) }
        return try JSONDecoder().decode(SharedSquad.self, from: data)
    }

    // ── Bundle (multiple squads) ──────────────────────────────────────

    func shareBundle(squads: [SharedSquad]) async throws -> String {
        guard let url = URL(string: "\(baseURL)/bundles") else { throw URLError(.badURL) }
        struct Payload: Encodable { let squads: [SharedSquad] }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(Payload(squads: squads))

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 201 else {
            throw URLError(.badServerResponse)
        }
        struct CodeResponse: Decodable { let code: String }
        return try JSONDecoder().decode(CodeResponse.self, from: data).code
    }

    /// Returns nil if the code was not found (404), throws on other errors.
    func tryImportBundle(code: String) async throws -> SharedBundle? {
        guard let url = URL(string: "\(baseURL)/bundles/\(code.uppercased())") else { throw URLError(.badURL) }
        let (data, response) = try await session.data(from: url)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        if http.statusCode == 404 { return nil }
        guard http.statusCode == 200 else { throw URLError(.badServerResponse) }
        return try JSONDecoder().decode(SharedBundle.self, from: data)
    }
}
