import FirebaseAuth
import Foundation

/// A non-200 reply from the Vercel server. 429 means the user hit a daily limit.
struct ServerError: Error {
    let status: Int
    var isRateLimited: Bool { status == 429 }
}

/// Calls the Vercel server. Requests carry the signed-in user's Firebase ID token; only the password reset
/// email (sent while signed out) goes without one.
enum ServerAPI {
    static let baseURL = URL(string: "https://grocery-recipes.vercel.app")!

    static func post<Response: Decodable>(
        _ path: String, body: some Encodable, timeout: TimeInterval = 30, signedIn: Bool = true
    ) async throws -> Response {
        var request = URLRequest(url: baseURL.appending(path: path), timeoutInterval: timeout)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if signedIn {
            guard let user = Auth.auth().currentUser else { throw ServerError(status: 401) }
            request.setValue("Bearer \(try await user.getIDToken())", forHTTPHeaderField: "Authorization")
        }
        request.httpBody = try JSONEncoder().encode(body)
        return try await send(request)
    }

    /// A GET; `signedIn` false for what anyone may read (a shared recipe).
    static func get<Response: Decodable>(_ path: String, query: [String: String] = [:], signedIn: Bool = true) async throws -> Response {
        var url = baseURL.appending(path: path)
        if !query.isEmpty { url.append(queryItems: query.map { URLQueryItem(name: $0.key, value: $0.value) }) }
        var request = URLRequest(url: url, timeoutInterval: 30)
        if signedIn { try await authorize(&request) }
        return try await send(request)
    }

    static func delete(_ path: String) async throws {
        var request = URLRequest(url: baseURL.appending(path: path), timeoutInterval: 30)
        request.httpMethod = "DELETE"
        try await authorize(&request)
        let _: ServerOK = try await send(request)
    }

    private static func authorize(_ request: inout URLRequest) async throws {
        guard let user = Auth.auth().currentUser else { throw ServerError(status: 401) }
        request.setValue("Bearer \(try await user.getIDToken())", forHTTPHeaderField: "Authorization")
    }

    private static func send<Response: Decodable>(_ request: URLRequest) async throws -> Response {
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard status == 200 else { throw ServerError(status: status) }
        return try JSONDecoder().decode(Response.self, from: data)
    }
}

/// A reply we only check succeeded.
struct ServerOK: Decodable {}
