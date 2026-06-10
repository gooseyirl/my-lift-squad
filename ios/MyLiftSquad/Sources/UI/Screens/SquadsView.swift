import SwiftUI
import SwiftData

struct SquadsView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var viewModel: SquadsViewModel?
    @State private var showFABMenu = false
    @State private var navigateToSettings = false
    @State private var navigatingToSquad: Squad?
    @State private var squadToDelete: Squad?
    @State private var showDeleteConfirm = false
    @State private var showDeleteSelectedConfirm = false
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
            .navigationTitle(viewModel?.isSelecting == true
                ? "\(viewModel?.selectedSquadIDs.count ?? 0) selected"
                : "My Lift Squad")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                if let vm = viewModel, vm.isSelecting {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Cancel") { vm.cancelSelection() }
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        HStack(spacing: 4) {
                            Button {
                                vm.shareSelected()
                            } label: {
                                Image(systemName: "square.and.arrow.up")
                            }
                            .disabled(vm.selectedSquadIDs.isEmpty || vm.isSharing)

                            Button {
                                showDeleteSelectedConfirm = true
                            } label: {
                                Image(systemName: "trash")
                                    .foregroundColor(.red)
                            }
                            .disabled(vm.selectedSquadIDs.isEmpty || vm.isSharing)
                        }
                    }
                } else {
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Image("app_icon")
                            .resizable()
                            .scaledToFit()
                            .frame(width: 40, height: 40)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                    }
                }
            }
            .navigationDestination(isPresented: $navigateToSettings) {
                SettingsView(modelContext: modelContext)
            }
            .navigationDestination(item: $navigatingToSquad) { squad in
                SquadDetailView(squad: squad)
            }
            .navigationDestination(isPresented: Binding(
                get: { viewModel?.newlyCreatedSquad != nil },
                set: { if !$0 { viewModel?.newlyCreatedSquad = nil } }
            )) {
                if let squad = viewModel?.newlyCreatedSquad {
                    SquadDetailView(squad: squad, autoOpenSearch: true)
                }
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
            .alert("Delete Squads", isPresented: $showDeleteSelectedConfirm) {
                Button("Delete", role: .destructive) { viewModel?.deleteSelected() }
                Button("Cancel", role: .cancel) {}
            } message: {
                let count = viewModel?.selectedSquadIDs.count ?? 0
                Text("Delete \(count) squad\(count == 1 ? "" : "s") and all their athletes? This cannot be undone.")
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
        .onChange(of: navigatingToSquad) { _, newValue in
            if newValue == nil { viewModel?.loadData() }
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
                            SquadRowView(
                                squad: squad,
                                isSelecting: vm.isSelecting,
                                isSelected: vm.selectedSquadIDs.contains(squad.id),
                                onTap: {
                                    if vm.isSelecting {
                                        vm.toggleSelection(squad)
                                    } else {
                                        navigatingToSquad = squad
                                    }
                                },
                                onLongPress: {
                                    if vm.isSelecting {
                                        vm.toggleSelection(squad)
                                    } else {
                                        vm.beginSelection(squad)
                                    }
                                },
                                onDelete: {
                                    squadToDelete = squad
                                    showDeleteConfirm = true
                                },
                                onRename: { vm.beginRename(squad) }
                            )
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

            // FAB area — hidden during multi-select
            if !vm.isSelecting { VStack(alignment: .trailing, spacing: 12) {
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
            } // end if !vm.isSelecting
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
                .presentationBackground(Color(.systemBackground))
            }
        }
        .sheet(isPresented: Binding(
            get: { vm.showImportDialog },
            set: { if !$0 { vm.clearImportDialog() } }
        )) {
            ImportSquadSheet(viewModel: vm)
                .presentationDetents([.height(380)])
                .presentationDragIndicator(.visible)
        }
        .overlay(alignment: .bottom) {
            if let name = vm.importedSquadName {
                Text("\(name) imported successfully")
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
        // Share loading overlay
        .overlay {
            if vm.isSharing {
                ZStack {
                    Color.black.opacity(0.3).ignoresSafeArea()
                    VStack(spacing: 16) {
                        ProgressView()
                            .scaleEffect(1.3)
                        Text("Uploading squads…")
                            .font(.subheadline)
                    }
                    .padding(28)
                    .background(Color(.systemBackground))
                    .clipShape(RoundedRectangle(cornerRadius: 16))
                    .shadow(radius: 8)
                }
            }
        }
        // Share code sheet (alert can't show images, so we use a sheet for the QR)
        .sheet(isPresented: Binding(
            get: { vm.shareCode != nil },
            set: { if !$0 { vm.shareCode = nil } }
        )) {
            if let code = vm.shareCode {
                ShareCodeSheet(code: code) { vm.shareCode = nil }
                    .presentationDetents([.medium])
                    .presentationDragIndicator(.visible)
            }
        }
        // Share error
        .alert("Share Failed", isPresented: Binding(
            get: { vm.shareError != nil },
            set: { if !$0 { vm.shareError = nil } }
        )) {
            Button("OK", role: .cancel) { vm.shareError = nil }
        } message: {
            Text(vm.shareError ?? "")
        }
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
                    Text("Keep Up With Your Gym Crew")
                        .font(.title2)
                        .fontWeight(.bold)
                        .multilineTextAlignment(.center)
                    Text("Build squads to follow your training partners and friends — track their competition PRs and results in one place.")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                }

                VStack(alignment: .leading, spacing: 14) {
                    EmptyStateFeatureRow(icon: "trophy.fill",            text: "See your training partners' squat, bench and deadlift PRs")
                    EmptyStateFeatureRow(icon: "calendar",               text: "Browse their full competition history")
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
    let isSelecting: Bool
    let isSelected: Bool
    let onTap: () -> Void
    let onLongPress: () -> Void
    let onDelete: () -> Void
    let onRename: () -> Void

    @State private var showOptions = false

    var body: some View {
        HStack {
            if isSelecting {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundColor(isSelected ? .accentColor : .secondary)
                    .font(.title3)
                    .padding(.trailing, 4)
            }

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

            if isSelecting {
                EmptyView()
            } else {
                Button {
                    showOptions = true
                } label: {
                    Image(systemName: "ellipsis")
                        .foregroundColor(.secondary)
                        .padding(.leading, 8)
                }
                .buttonStyle(.plain)
                .confirmationDialog(squad.name, isPresented: $showOptions, titleVisibility: .visible) {
                    Button("Rename") { onRename() }
                    Button("Delete", role: .destructive) { onDelete() }
                }

                Image(systemName: "chevron.right")
                    .foregroundColor(.secondary)
                    .font(.caption)
            }
        }
        .padding(.horizontal)
        .padding(.vertical, 12)
        .background(isSelected ? Color.accentColor.opacity(0.12) : Color(.systemBackground))
        .contentShape(Rectangle())
        .onTapGesture { onTap() }
        .onLongPressGesture { onLongPress() }

        Divider().padding(.leading)
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
    @State private var showQRScanner = false

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

                    Button {
                        isCodeFocused = false
                        showQRScanner = true
                    } label: {
                        Label("Scan QR Code", systemImage: "qrcode.viewfinder")
                            .font(.subheadline)
                    }
                    .padding(.top, 4)
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
        .sheet(isPresented: $showQRScanner) {
            QRCodeScannerSheet { scannedCode in
                let code = scannedCode.uppercased()
                viewModel.importCode = String(code.prefix(6))
                if viewModel.importCode.count == 6 {
                    viewModel.importSquad()
                }
            }
        }
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
