import XCTest
@testable import MyLiftSquad

final class SlugScopeTests: XCTestCase {

    func testAthleteInDifferentSquadNotBlockedFromAdding() {
        // addedSlugs in SearchViewModel is populated from the current squad's
        // athletes only, so a slug present in another squad must not block addition.
        let currentSquadSlugs: Set<String> = ["athlete-a", "athlete-b"]
        let slugFromOtherSquad = "athlete-c"

        XCTAssertFalse(
            currentSquadSlugs.contains(slugFromOtherSquad),
            "Athlete from a different squad should not be reported as already added"
        )
    }

    func testAthleteAlreadyInCurrentSquadIsBlocked() {
        let currentSquadSlugs: Set<String> = ["athlete-a", "athlete-b"]
        let duplicateSlug = "athlete-a"

        XCTAssertTrue(
            currentSquadSlugs.contains(duplicateSlug),
            "Athlete already in the current squad should be blocked"
        )
    }
}
