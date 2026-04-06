import SwiftData
import Foundation

@Model
final class CompetitionEntry {
    var id: UUID
    var athleteSlug: String
    var date: String
    var meetName: String
    var federation: String
    var equipment: String
    var division: String
    var weightClassKg: String
    var bodyweightKg: Double
    var best3SquatKg: Double
    var best3BenchKg: Double
    var best3DeadliftKg: Double
    var totalKg: Double
    var place: String
    var dots: Double
    var meetCountry: String
    var meetTown: String

    init(
        athleteSlug: String,
        date: String,
        meetName: String,
        federation: String,
        equipment: String = "",
        division: String = "",
        weightClassKg: String = "",
        bodyweightKg: Double = 0,
        best3SquatKg: Double = 0,
        best3BenchKg: Double = 0,
        best3DeadliftKg: Double = 0,
        totalKg: Double = 0,
        place: String = "",
        dots: Double = 0,
        meetCountry: String = "",
        meetTown: String = ""
    ) {
        self.id = UUID()
        self.athleteSlug = athleteSlug
        self.date = date
        self.meetName = meetName
        self.federation = federation
        self.equipment = equipment
        self.division = division
        self.weightClassKg = weightClassKg
        self.bodyweightKg = bodyweightKg
        self.best3SquatKg = best3SquatKg
        self.best3BenchKg = best3BenchKg
        self.best3DeadliftKg = best3DeadliftKg
        self.totalKg = totalKg
        self.place = place
        self.dots = dots
        self.meetCountry = meetCountry
        self.meetTown = meetTown
    }
}
