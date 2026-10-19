import XCTest
@testable import HAWE

/// Android `CameraZoomTest` 를 옮겼어요.
final class CameraZoomTests: XCTestCase {
    func testClampsToDeviceRangeAndTenTimes() {
        XCTAssertEqual(CameraZoom.clamp(0.5, deviceMin: 1, deviceMax: 8), 1)
        XCTAssertEqual(CameraZoom.clamp(3, deviceMin: 1, deviceMax: 8), 3)
        XCTAssertEqual(CameraZoom.clamp(20, deviceMin: 1, deviceMax: 8), 8)
        XCTAssertEqual(CameraZoom.clamp(25, deviceMin: 1, deviceMax: 120), 10, "기기가 더 돼도 10배까지만")
        XCTAssertEqual(CameraZoom.clamp(2, deviceMin: 1, deviceMax: 1), 1, "확대가 안 되는 카메라")
    }

    func testLabelHasOneDecimal() {
        XCTAssertEqual(CameraZoom.label(1), "1.0×")
        XCTAssertEqual(CameraZoom.label(2.46), "2.5×")
        XCTAssertEqual(CameraZoom.label(10), "10.0×")
    }
}
