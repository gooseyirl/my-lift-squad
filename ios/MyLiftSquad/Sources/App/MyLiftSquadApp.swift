import SwiftUI
import SwiftData

@main
struct MyLiftSquadApp: App {
    let modelContainer: ModelContainer

    init() {

        do {
            modelContainer = try ModelContainer(
                for: Squad.self, Athlete.self, CompetitionEntry.self
            )
        } catch {
            fatalError("Failed to create ModelContainer: \(error)")
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .modelContainer(modelContainer)
        }
    }
}
