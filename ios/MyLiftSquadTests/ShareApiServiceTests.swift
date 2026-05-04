import XCTest
@testable import MyLiftSquad

final class ShareApiServiceTests: XCTestCase {

    private var service: ShareApiService!

    override func setUp() {
        super.setUp()
        MockURLProtocol.reset()
        service = ShareApiService(baseURL: "http://mock", session: makeMockSession())
    }

    // MARK: - shareSquad

    func testShareSquadReturnsCode() async throws {
        MockURLProtocol.requestHandler = { _ in (201, #"{"code":"ABC123"}"#) }
        let code = try await service.shareSquad(
            name: "My Squad",
            athletes: [AthleteRef(name: "Alice", slug: "alice")]
        )
        XCTAssertEqual(code, "ABC123")
    }

    func testShareSquadSendsPOSTToSquadsPath() async throws {
        MockURLProtocol.requestHandler = { _ in (201, #"{"code":"XY1234"}"#) }
        _ = try await service.shareSquad(name: "Crew", athletes: [AthleteRef(name: "Alice", slug: "alice")])
        let req = MockURLProtocol.capturedRequests.first
        XCTAssertEqual(req?.httpMethod, "POST")
        XCTAssertEqual(req?.url?.path, "/squads")
    }

    func testShareSquadSendsCorrectJSONBody() async throws {
        MockURLProtocol.requestHandler = { _ in (201, #"{"code":"XY1234"}"#) }
        _ = try await service.shareSquad(
            name: "Crew",
            athletes: [AthleteRef(name: "Alice Smith", slug: "alice-smith")]
        )
        let body = MockURLProtocol.capturedRequests.first?.httpBodyData()
        let json = try XCTUnwrap(body.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] })
        XCTAssertEqual(json["name"] as? String, "Crew")
        let athletes = try XCTUnwrap(json["athletes"] as? [[String: String]])
        XCTAssertEqual(athletes.count, 1)
        XCTAssertEqual(athletes[0]["name"], "Alice Smith")
        XCTAssertEqual(athletes[0]["slug"], "alice-smith")
    }

    func testShareSquadThrowsOnServerError() async {
        MockURLProtocol.requestHandler = { _ in (500, "Internal Server Error") }
        do {
            _ = try await service.shareSquad(name: "Squad", athletes: [AthleteRef(name: "Alice", slug: "alice")])
            XCTFail("Expected an error to be thrown")
        } catch {
            // expected
        }
    }

    // MARK: - tryImportSquad

    func testTryImportSquadReturnsSquadOn200() async throws {
        let body = #"{"name":"Imported","athletes":[{"name":"Alice","slug":"alice"}]}"#
        MockURLProtocol.requestHandler = { _ in (200, body) }
        let result = try await service.tryImportSquad(code: "ABC123")
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.name, "Imported")
        XCTAssertEqual(result?.athletes.count, 1)
        XCTAssertEqual(result?.athletes[0].name, "Alice")
        XCTAssertEqual(result?.athletes[0].slug, "alice")
    }

    func testTryImportSquadReturnsNilOn404() async throws {
        MockURLProtocol.requestHandler = { _ in (404, #"{"error":"not found"}"#) }
        let result = try await service.tryImportSquad(code: "XXXXXX")
        XCTAssertNil(result)
    }

    func testTryImportSquadSendsGETToCorrectPathWithUppercasedCode() async throws {
        MockURLProtocol.requestHandler = { _ in (404, "") }
        _ = try await service.tryImportSquad(code: "abc123")
        let req = MockURLProtocol.capturedRequests.first
        XCTAssertEqual(req?.httpMethod, "GET")
        XCTAssertEqual(req?.url?.path, "/squads/ABC123")
    }

    func testTryImportSquadThrowsOnServerError() async {
        MockURLProtocol.requestHandler = { _ in (500, "Error") }
        do {
            _ = try await service.tryImportSquad(code: "ABC123")
            XCTFail("Expected an error to be thrown")
        } catch {
            // expected
        }
    }

    // MARK: - shareBundle

    func testShareBundleReturnsCode() async throws {
        MockURLProtocol.requestHandler = { _ in (201, #"{"code":"BUNDLE"}"#) }
        let squads = [
            SharedSquad(name: "Squad A", athletes: [AthleteRef(name: "Alice", slug: "alice")]),
            SharedSquad(name: "Squad B", athletes: [AthleteRef(name: "Bob", slug: "bob")])
        ]
        let code = try await service.shareBundle(squads: squads)
        XCTAssertEqual(code, "BUNDLE")
    }

    func testShareBundleSendsPOSTToBundlesPath() async throws {
        MockURLProtocol.requestHandler = { _ in (201, #"{"code":"BUNDLE"}"#) }
        _ = try await service.shareBundle(squads: [
            SharedSquad(name: "Squad A", athletes: [AthleteRef(name: "Alice", slug: "alice")])
        ])
        let req = MockURLProtocol.capturedRequests.first
        XCTAssertEqual(req?.httpMethod, "POST")
        XCTAssertEqual(req?.url?.path, "/bundles")
    }

    func testShareBundleSendsAllSquadsInBody() async throws {
        MockURLProtocol.requestHandler = { _ in (201, #"{"code":"XY1234"}"#) }
        _ = try await service.shareBundle(squads: [
            SharedSquad(name: "Squad A", athletes: [AthleteRef(name: "Alice", slug: "alice")]),
            SharedSquad(name: "Squad B", athletes: [AthleteRef(name: "Bob", slug: "bob")])
        ])
        let body = MockURLProtocol.capturedRequests.first?.httpBodyData()
        let json = try XCTUnwrap(body.flatMap { try? JSONSerialization.jsonObject(with: $0) as? [String: Any] })
        let squads = try XCTUnwrap(json["squads"] as? [[String: Any]])
        XCTAssertEqual(squads.count, 2)
        XCTAssertEqual(squads[0]["name"] as? String, "Squad A")
        XCTAssertEqual(squads[1]["name"] as? String, "Squad B")
    }

    // MARK: - tryImportBundle

    func testTryImportBundleReturnsBundleOn200() async throws {
        let body = #"""
        {"squads":[
          {"name":"Squad A","athletes":[{"name":"Alice","slug":"alice"}]},
          {"name":"Squad B","athletes":[{"name":"Bob","slug":"bob"}]}
        ]}
        """#
        MockURLProtocol.requestHandler = { _ in (200, body) }
        let result = try await service.tryImportBundle(code: "BUNDLE")
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.squads.count, 2)
        XCTAssertEqual(result?.squads[0].name, "Squad A")
        XCTAssertEqual(result?.squads[1].name, "Squad B")
        XCTAssertEqual(result?.squads[0].athletes[0].name, "Alice")
    }

    func testTryImportBundleReturnsNilOn404() async throws {
        MockURLProtocol.requestHandler = { _ in (404, #"{"error":"not found"}"#) }
        let result = try await service.tryImportBundle(code: "XXXXXX")
        XCTAssertNil(result)
    }

    func testTryImportBundleSendsGETToCorrectPathWithUppercasedCode() async throws {
        MockURLProtocol.requestHandler = { _ in (404, "") }
        _ = try await service.tryImportBundle(code: "abc123")
        let req = MockURLProtocol.capturedRequests.first
        XCTAssertEqual(req?.httpMethod, "GET")
        XCTAssertEqual(req?.url?.path, "/bundles/ABC123")
    }
}

// MARK: - URLRequest helper

private extension URLRequest {
    /// Reads the httpBody, including the case where it was set as a stream
    /// (URLSession converts httpBody to a stream internally).
    func httpBodyData() -> Data? {
        if let body = httpBody { return body }
        if let stream = httpBodyStream {
            stream.open()
            defer { stream.close() }
            var data = Data()
            let bufferSize = 4096
            let buffer = UnsafeMutablePointer<UInt8>.allocate(capacity: bufferSize)
            defer { buffer.deallocate() }
            while stream.hasBytesAvailable {
                let read = stream.read(buffer, maxLength: bufferSize)
                if read > 0 { data.append(buffer, count: read) }
            }
            return data
        }
        return nil
    }
}
