// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation

/// Lien profond vers un objectif : `goalwidgets://goal/<uuid>` (appui sur un widget, notification).
public enum GoalDeepLink {
    public static let scheme = "goalwidgets"
    private static let host = "goal"

    public static func url(forGoal id: UUID) -> URL {
        URL(string: "\(scheme)://\(host)/\(id.uuidString)")!
    }

    /// Identifiant de l'objectif visé, ou nil si l'URL n'est pas un lien d'objectif valide.
    public static func goalID(from url: URL) -> UUID? {
        guard url.scheme?.lowercased() == scheme,
              url.host?.lowercased() == host else { return nil }
        let parts = url.pathComponents.filter { $0 != "/" }
        guard parts.count == 1 else { return nil }
        return UUID(uuidString: parts[0])
    }
}
