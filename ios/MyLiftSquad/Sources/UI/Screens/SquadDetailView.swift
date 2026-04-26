import SwiftUI
import SwiftData

struct SquadDetailView: View {
    let squad: Squad
    var autoOpenSearch: Bool = false
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: SquadDetailViewModel?
    @State private var showSearch = false

    var body: some View {
        Group {
            if let vm = viewModel {
                detailContent(vm: vm)
            } else {
                ProgressView()
            }
        }
        .navigationTitle(squad.name)
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 16) {
                    Button {
                        viewModel?.shareSquad()
                    } label: {
                        if viewModel?.isShareLoading == true {
                            ProgressView().scaleEffect(0.8)
                        } else {
                            Image(systemName: "square.and.arrow.up")
                        }
                    }
                    .disabled(viewModel?.isShareLoading == true || viewModel?.athletes.isEmpty == true)

                    Menu {
                        ForEach(AthleteSortOption.allCases, id: \.self) { option in
                            Button {
                                viewModel?.setSortOption(option)
                            } label: {
                                if viewModel?.sortOption == option {
                                    Label(option.rawValue, systemImage: "checkmark")
                                } else {
                                    Text(option.rawValue)
                                }
                            }
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                    }
                    .disabled(viewModel?.athletes.isEmpty == true)

                    Button {
                        Task { await viewModel?.refreshAll() }
                    } label: {
                        if viewModel?.isRefreshing == true {
                            ProgressView().scaleEffect(0.8)
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                    }
                }
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = SquadDetailViewModel(squad: squad, modelContext: modelContext)
            }
            viewModel?.loadAthletes()
            if autoOpenSearch {
                showSearch = true
            }
        }
    }

    @ViewBuilder
    private func detailContent(vm: SquadDetailViewModel) -> some View {
        ZStack(alignment: .bottomTrailing) {
            Group {
                if vm.athletes.isEmpty {
                    VStack(spacing: 0) {
                        Spacer(minLength: 40)
                        VStack(spacing: 20) {
                            Image(systemName: "person.badge.plus")
                                .font(.system(size: 64))
                                .foregroundColor(.secondary.opacity(0.4))
                            VStack(spacing: 8) {
                                Text("No athletes yet")
                                    .font(.title2)
                                    .fontWeight(.bold)
                                Text("Add your training partners and friends to start tracking their progress.")
                                    .font(.subheadline)
                                    .foregroundColor(.secondary)
                                    .multilineTextAlignment(.center)
                            }
                            Button {
                                showSearch = true
                            } label: {
                                Text("Add an Athlete")
                                    .fontWeight(.semibold)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 14)
                                    .background(Color.accentColor)
                                    .foregroundColor(.white)
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                            }
                            .buttonStyle(.plain)
                        }
                        .padding(.horizontal, 32)
                        Spacer(minLength: 40)
                    }
                    .frame(maxWidth: .infinity)
                } else {
                    List {
                        ForEach(vm.athletes) { athlete in
                            Button {
                                vm.showDetail(for: athlete)
                            } label: {
                                AthleteRowView(athlete: athlete)
                                    .contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .listRowBackground(Color(.systemBackground))
                            .contextMenu {
                                Button {
                                    vm.toggleFavourite(athlete)
                                } label: {
                                    Label(
                                        athlete.isFavourite ? "Remove Favourite" : "Add to Favourites",
                                        systemImage: athlete.isFavourite ? "star.slash" : "star"
                                    )
                                }
                                Button(role: .destructive) {
                                    vm.removeAthlete(athlete)
                                } label: {
                                    Label("Remove", systemImage: "trash")
                                }
                            }
                        }
                    }
                    .listStyle(.plain)
                }
            }

            Button {
                showSearch = true
            } label: {
                Image(systemName: "plus")
                    .font(.title2)
                    .fontWeight(.semibold)
                    .foregroundColor(.white)
                    .frame(width: 56, height: 56)
                    .background(Color.accentColor)
                    .clipShape(Circle())
                    .shadow(radius: 4)
            }
            .padding()
        }
        .sheet(isPresented: $showSearch) {
            SearchAthleteView(squad: squad, modelContext: modelContext) {
                vm.loadAthletes()
                showSearch = false
            }
        }
        .sheet(isPresented: Binding(get: { vm.showAthleteDetail }, set: { vm.showAthleteDetail = $0 })) {
            if let athlete = vm.selectedAthlete {
                AthleteDetailSheet(
                    athlete: athlete,
                    history: vm.competitionHistory,
                    isLoading: vm.isLoadingHistory,
                    onRefresh: { vm.refreshSelectedAthlete() }
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
            }
        }
        .sheet(isPresented: Binding(
            get: { vm.shareCode != nil },
            set: { if !$0 { vm.dismissShareCode() } }
        )) {
            ShareCodeSheet(code: vm.shareCode ?? "") {
                vm.dismissShareCode()
            }
            .presentationDetents([.height(260)])
            .presentationDragIndicator(.visible)
        }
        .alert("Share Failed", isPresented: Binding(
            get: { vm.shareError != nil },
            set: { if !$0 { vm.shareError = nil } }
        )) {
            Button("OK", role: .cancel) {}
        } message: {
            Text(vm.shareError ?? "")
        }
        .overlay(alignment: .bottom) {
            if vm.showSquadFullMessage {
                Text("Squad is full (max 30 athletes)")
                    .font(.subheadline)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color(.systemBackground).shadow(.drop(radius: 4)))
                    .clipShape(Capsule())
                    .padding(.bottom, 80)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                            withAnimation { vm.showSquadFullMessage = false }
                        }
                    }
            }
        }
        .animation(.easeInOut, value: vm.showSquadFullMessage)
        .overlay(alignment: .bottom) {
            if vm.showMaxFavsMessage {
                Text("Maximum 3 favourites allowed")
                    .font(.subheadline)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color(.systemBackground).shadow(.drop(radius: 4)))
                    .clipShape(Capsule())
                    .padding(.bottom, 80)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                            withAnimation { vm.showMaxFavsMessage = false }
                        }
                    }
            }
        }
        .animation(.easeInOut, value: vm.showMaxFavsMessage)
    }
}

struct ShareCodeSheet: View {
    let code: String
    let onDismiss: () -> Void
    @State private var copied = false

    var body: some View {
        VStack(spacing: 24) {
            Text("Share Code")
                .font(.headline)
                .padding(.top, 8)

            Text(code)
                .font(.system(size: 42, weight: .bold, design: .monospaced))
                .tracking(6)
                .foregroundColor(.accentColor)

            Text("Share this code with others so they can import your squad. It expires in 30 days.")
                .font(.caption)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal)

            Button {
                UIPasteboard.general.string = code
                copied = true
                DispatchQueue.main.asyncAfter(deadline: .now() + 2) { copied = false }
            } label: {
                Label(copied ? "Copied!" : "Copy Code", systemImage: copied ? "checkmark" : "doc.on.doc")
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(Color.accentColor)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    .padding(.horizontal)
            }
            .buttonStyle(.plain)
        }
        .padding(.bottom)
    }
}

struct AthleteRowView: View {
    let athlete: Athlete

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                HStack(spacing: 6) {
                    Text(athlete.name)
                        .font(.body)
                        .fontWeight(.medium)
                    if athlete.isFavourite {
                        Image(systemName: "star.fill")
                            .foregroundColor(AppTheme.gold)
                            .font(.caption)
                    }
                }
                // Federation in accent color, followed by gender/weight class/equipment in secondary
                let metaParts = [athlete.federation, athlete.gender, athlete.weightClass, athlete.equipment]
                    .filter { !$0.isEmpty }
                if !metaParts.isEmpty {
                    HStack(spacing: 0) {
                        ForEach(Array(metaParts.enumerated()), id: \.offset) { index, part in
                            if index > 0 {
                                Text(" - ").font(.caption).foregroundColor(.secondary)
                            }
                            Text(part)
                                .font(.caption)
                                .foregroundColor(index == 0 ? .accentColor : .secondary)
                        }
                    }
                }
                SBDText(squatKg: athlete.bestSquatKg, benchKg: athlete.bestBenchKg, deadliftKg: athlete.bestDeadliftKg)
            }
            Spacer()
            if athlete.bestTotalKg > 0 {
                Text("\(Int(athlete.bestTotalKg)) kg")
                    .font(.subheadline)
                    .fontWeight(.semibold)
                    .foregroundColor(.accentColor)
            }
        }
        .padding(.vertical, 4)
    }
}
