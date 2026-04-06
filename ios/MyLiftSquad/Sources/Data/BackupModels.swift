import Foundation

struct BackupFile: Codable {
    let version: Int
    let squads: [SquadBackup]
    let orphanedFavourites: [AthleteBackup]
}

struct SquadBackup: Codable {
    let name: String
    let athletes: [AthleteBackup]
}

struct AthleteBackup: Codable {
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
    let isFavourite: Bool
}
