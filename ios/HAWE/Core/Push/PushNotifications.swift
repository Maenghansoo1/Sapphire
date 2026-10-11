import Observation
import UIKit
import UserNotifications

/// 푸시 알림 종류 (CLAUDE.md §7) — 서버가 보내는 payload 의 "type" 값
enum PushKind: String {
    /// 오늘의 주제 도착
    case topic
    /// 콕 찌르기
    case poke
    /// 친구가 미션 완료 (기본 OFF)
    case friendDone = "friend_done"
    /// 내 작품에 댓글
    case comment
}

/// 알림을 눌러서 앱이 열렸을 때 갈 곳
struct PushDestination: Equatable {
    var kind: PushKind
    var groupID: String?
    var submissionID: String?
}

/// 푸시 권한·기기 토큰·알림 눌림을 관리해요.
/// 보낼지 말지(전체 알림 OFF, 그룹 알림 OFF)는 서버가 notification_prefs·group_members.notify 를 보고 정해요.
@MainActor
@Observable
final class PushCenter {
    static let shared = PushCenter()

    private(set) var authorization: UNAuthorizationStatus = .notDetermined
    /// APNs 기기 토큰(16진수). TODO: 서버 연결 시 device_tokens 에 저장
    private(set) var deviceToken: String?
    /// 알림을 눌러 열린 경우, 화면이 처리할 때까지 들고 있어요.
    var pendingDestination: PushDestination?

    /// 로그인 뒤 처음 홈(또는 01 시작)에 들어왔을 때 한 번 물어봐요.
    func requestAuthorizationIfNeeded() async {
        let center = UNUserNotificationCenter.current()
        let settings = await center.notificationSettings()
        authorization = settings.authorizationStatus
        if settings.authorizationStatus == .notDetermined {
            let granted = (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
            authorization = granted ? .authorized : .denied
        }
        if authorization == .authorized || authorization == .provisional {
            UIApplication.shared.registerForRemoteNotifications()
        }
    }

    func didRegister(token: Data) {
        deviceToken = token.map { String(format: "%02x", $0) }.joined()
        // TODO: 서버 연결 시 deviceToken 을 Spring 서버에 등록 (로그아웃·탈퇴 시 해제)
    }

    func handle(userInfo: [AnyHashable: Any]) {
        guard let raw = userInfo["type"] as? String, let kind = PushKind(rawValue: raw) else { return }
        pendingDestination = PushDestination(
            kind: kind,
            groupID: userInfo["group_id"] as? String,
            submissionID: userInfo["submission_id"] as? String
        )
    }
}

/// UIKit 쪽 알림 콜백을 받아요 (HAWEApp 의 @UIApplicationDelegateAdaptor).
final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    func application(_ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Task { @MainActor in PushCenter.shared.didRegister(token: deviceToken) }
    }

    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        // 시뮬레이터나 Push 권한(Capability)이 없는 서명에서는 여기로 와요. 앱 사용에는 지장 없어요.
        print("푸시 등록 실패: \(error.localizedDescription)")
    }

    /// 앱을 보고 있을 때도 배너로 보여줘요.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        [.banner, .list, .sound]
    }

    /// 알림을 눌렀을 때
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        let userInfo = response.notification.request.content.userInfo
        await MainActor.run { PushCenter.shared.handle(userInfo: userInfo) }
    }
}
