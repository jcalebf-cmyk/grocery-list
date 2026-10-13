import FirebaseFirestore

/// Live Firestore listeners as async streams, so views can `for try await` over changes.
extension Query {
    func stream<T: Decodable>(_ type: T.Type) -> AsyncThrowingStream<[T], Error> {
        AsyncThrowingStream { continuation in
            let registration = addSnapshotListener { snapshot, error in
                if let error { continuation.finish(throwing: error); return }
                let docs = snapshot?.documents ?? []
                continuation.yield(docs.compactMap { try? $0.data(as: T.self) })
            }
            continuation.onTermination = { _ in registration.remove() }
        }
    }
}

extension DocumentReference {
    /// Like `stream`, plus whether the document has changes from this phone the server hasn't saved yet.
    /// Yields again once the server saves them.
    func streamSaving<T: Decodable>(_ type: T.Type) -> AsyncThrowingStream<(value: T?, saving: Bool), Error> {
        AsyncThrowingStream { continuation in
            let registration = addSnapshotListener(includeMetadataChanges: true) { snapshot, error in
                if let error { continuation.finish(throwing: error); return }
                guard let snapshot else { return }
                let value = snapshot.exists ? try? snapshot.data(as: T.self) : nil
                continuation.yield((value, snapshot.metadata.hasPendingWrites))
            }
            continuation.onTermination = { _ in registration.remove() }
        }
    }

    /// Yields nil while the document doesn't exist. With `confirmedOnly`, skips this phone's
    /// unsaved changes and yields again once the server has them.
    func stream<T: Decodable>(_ type: T.Type, confirmedOnly: Bool = false) -> AsyncThrowingStream<T?, Error> {
        AsyncThrowingStream { continuation in
            let registration = addSnapshotListener(includeMetadataChanges: confirmedOnly) { snapshot, error in
                if let error { continuation.finish(throwing: error); return }
                if confirmedOnly && snapshot?.metadata.hasPendingWrites == true { return }
                guard let snapshot, snapshot.exists else { continuation.yield(nil); return }
                continuation.yield(try? snapshot.data(as: T.self))
            }
            continuation.onTermination = { _ in registration.remove() }
        }
    }
}

/// Applies each value from a live listener until it ends. Listener errors (e.g. permission denied right
/// after leaving a household) just stop the updates; the session handles those cases itself.
func consume<T>(_ stream: AsyncThrowingStream<T, Error>, _ apply: (T) async -> Void) async {
    do {
        for try await value in stream { await apply(value) }
    } catch {}
}
