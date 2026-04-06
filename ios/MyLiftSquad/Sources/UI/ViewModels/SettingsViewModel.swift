import SwiftUI
import SwiftData
import UniformTypeIdentifiers

@MainActor
@Observable
final class SettingsViewModel {
    var statusMessage: String?
    var showRestoreConfirm = false
    var pendingRestoreURL: URL?
    var showDocumentPicker = false
    var isExporting = false
    var exportDocument: BackupDocument?

    private let modelContext: ModelContext

    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }

    func exportBackup() {
        do {
            let squads = try modelContext.fetch(FetchDescriptor<Squad>(
                predicate: #Predicate { !$0.isSystem }
            ))
            let systemSquads = try modelContext.fetch(FetchDescriptor<Squad>(
                predicate: #Predicate { $0.isSystem }
            ))

            var orphanedFavs: [AthleteBackup] = []
            for sq in systemSquads {
                for a in sq.athletes where a.isFavourite {
                    orphanedFavs.append(toBackup(a))
                }
            }

            let squadBackups = squads.map { sq in
                SquadBackup(
                    name: sq.name,
                    athletes: sq.athletes.map { toBackup($0) }
                )
            }

            let backup = BackupFile(version: 1, squads: squadBackups, orphanedFavourites: orphanedFavs)
            let data = try JSONEncoder().encode(backup)
            exportDocument = BackupDocument(data: data)
            isExporting = true
        } catch {
            statusMessage = "Export failed: \(error.localizedDescription)"
        }
    }

    func restoreBackup(from url: URL) {
        do {
            guard url.startAccessingSecurityScopedResource() else {
                statusMessage = "Cannot access file"
                return
            }
            defer { url.stopAccessingSecurityScopedResource() }

            let data = try Data(contentsOf: url)
            let backup = try JSONDecoder().decode(BackupFile.self, from: data)

            // Wipe all data
            try modelContext.delete(model: Squad.self)
            try modelContext.delete(model: Athlete.self)
            try modelContext.delete(model: CompetitionEntry.self)

            // Restore squads
            for squadBackup in backup.squads {
                let squad = Squad(name: squadBackup.name)
                modelContext.insert(squad)
                for ab in squadBackup.athletes {
                    let athlete = fromBackup(ab)
                    athlete.squad = squad
                    modelContext.insert(athlete)
                }
            }

            // Restore orphaned favourites to a system squad
            if !backup.orphanedFavourites.isEmpty {
                let systemSquad = Squad(name: "Favourites", isSystem: true)
                modelContext.insert(systemSquad)
                for ab in backup.orphanedFavourites {
                    let athlete = fromBackup(ab)
                    athlete.squad = systemSquad
                    modelContext.insert(athlete)
                }
            }

            try modelContext.save()
            statusMessage = "Backup restored successfully"
        } catch {
            statusMessage = "Restore failed: \(error.localizedDescription)"
        }
    }

    private func toBackup(_ a: Athlete) -> AthleteBackup {
        AthleteBackup(
            name: a.name,
            slug: a.slug,
            country: a.country,
            federation: a.federation,
            bestSquatKg: a.bestSquatKg,
            bestBenchKg: a.bestBenchKg,
            bestDeadliftKg: a.bestDeadliftKg,
            bestTotalKg: a.bestTotalKg,
            weightClass: a.weightClass,
            equipment: a.equipment,
            lastCompDate: a.lastCompDate,
            gender: a.gender,
            isFavourite: a.isFavourite
        )
    }

    private func fromBackup(_ ab: AthleteBackup) -> Athlete {
        Athlete(
            name: ab.name,
            slug: ab.slug,
            country: ab.country,
            federation: ab.federation,
            bestSquatKg: ab.bestSquatKg,
            bestBenchKg: ab.bestBenchKg,
            bestDeadliftKg: ab.bestDeadliftKg,
            bestTotalKg: ab.bestTotalKg,
            weightClass: ab.weightClass,
            equipment: ab.equipment,
            lastCompDate: ab.lastCompDate,
            gender: ab.gender,
            isFavourite: ab.isFavourite
        )
    }
}

struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data

    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
