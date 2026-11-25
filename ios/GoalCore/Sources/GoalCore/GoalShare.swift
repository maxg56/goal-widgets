// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation

/// Partage d'un objectif avec une autre personne (ticket 13).
/// Le fichier est un export `goal-widgets-export` v1 (`docs/export-format.md`) contenant un seul
/// objectif, `notificationsEnabled` à null (le destinataire décide). La lecture réutilise
/// `GoalExport.decode` et `GoalImport.plan` : même validation tout-ou-rien, même dédoublonnage par id.
public enum GoalShare {
    /// Suffixe du nom de fichier : `<titre>.goalwidgets.json` (type MIME `application/json`).
    public static let fileSuffix = ".goalwidgets.json"
    /// Nom utilisé quand le titre ne laisse aucun caractère utilisable.
    public static let fallbackName = "objectif"
    /// Longueur maximale (en caractères) de la partie « titre » du nom de fichier.
    public static let maxNameLength = 60

    /// Fichier de partage d'un objectif.
    public static func encode(_ goal: ExportedGoal, exportedAt: Date) -> Data {
        let shared = ExportedGoal(
            id: goal.id,
            title: goal.title,
            goal: goal.goal,
            color: goal.color,
            emoji: goal.emoji,
            notificationsEnabled: nil,
            createdAt: goal.createdAt
        )
        return GoalExport.encode([shared], exportedAt: exportedAt)
    }

    /// `<titre>.goalwidgets.json`, sans caractère interdit dans un nom de fichier (`/ \ : * ? " < > |` → `-`,
    /// retours à la ligne et caractères de contrôle → espace), sans point initial (fichier caché),
    /// tronqué à `maxNameLength` caractères.
    public static func fileName(title: String) -> String {
        let reserved: Set<Character> = ["/", "\\", ":", "*", "?", "\"", "<", ">", "|"]
        var cleaned = String(title.map { character -> Character in
            if reserved.contains(character) { return "-" }
            if character.unicodeScalars.contains(where: { CharacterSet.controlCharacters.contains($0) || CharacterSet.newlines.contains($0) }) {
                return " "
            }
            return character
        })
        cleaned = cleaned.trimmingCharacters(in: .whitespaces)
        while cleaned.hasPrefix(".") { cleaned.removeFirst() }
        cleaned = String(cleaned.prefix(maxNameLength)).trimmingCharacters(in: .whitespaces)
        if cleaned.isEmpty { cleaned = fallbackName }
        return cleaned + fileSuffix
    }

    /// Lit un fichier reçu (partage d'un objectif ou export complet) pour l'écran d'aperçu.
    /// Rien n'est écrit ici ; l'ajout passe ensuite par l'import habituel.
    public static func preview(_ data: Data, existingIds: Set<String>) -> Result<SharedGoalsPreview, ImportError> {
        GoalExport.decode(data).map { incoming in
            let plan = GoalImport.plan(existingIds: existingIds, incoming: incoming)
            let toAdd = Set(plan.toAdd.map(\.id))
            return SharedGoalsPreview(items: incoming.map {
                SharedGoalsPreview.Item(goal: $0, alreadyPresent: !toAdd.contains($0.id))
            })
        }
    }
}

/// Contenu d'un fichier reçu, dans l'ordre du fichier.
public struct SharedGoalsPreview: Equatable, Sendable {
    public struct Item: Equatable, Sendable {
        public let goal: ExportedGoal
        /// Un objectif de même id existe déjà : il ne sera pas ajouté (« Déjà dans tes objectifs »).
        public let alreadyPresent: Bool

        public init(goal: ExportedGoal, alreadyPresent: Bool) {
            self.goal = goal
            self.alreadyPresent = alreadyPresent
        }
    }

    public let items: [Item]

    public init(items: [Item]) {
        self.items = items
    }

    /// Nombre d'objectifs que « Ajouter » écrira.
    public var toAddCount: Int {
        items.filter { !$0.alreadyPresent }.count
    }
}
