import SwiftUI
import SwiftData

@main
struct MyLiftSquadApp: App {
    let modelContainer: ModelContainer
    @AppStorage("theme_preference") private var themePreference: String = "system"

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
                .preferredColorScheme(colorScheme(for: themePreference))
        }
    }

    private func colorScheme(for preference: String) -> ColorScheme? {
        switch preference {
        case "light": return .light
        case "dark": return .dark
        default: return nil
        }
    }
}
