import SwiftUI

struct AthleteDetailSheet: View {
    let athlete: Athlete
    let history: [CompetitionResult]
    let isLoading: Bool
    var onRefresh: (() -> Void)? = nil

    var body: some View {
        NavigationStack {
            List {
                // Header info
                Section {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(athlete.name)
                            .font(.title2)
                            .fontWeight(.bold)

                        HStack(spacing: 16) {
                            if !athlete.federation.isEmpty {
                                InfoChip(text: athlete.federation)
                            }
                            if !athlete.country.isEmpty {
                                InfoChip(text: athlete.country)
                            }
                            if !athlete.weightClass.isEmpty {
                                InfoChip(text: athlete.weightClass + "kg")
                            }
                        }

                        if !athlete.equipment.isEmpty {
                            InfoChip(text: athlete.equipment)
                        }
                    }
                    .padding(.vertical, 4)
                }

                // Best lifts
                Section("Best Lifts") {
                    HStack(spacing: 0) {
                        LiftStatView(label: "Squat", value: athlete.bestSquatKg)
                        LiftStatView(label: "Bench", value: athlete.bestBenchKg)
                        LiftStatView(label: "Deadlift", value: athlete.bestDeadliftKg)
                        LiftStatView(label: "Total", value: athlete.bestTotalKg)
                    }
                }

                // Competition history
                Section("Competition History") {
                    if isLoading && history.isEmpty {
                        HStack {
                            ProgressView()
                                .scaleEffect(0.8)
                            Text("Loading history...")
                                .foregroundColor(.secondary)
                        }
                    } else if history.isEmpty {
                        Text("No competitions found")
                            .foregroundColor(.secondary)
                    } else {
                        ForEach(history) { result in
                            CompetitionRowView(result: result)
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button {
                        onRefresh?()
                    } label: {
                        if isLoading {
                            ProgressView().scaleEffect(0.8)
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                    }
                    .disabled(isLoading)
                }
            }
        }
    }
}

struct LiftStatView: View {
    let label: String
    let value: Double

    var body: some View {
        VStack(spacing: 4) {
            Text(value > 0 ? "\(Int(value))" : "—")
                .font(.title3)
                .fontWeight(.bold)
                .foregroundColor(value > 0 ? .primary : .secondary)
            Text(label)
                .font(.caption2)
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}

struct InfoChip: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color(.systemGray5))
            .clipShape(Capsule())
    }
}

struct CompetitionRowView: View {
    let result: CompetitionResult

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text(result.meetName)
                    .font(.subheadline)
                    .fontWeight(.medium)
                Spacer()
                Text(result.date)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }

            HStack(spacing: 8) {
                if !result.meetTown.isEmpty || !result.meetCountry.isEmpty {
                    let location = [result.meetTown, result.meetCountry]
                        .filter { !$0.isEmpty }
                        .joined(separator: ", ")
                    Text(location)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
                if !result.place.isEmpty {
                    Text(result.place == "1" ? "🥇" : result.place == "2" ? "🥈" : result.place == "3" ? "🥉" : result.place)
                        .font(.caption)
                        .fontWeight(.medium)
                }
            }

            HStack(spacing: 0) {
                if result.best3SquatKg > 0 {
                    Text("SQ \(Int(result.best3SquatKg))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.trailing, 8)
                }
                if result.best3BenchKg > 0 {
                    Text("BP \(Int(result.best3BenchKg))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.trailing, 8)
                }
                if result.best3DeadliftKg > 0 {
                    Text("DL \(Int(result.best3DeadliftKg))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.trailing, 8)
                }
                if result.totalKg > 0 {
                    Text("Total \(Int(result.totalKg)) kg")
                        .font(.caption)
                        .fontWeight(.medium)
                }
            }
        }
        .padding(.vertical, 2)
    }
}
