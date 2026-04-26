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
        .overlay(alignment: .bottom) {
            if viewModel?.showSquadFullMessage == true {
                Text("Squad is full (max 30 athletes)")
                    .font(.subheadline)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color(.systemBackground).shadow(.drop(radius: 4)))
                    .clipShape(Capsule())
                    .padding(.bottom, 16)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
                            withAnimation { viewModel?.showSquadFullMessage = false }
                        }
                    }
            }
        }
        .animation(.easeInOut, value: viewModel?.showSquadFullMessage)
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
                    VStack(spacing: 6) {
                        Text("Search OpenPowerlifting records")
                            .font(.subheadline)
                            .fontWeight(.medium)
                        Text("Type at least 2 characters to search")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
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

                    HStack {
                        Spacer()
                        if vm.isLoadingMore {
                            ProgressView()
                                .padding(.vertical, 8)
                        } else if vm.canLoadMore {
                            Button("See more results") {
                                vm.loadMore()
                            }
                            .padding(.vertical, 8)
                        } else if vm.showNoMoreResults {
                            Text("No more results")
                                .font(.footnote)
                                .foregroundColor(.secondary)
                                .padding(.vertical, 8)
                        }
                        Spacer()
                    }
                    .listRowSeparator(.hidden)
                }
                .listStyle(.plain)
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
