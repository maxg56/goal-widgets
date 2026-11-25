// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation
import SwiftData

/// Conteneur SwiftData stocké dans l'App Group, lisible par l'app et l'extension widget.
enum GoalStore {
    static let appGroupID = "group.app.goalwidgets"

    /// Conteneur persistant (local uniquement, pas de CloudKit).
    static func makeContainer(inMemory: Bool = false) throws -> ModelContainer {
        let schema = Schema([GoalRecord.self])
        let configuration = ModelConfiguration(
            "Goals",
            schema: schema,
            isStoredInMemoryOnly: inMemory,
            groupContainer: inMemory
                ? ModelConfiguration.GroupContainer.none
                : ModelConfiguration.GroupContainer.identifier(appGroupID),
            cloudKitDatabase: ModelConfiguration.CloudKitDatabase.none
        )
        return try ModelContainer(for: schema, configurations: [configuration])
    }
}
