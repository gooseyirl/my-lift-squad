import StoreKit
import Foundation

@MainActor
@Observable
final class StoreKitManager {
    static let shared = StoreKitManager()

    var isDonated: Bool = false
    var isLoading: Bool = false
    var errorMessage: String?

    private let productID = "support_developer"

    private init() {
        isDonated = UserDefaults.standard.bool(forKey: "donated")
        Task { await listenForTransactions() }
    }

    func purchase() async {
        isLoading = true
        defer { isLoading = false }
        do {
            let products = try await Product.products(for: [productID])
            guard let product = products.first else {
                errorMessage = "Product not available"
                return
            }
            let result = try await product.purchase()
            switch result {
            case .success(let verification):
                switch verification {
                case .verified(let transaction):
                    await transaction.finish()
                    markDonated()
                case .unverified:
                    errorMessage = "Purchase could not be verified"
                }
            case .userCancelled:
                break
            case .pending:
                break
            @unknown default:
                break
            }
        } catch {
            errorMessage = "Purchase failed: \(error.localizedDescription)"
        }
    }

    func restorePurchases() async {
        do {
            try await AppStore.sync()
            for await result in Transaction.currentEntitlements {
                if case .verified(let transaction) = result, transaction.productID == productID {
                    markDonated()
                }
            }
        } catch {
            errorMessage = "Restore failed: \(error.localizedDescription)"
        }
    }

    private func markDonated() {
        isDonated = true
        UserDefaults.standard.set(true, forKey: "donated")
    }

    private func listenForTransactions() async {
        for await result in Transaction.updates {
            if case .verified(let transaction) = result, transaction.productID == productID {
                await transaction.finish()
                markDonated()
            }
        }
    }
}
