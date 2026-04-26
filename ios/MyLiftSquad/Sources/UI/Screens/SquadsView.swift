import SwiftUI
import SwiftData

struct SquadsView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: SquadsViewModel?
    @State private var showFABMenu = false
    @State private var navigateToSettings = false
    @State private var squadToDelete: Squad?
    @State private var showDeleteConfirm = false
    @State private var showSupportAlert = false
    private let store = StoreKitManager.shared

    var body: some View {
        NavigationStack {
            Group {
                if let vm = viewModel {
                    squadsContent(vm: vm)
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("My Lift Squad")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Image("app_icon")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 40, height: 40)
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                }
            }
            .navigationDestination(isPresented: $navigateToSettings) {
                SettingsView(modelContext: modelContext)
            }
            .alert("New Squad", isPresented: Binding(
                get: { viewModel?.showNewSquadDialog ?? false },
                set: { viewModel?.showNewSquadDialog = $0 }
            )) {
                TextField("Squad name", text: Binding(
                    get: { viewModel?.newSquadName ?? "" },
                    set: { viewModel?.newSquadName = $0 }
                ))
                Button("Create") { viewModel?.createSquad() }
                Button("Cancel", role: .cancel) {
                    viewModel?.showNewSquadDialog = false
                    viewModel?.newSquadName = ""
                    viewModel?.errorMessage = nil
                }
            }
            .alert("Name already taken", isPresented: Binding(
                get: { viewModel?.errorMessage != nil },
                set: { if !$0 {
                    viewModel?.errorMessage = nil
                    viewModel?.showNewSquadDialog = true
                }}
            )) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(viewModel?.errorMessage ?? "")
            }
            .alert("Delete Squad", isPresented: $showDeleteConfirm) {
                Button("Delete", role: .destructive) {
                    if let squad = squadToDelete { viewModel?.deleteSquad(squad) }
                    squadToDelete = nil
                }
                Button("Cancel", role: .cancel) { squadToDelete = nil }
            } message: {
                Text("Are you sure you want to delete this squad and all its athletes?")
            }
            .alert("Rename Squad", isPresented: Binding(
                get: { viewModel?.showRenameDialog ?? false },
                set: { if !$0 { viewModel?.clearRenameDialog() } }
            )) {
                TextField("Squad name", text: Binding(
                    get: { viewModel?.renameNewName ?? "" },
                    set: { viewModel?.renameNewName = $0 }
                ))
                Button("Rename") { viewModel?.confirmRename() }
                Button("Cancel", role: .cancel) { viewModel?.clearRenameDialog() }
            } message: {
                if let err = viewModel?.renameErrorMessage {
                    Text(err)
                }
            }
            .alert("Support Developer", isPresented: $showSupportAlert) {
                Button("Support \u{2764}\u{FE0F}") { Task { await store.purchase() } }
                Button("Restore Purchase") { Task { await store.restorePurchases() } }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("MyLiftSquad is free and ad-free. If you enjoy using it, consider supporting development with a one-time purchase.")
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = SquadsViewModel(modelContext: modelContext)
            }
            viewModel?.loadData()
        }
    }

    @ViewBuilder
    private func squadsContent(vm: SquadsViewModel) -> some View {
        ZStack(alignment: .bottomTrailing) {
            ScrollView {
                LazyVStack(alignment: .leading, spacing: 0) {
                    // Favourites Section
                    if !vm.favourites.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Favourites")
                                .font(.headline)
                                .padding(.horizontal)
                                .padding(.top, 16)
                                .padding(.bottom, 4)

                            ForEach(vm.favourites) { athlete in
                                FavouriteCard(athlete: athlete) {
                                    vm.showDetail(for: athlete)
                                } onUnfavourite: {
                                    vm.unfavourite(athlete)
                                }
                            }

                            Divider().padding(.vertical, 8)
                        }
                    }

                    // Squads List
                    if vm.squads.isEmpty {
                        EmptySquadsView {
                            showFABMenu = false
                            vm.showNewSquadDialog = true
                        }
                    } else {
                        ForEach(vm.squads) { squad in
                            SquadRowView(squad: squad) {
                                squadToDelete = squad
                                showDeleteConfirm = true
                            } onRename: {
                                vm.beginRename(squad)
                            }
                        }
                    }

                }
            }

            // Quote — fixed at bottom-left, content scrolls behind it
            if vm.showQuote && !vm.currentQuote.isEmpty {
                VStack {
                    Spacer()
                    HStack(alignment: .center, spacing: 6) {
                        Image(systemName: "star.fill")
                            .font(.caption)
                            .foregroundColor(.accentColor)
                        Text("\u{201C}\(vm.currentQuote)\u{201D}")
                            .font(.caption)
                            .italic()
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color(.systemBackground).opacity(0.9))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .padding(.leading, 16)
                    .padding(.bottom, 16)
                    .padding(.trailing, 80) // avoid FAB
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .onTapGesture { vm.nextQuote() }
                }
            }

            // FAB area
            VStack(alignment: .trailing, spacing: 12) {
                if showFABMenu {
                    FABMenuItem(icon: "plus.circle", label: "New Squad") {
                        showFABMenu = false
                        vm.showNewSquadDialog = true
                    }
                    FABMenuItem(icon: "arrow.down.circle", label: "Import Squad") {
                        showFABMenu = false
                        vm.showImportDialog = true
                    }
                    FABMenuItem(icon: "gear", label: "Settings") {
                        showFABMenu = false
                        navigateToSettings = true
                    }
                    if !store.isDonated {
                        FABMenuItem(icon: "star.fill", label: "Support Developer") {
                            showFABMenu = false
                            showSupportAlert = true
                        }
                    }
                }

                Button {
                    withAnimation(.spring(duration: 0.3)) {
                        showFABMenu.toggle()
                    }
                } label: {
                    Image(systemName: showFABMenu ? "xmark" : "line.3.horizontal")
                        .font(.title2)
                        .fontWeight(.semibold)
                        .foregroundColor(.white)
                        .frame(width: 56, height: 56)
                        .background(Color.accentColor)
                        .clipShape(Circle())
                        .shadow(radius: 4)
                }
            }
            .padding()
        }
        .contentShape(Rectangle())
        .onTapGesture {
            if showFABMenu { withAnimation { showFABMenu = false } }
        }
        .sheet(isPresented: Binding(
            get: { vm.showFavouriteDetail },
            set: { vm.showFavouriteDetail = $0 }
        )) {
            if let athlete = vm.selectedFavourite {
                AthleteDetailSheet(
                    athlete: athlete,
                    history: vm.favouriteHistory,
                    isLoading: vm.isFavouriteHistoryLoading,
                    onRefresh: { vm.refreshFavouriteAthlete() }
                )
                .presentationDetents([.medium, .large])
                .presentationDragIndicator(.visible)
            }
        }
        .sheet(isPresented: Binding(
            get: { vm.showImportDialog },
            set: { if !$0 { vm.clearImportDialog() } }
        )) {
            ImportSquadSheet(viewModel: vm)
                .presentationDetents([.height(320)])
                .presentationDragIndicator(.visible)
        }
        .overlay(alignment: .bottom) {
            if let name = vm.importedSquadName {
                Text("\"\(name)\" imported successfully")
                    .font(.subheadline)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color(.systemBackground).shadow(.drop(radius: 4)))
                    .clipShape(Capsule())
                    .padding(.bottom, 80)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.easeInOut, value: vm.importedSquadName)
    }
}

struct EmptySquadsView: View {
    let onCreateSquad: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            Spacer(minLength: 40)
            VStack(spacing: 20) {
                Image("app_icon")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 80, height: 80)
                    .clipShape(RoundedRectangle(cornerRadius: 18))

                VStack(spacing: 8) {
                    Text("Track Your Powerlifting Squad")
                        .font(.title2)
                        .fontWeight(.bold)
                        .multilineTextAlignment(.center)
                    Text("Follow athletes, monitor PRs, and keep up with competition results — all in one place.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }

                VStack(alignment: .leading, spacing: 14) {
                    EmptyStateFeatureRow(icon: "trophy.fill",            text: "Track personal records for squat, bench and deadlift")
                    EmptyStateFeatureRow(icon: "calendar",               text: "Browse full competition history")
                    EmptyStateFeatureRow(icon: "square.and.arrow.up",    text: "Share squads with friends using a 6-character code")
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                Button(action: onCreateSquad) {
                    Text("Create Your First Squad")
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
    }
}

private struct EmptyStateFeatureRow: View {
    let icon: String
    let text: String

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .foregroundColor(.accentColor)
                .frame(width: 20)
            Text(text)
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }
}

struct SquadRowView: View {
    let squad: Squad
    let onDelete: () -> Void
    let onRename: () -> Void

    var body: some View {
        NavigationLink {
            SquadDetailView(squad: squad)
        } label: {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(squad.name)
                        .font(.body)
                        .fontWeight(.medium)
                        .foregroundColor(.primary)
                    Text("\(squad.athletes.count) athlete\(squad.athletes.count == 1 ? "" : "s")")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .foregroundColor(.secondary)
                    .font(.caption)
            }
            .padding(.horizontal)
            .padding(.vertical, 12)
            .background(Color(.systemBackground))
        }
        .contextMenu {
            Button {
                onRename()
            } label: {
                Label("Rename Squad", systemImage: "pencil")
            }
            Button(role: .destructive) {
                onDelete()
            } label: {
                Label("Delete Squad", systemImage: "trash")
            }
        }
        Divider()
            .padding(.leading)
    }
}

private func formatKg(_ v: Double) -> String {
    v.truncatingRemainder(dividingBy: 1) == 0 ? "\(Int(v)) kg" : String(format: "%.1f kg", v)
}

struct FavouriteCard: View {
    let athlete: Athlete
    let onTap: () -> Void
    let onUnfavourite: () -> Void

    var body: some View {
        HStack {
            Image(systemName: "star.fill")
                .foregroundColor(AppTheme.gold)
                .font(.caption)
            VStack(alignment: .leading, spacing: 2) {
                Text(athlete.name)
                    .font(.subheadline)
                    .fontWeight(.medium)
                SBDText(squatKg: athlete.bestSquatKg, benchKg: athlete.bestBenchKg, deadliftKg: athlete.bestDeadliftKg)
            }
            Spacer()
            if athlete.bestTotalKg > 0 {
                Text(formatKg(athlete.bestTotalKg))
                    .font(.caption)
                    .fontWeight(.bold)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color.accentColor.opacity(0.15))
                    .foregroundColor(.accentColor)
                    .clipShape(Capsule())
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
        .contextMenu {
            Button {
                onUnfavourite()
            } label: {
                Label("Remove from Favourites", systemImage: "star.slash")
            }
        }
    }
}

struct ImportSquadSheet: View {
    let viewModel: SquadsViewModel
    @FocusState private var isCodeFocused: Bool

    var body: some View {
        VStack(spacing: 20) {
            Text(viewModel.isImporting ? "Importing squad..." : "Import Squad")
                .font(.headline)
                .padding(.top, 8)

            if viewModel.isImporting {
                VStack(spacing: 12) {
                    ProgressView()
                    if let progress = viewModel.importProgress {
                        Text(progress)
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .multilineTextAlignment(.center)
                            .padding(.horizontal)
                    }
                }
                .frame(height: 80)
            } else {
                VStack(spacing: 8) {
                    TextField("6-character code", text: Binding(
                        get: { viewModel.importCode },
                        set: { viewModel.importCode = $0.uppercased().prefix(6).description }
                    ))
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                    .multilineTextAlignment(.center)
                    .font(.system(size: 28, weight: .bold, design: .monospaced))
                    .tracking(4)
                    .focused($isCodeFocused)
                    .padding()
                    .background(Color(.systemGray6))
                    .clipShape(RoundedRectangle(cornerRadius: 10))
                    .padding(.horizontal)

                    if let error = viewModel.importError {
                        Text(error)
                            .font(.caption)
                            .foregroundColor(.red)
                            .padding(.horizontal)
                    }
                }
            }

            if !viewModel.isImporting {
                Button {
                    viewModel.importSquad()
                } label: {
                    Text("Import")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color.accentColor)
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .padding(.horizontal)
                }
                .buttonStyle(.plain)
                .disabled(viewModel.importCode.count != 6)
            }
        }
        .padding(.bottom)
        .onAppear { isCodeFocused = true }
    }
}

struct FABMenuItem: View {
    let icon: String
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Text(label)
                    .font(.subheadline)
                    .foregroundColor(.primary)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color(.systemBackground))
                    .clipShape(Capsule())
                    .shadow(radius: 2)

                Image(systemName: icon)
                    .foregroundColor(.white)
                    .frame(width: 40, height: 40)
                    .background(Color.accentColor.opacity(0.8))
                    .clipShape(Circle())
                    .shadow(radius: 2)
            }
        }
        .transition(.asymmetric(
            insertion: .move(edge: .bottom).combined(with: .opacity),
            removal: .move(edge: .bottom).combined(with: .opacity)
        ))
    }
}
