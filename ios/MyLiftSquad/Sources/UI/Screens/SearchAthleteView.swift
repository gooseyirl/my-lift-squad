import SwiftUI
import SwiftData

struct SearchAthleteView: View {
    let squad: Squad
    let modelContext: ModelContext
    let onDismiss: () -> Void

    @State private var viewModel: SearchViewModel?
    @FocusState private var isSearchFocused: Bool

    var body: some View {
        NavigationStack {
            Group {
                if let vm = viewModel {
                    searchContent(vm: vm)
                } else {
                    ProgressView()
                }
            }
            .navigationTitle("Add Athlete")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button("Cancel") { onDismiss() }
                }
            }
        }
        .onAppear {
            if viewModel == nil {
                viewModel = SearchViewModel(squad: squad, modelContext: modelContext)
            }
            isSearchFocused = true
        }
    }

    @ViewBuilder
    private func searchContent(vm: SearchViewModel) -> some View {
        VStack(spacing: 0) {
            // Search bar
            HStack {
                Image(systemName: "magnifyingglass")
                    .foregroundColor(.secondary)
                TextField("Search OpenPowerlifting...", text: Binding(
                    get: { vm.query },
                    set: { vm.query = $0; vm.onQueryChanged() }
                ))
                .focused($isSearchFocused)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.words)

                if !vm.query.isEmpty {
                    Button {
                        vm.query = ""
                        vm.results = []
                        vm.canLoadMore = false
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)
            .background(Color(.systemGray6))
            .clipShape(RoundedRectangle(cornerRadius: 10))
            .padding()

            Divider()

            // Results
            if vm.isSearching {
                VStack {
                    Spacer()
                    ProgressView("Searching...")
                    Spacer()
                }
            } else if vm.query.count < 2 {
                VStack {
                    Spacer()
                    Text("Type at least 2 characters to search")
                        .foregroundColor(.secondary)
                    Spacer()
                }
            } else if vm.results.isEmpty {
                VStack {
                    Spacer()
                    Text("No athletes found")
                        .foregroundColor(.secondary)
                    Spacer()
                }
            } else {
                List {
                    ForEach(vm.results, id: \.slug) { athlete in
                        SearchResultRow(athlete: athlete, isAdded: vm.addedSlugs.contains(athlete.slug)) {
                            vm.addAthlete(athlete)
                        }
                    }

                    if vm.canLoadMore || vm.isLoadingMore {
                        HStack {
                            Spacer()
                            if vm.isLoadingMore {
                                ProgressView()
                                    .padding(.vertical, 8)
                            } else {
                                Button("See more results") {
                                    vm.loadMore()
                                }
                                .padding(.vertical, 8)
                            }
                            Spacer()
                        }
                        .listRowSeparator(.hidden)
                    }
                }
                .listStyle(.plain)
                .alert("No more results", isPresented: Binding(
                    get: { vm.showNoMoreResults },
                    set: { vm.showNoMoreResults = $0 }
                )) {
                    Button("OK", role: .cancel) { vm.showNoMoreResults = false }
                } message: {
                    Text("There are no more athletes matching \"\(vm.query)\".")
                }
            }
        }
    }
}

struct SearchResultRow: View {
    let athlete: OplAthlete
    let isAdded: Bool
    let onAdd: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(athlete.name)
                    .font(.body)
                    .fontWeight(.medium)

                HStack(spacing: 8) {
                    if !athlete.federation.isEmpty {
                        Text(athlete.federation)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    if !athlete.country.isEmpty {
                        Text(athlete.country)
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                    if athlete.bestTotalKg > 0 {
                        Text("Total: \(Int(athlete.bestTotalKg)) kg")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }
            }

            Spacer()

            if isAdded {
                Text("Added")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color(.systemGray5))
                    .clipShape(Capsule())
            } else {
                Button(action: onAdd) {
                    Image(systemName: "plus.circle.fill")
                        .font(.title2)
                        .foregroundColor(.accentColor)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 4)
    }
}
