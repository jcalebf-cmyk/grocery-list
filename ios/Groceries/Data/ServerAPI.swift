import FirebaseAuth
import Foundation

/// A non-200 reply from the Vercel server. 429 means the user hit a daily limit.
struct ServerError: Error {
    let status: Int
    var isRateLimited: Bool { status == 429 }
}

/// Calls the Vercel server, which only accepts requests carrying the signed-in user's Firebase ID token.
enum ServerAPI {
    static let baseURL = URL(string: "https://grocery-recipes.vercel.app")!

    static func post<Response: Decodable>(_ path: String, body: some Encodable, timeout: TimeInterval = 30) async throws -> Response {
        guard let user = Auth.auth().currentUser else { throw ServerError(status: 401) }
        let token = try await user.getIDToken()
        var request = URLRequest(url: baseURL.appending(path: path), timeoutInterval: timeout)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        request.httpBody = try JSONEncoder().encode(body)
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard status == 200 else { throw ServerError(status: status) }
        return try JSONDecoder().decode(Response.self, from: data)
    }
}
