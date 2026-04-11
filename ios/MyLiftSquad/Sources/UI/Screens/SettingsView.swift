import SwiftUI
import SwiftData
import UniformTypeIdentifiers

struct SettingsView: View {
    let modelContext: ModelContext
    @State private var viewModel: SettingsViewModel?
    @State private var showFilePicker = false
    @AppStorage("theme_preference") private var themePreference: String = "system"

    var body: some View {
        Group {
            if let vm = viewModel {
                settingsContent(vm: vm)
            } else {
                ProgressView()
            }
        }
        .navigationTitle("Settings")
        .navigationBarTitleDisplayMode(.large)
        .onAppear {
            if viewModel == nil {
                viewModel = SettingsViewModel(modelContext: modelContext)
            }
        }
    }

    @ViewBuilder
    private func settingsContent(vm: SettingsViewModel) -> some View {
        List {
            Section("Appearance") {
                Picker("Theme", selection: $themePreference) {
                    Text("System").tag("system")
                    Text("Light").tag("light")
                    Text("Dark").tag("dark")
                }
                .pickerStyle(.segmented)
            }

            Section("Data") {
                Button {
                    vm.exportBackup()
                } label: {
                    Label("Export Backup", systemImage: "square.and.arrow.up")
                }

                Button {
                    showFilePicker = true
                } label: {
                    Label("Restore Backup", systemImage: "square.and.arrow.down")
                }
                .foregroundColor(.primary)
            }

            Section("About") {
                HStack {
                    Text("Version")
                    Spacer()
                    Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                        .foregroundColor(.secondary)
                }
                HStack {
                    Text("Data Source")
                    Spacer()
                    Text("OpenPowerlifting")
                        .foregroundColor(.secondary)
                }
            }
        }
        .alert("Restore Backup", isPresented: Binding(get: { vm.showRestoreConfirm }, set: { vm.showRestoreConfirm = $0 })) {
            Button("Restore", role: .destructive) {
                if let url = vm.pendingRestoreURL {
                    vm.restoreBackup(from: url)
                }
            }
            Button("Cancel", role: .cancel) {
                vm.pendingRestoreURL = nil
            }
        } message: {
            Text("This will overwrite all current data. Are you sure?")
        }
        .fileExporter(
            isPresented: Binding(
                get: { vm.isExporting },
                set: { vm.isExporting = $0 }
            ),
            document: vm.exportDocument,
            contentType: .json,
            defaultFilename: "myliftsquad-backup"
        ) { result in
            switch result {
            case .success:
                vm.statusMessage = "Backup exported successfully"
            case .failure(let error):
                vm.statusMessage = "Export failed: \(error.localizedDescription)"
            }
        }
        .fileImporter(
            isPresented: $showFilePicker,
            allowedContentTypes: [.json]
        ) { result in
            switch result {
            case .success(let url):
                vm.pendingRestoreURL = url
                vm.showRestoreConfirm = true
            case .failure(let error):
                vm.statusMessage = "Failed to open file: \(error.localizedDescription)"
            }
        }
        .overlay(alignment: .bottom) {
            if let msg = vm.statusMessage {
                Text(msg)
                    .font(.subheadline)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(Color(.systemBackground).shadow(.drop(radius: 4)))
                    .clipShape(Capsule())
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .onAppear {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 3) {
                            withAnimation { vm.statusMessage = nil }
                        }
                    }
            }
        }
        .animation(.easeInOut, value: vm.statusMessage)
    }
}
