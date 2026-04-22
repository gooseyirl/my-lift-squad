import Foundation

struct AthleteRef: Codable {
    let name: String
    let slug: String
}

struct SharedSquad: Decodable {
    let name: String
    let athletes: [AthleteRef]
}

actor ShareApiService {
    static let shared = ShareApiService()
    private let baseURL = "https://myliftsquad-api.gooseyirl.workers.dev"

    private init() {}

    func shareSquad(name: String, athletes: [AthleteRef]) async throws -> String {
        guard let url = URL(string: "\(baseURL)/squads") else { throw URLError(.badURL) }
        struct Payload: Encodable {
            let name: String
            let athletes: [AthleteRef]
        }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONEncoder().encode(Payload(name: name, athletes: athletes))

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, http.statusCode == 201 else {
            if let http = response as? HTTPURLResponse, http.statusCode >= 400,
               let body = try? JSONDecoder().decode([String: String].self, from: data),
               let msg = body["error"] {
                throw NSError(domain: "ShareAPI", code: http.statusCode,
                              userInfo: [NSLocalizedDescriptionKey: msg])
            }
            throw URLError(.badServerResponse)
        }
        struct CodeResponse: Decodable { let code: String }
        return try JSONDecoder().decode(CodeResponse.self, from: data).code
    }

    func importSquad(code: String) async throws -> SharedSquad {
        guard let url = URL(string: "\(baseURL)/squads/\(code)") else { throw URLError(.badURL) }
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
        if http.statusCode == 404 {
            throw NSError(domain: "ShareAPI", code: 404,
                          userInfo: [NSLocalizedDescriptionKey: "Squad not found or link has expired"])
        }
        guard http.statusCode == 200 else { throw URLError(.badServerResponse) }
        return try JSONDecoder().decode(SharedSquad.self, from: data)
    }
}
