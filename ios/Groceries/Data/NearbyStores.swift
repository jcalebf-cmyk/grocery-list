import CoreLocation
import Foundation

struct NearbyStore: Codable, Hashable {
    let name: String
    var distanceMeters: Int
    /// Missing only in results saved by app versions before 0.9.
    var lat: Double?
    var lng: Double?
}

/// Nearby grocery stores, saved on the phone so the paid Places lookup runs about once a week per
/// area: results are reused until they're 7 days old or the user has moved more than ~1.5 km, with
/// distances re-measured from where the user is now.
@MainActor
final class NearbyStores: NSObject, CLLocationManagerDelegate {
    static let shared = NearbyStores()

    private struct Saved: Codable { let lat: Double; let lng: Double; let fetchedAt: Date; let stores: [NearbyStore] }
    private let key = "nearbyStores.lastLookup"
    private let maxAge: TimeInterval = 7 * 24 * 60 * 60
    private let reuseRadius: CLLocationDistance = 1_500

    private let manager = CLLocationManager()
    private var authContinuation: CheckedContinuation<CLAuthorizationStatus, Never>?

    override init() {
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = kCLLocationAccuracyHundredMeters
    }

    var authorization: CLAuthorizationStatus { manager.authorizationStatus }

    /// Shows the system prompt if it hasn't been answered yet.
    func requestPermission() async -> CLAuthorizationStatus {
        guard manager.authorizationStatus == .notDetermined else { return manager.authorizationStatus }
        return await withCheckedContinuation { continuation in
            authContinuation = continuation
            manager.requestWhenInUseAuthorization()
        }
    }

    nonisolated func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        let status = manager.authorizationStatus
        Task { @MainActor in
            guard status != .notDetermined, let c = self.authContinuation else { return }
            self.authContinuation = nil
            c.resume(returning: status)
        }
    }

    /// The last saved results, if fresh, for showing immediately.
    func peek() -> [NearbyStore]? {
        guard let saved = load(), Date().timeIntervalSince(saved.fetchedAt) < maxAge else { return nil }
        return saved.stores
    }

    func find() async throws -> [NearbyStore] {
        let previous = load()
        guard let location = await currentLocation() else {
            if let previous { return previous.stores }
            throw CLError(.locationUnknown)
        }
        let lat = location.coordinate.latitude
        let lng = location.coordinate.longitude
        // Results saved before stores carried coordinates can't be re-measured, so they're looked up again.
        if let previous, Date().timeIntervalSince(previous.fetchedAt) < maxAge,
           CLLocation(latitude: previous.lat, longitude: previous.lng).distance(from: location) < reuseRadius,
           previous.stores.allSatisfy({ $0.lat != nil && $0.lng != nil }) {
            return measured(previous.stores, from: location)
        }
        struct Request: Encodable { let lat: Double; let lng: Double }
        struct Response: Decodable { let stores: [NearbyStore] }
        let response: Response = try await ServerAPI.post("/api/stores", body: Request(lat: lat, lng: lng))
        save(Saved(lat: lat, lng: lng, fetchedAt: Date(), stores: response.stores))
        return response.stores
    }

    /// The stores with distances from `location`, closest first.
    private func measured(_ stores: [NearbyStore], from location: CLLocation) -> [NearbyStore] {
        stores.map { store in
            guard let lat = store.lat, let lng = store.lng else { return store }
            var s = store
            s.distanceMeters = Int(location.distance(from: CLLocation(latitude: lat, longitude: lng)).rounded())
            return s
        }
        .sorted { $0.distanceMeters < $1.distanceMeters }
    }

    /// Forgets saved lookups, e.g. when the account is deleted.
    func clear() { UserDefaults.standard.removeObject(forKey: key) }

    private func currentLocation() async -> CLLocation? {
        do {
            for try await update in CLLocationUpdate.liveUpdates() {
                if let location = update.location { return location }
            }
        } catch {}
        return manager.location
    }

    private func load() -> Saved? {
        UserDefaults.standard.data(forKey: key).flatMap { try? JSONDecoder().decode(Saved.self, from: $0) }
    }

    private func save(_ saved: Saved) {
        UserDefaults.standard.set(try? JSONEncoder().encode(saved), forKey: key)
    }
}
