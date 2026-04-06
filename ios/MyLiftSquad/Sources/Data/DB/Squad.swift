import SwiftData
import Foundation

@Model
final class Squad {
    var id: UUID
    var name: String
    var isSystem: Bool
    @Relationship(deleteRule: .cascade) var athletes: [Athlete]

    init(name: String, isSystem: Bool = false) {
        self.id = UUID()
        self.name = name
        self.isSystem = isSystem
        self.athletes = []
    }
}
