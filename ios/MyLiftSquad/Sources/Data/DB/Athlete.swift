import SwiftData
import Foundation

@Model
final class Athlete {
    var id: UUID
    var name: String
    var slug: String
    var country: String
    var federation: String
    var bestSquatKg: Double
    var bestBenchKg: Double
    var bestDeadliftKg: Double
    var bestTotalKg: Double
    var weightClass: String
    var equipment: String
    var lastCompDate: String
    var gender: String
    var isFavourite: Bool
    var squad: Squad?

    init(
        name: String,
        slug: String,
        country: String,
        federation: String,
        bestSquatKg: Double = 0,
        bestBenchKg: Double = 0,
        bestDeadliftKg: Double = 0,
        bestTotalKg: Double = 0,
        weightClass: String = "",
        equipment: String = "",
        lastCompDate: String = "",
        gender: String = "",
        isFavourite: Bool = false
    ) {
        self.id = UUID()
        self.name = name
        self.slug = slug
        self.country = country
        self.federation = federation
        self.bestSquatKg = bestSquatKg
        self.bestBenchKg = bestBenchKg
        self.bestDeadliftKg = bestDeadliftKg
        self.bestTotalKg = bestTotalKg
        self.weightClass = weightClass
        self.equipment = equipment
        self.lastCompDate = lastCompDate
        self.gender = gender
        self.isFavourite = isFavourite
    }
}
