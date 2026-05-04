import Foundation

/// A URLProtocol subclass that intercepts all requests made by a URLSession
/// configured with this class. Set `requestHandler` before each test.
class MockURLProtocol: URLProtocol {

    /// Set this before each test. Receives the outgoing URLRequest and
    /// returns the (status code, body string) the mock should respond with.
    nonisolated(unsafe) static var requestHandler: ((URLRequest) -> (Int, String))?

    /// Captures every request that was intercepted (for path/method assertions).
    nonisolated(unsafe) static var capturedRequests: [URLRequest] = []

    static func reset() {
        requestHandler = nil
        capturedRequests = []
    }

    // MARK: - URLProtocol

    override class func canInit(with request: URLRequest) -> Bool { true }
    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        MockURLProtocol.capturedRequests.append(request)

        guard let handler = MockURLProtocol.requestHandler else {
            client?.urlProtocol(self, didFailWithError: URLError(.unknown))
            return
        }

        let (statusCode, body) = handler(request)
        let data = body.data(using: .utf8) ?? Data()
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: statusCode,
            httpVersion: "HTTP/1.1",
            headerFields: ["Content-Type": "application/json"]
        )!
        client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
        client?.urlProtocol(self, didLoad: data)
        client?.urlProtocolDidFinishLoading(self)
    }

    override func stopLoading() {}
}

extension XCTestCase {
    /// Creates a URLSession wired to MockURLProtocol for use with injectable services.
    func makeMockSession() -> URLSession {
        let config = URLSessionConfiguration.ephemeral
        config.protocolClasses = [MockURLProtocol.self]
        return URLSession(configuration: config)
    }
}

import XCTest
