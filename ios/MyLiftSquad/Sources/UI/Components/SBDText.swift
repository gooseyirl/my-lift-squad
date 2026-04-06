import SwiftUI

/// Renders S/B/D personal bests with the lift letter in accent color and the value in secondary color.
struct SBDText: View {
    let squatKg: Double
    let benchKg: Double
    let deadliftKg: Double

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let parts: [(String, Double)] = [("S", squatKg), ("B", benchKg), ("D", deadliftKg)]
            .filter { $0.1 > 0 }

        if parts.isEmpty {
            Text("No lifts recorded")
                .font(.caption)
                .foregroundColor(.secondary)
        } else {
            HStack(spacing: 8) {
                ForEach(parts, id: \.0) { letter, kg in
                    HStack(spacing: 2) {
                        Text(letter)
                            .font(.caption)
                            .foregroundColor(.accentColor)
                        Text(formatKg(kg))
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }
        }
    }

    private func formatKg(_ v: Double) -> String {
        v.truncatingRemainder(dividingBy: 1) == 0 ? "\(Int(v)) kg" : String(format: "%.1f kg", v)
    }
}
