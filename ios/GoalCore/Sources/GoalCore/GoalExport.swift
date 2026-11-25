// Non vérifié : écrit sans Xcode (machine Linux).
import Foundation

/// Un objectif tel qu'il circule dans un fichier d'export (format `goal-widgets-export` v1,
/// cf. `docs/export-format.md`, référence Android `GoalExport.kt`). `id` est un UUID en minuscules,
/// stable d'un appareil à l'autre : c'est lui qui évite les doublons à l'import.
public struct ExportedGoal: Equatable, Sendable {
    public let id: String
    public let title: String
    public let goal: Goal
    public let color: GoalColor?
    public let emoji: String?
    /// nil = non précisé par le fichier (l'app applique sa valeur par défaut).
    public let notificationsEnabled: Bool?
    public let createdAt: Date

    public init(
        id: String,
        title: String,
        goal: Goal,
        color: GoalColor?,
        emoji: String?,
        notificationsEnabled: Bool?,
        createdAt: Date
    ) {
        self.id = id.lowercased()
        self.title = title
        self.goal = goal
        self.color = color
        self.emoji = emoji
        self.notificationsEnabled = notificationsEnabled
        self.createdAt = createdAt
    }
}

/// Raison pour laquelle un fichier est refusé en entier (aucun objectif n'est importé).
/// Mêmes codes que `ImportError` côté Kotlin.
public enum ImportError: Error, Equatable, Sendable {
    /// Le fichier n'est pas du JSON.
    case notJson
    /// JSON valide mais pas un export Goal Widgets (`format`, `version` ou `goals` absents/invalides).
    case notAnExport
    /// Export d'une version plus récente que celle que l'app sait lire.
    case unsupportedVersion(Int)
    /// L'objectif n° `index` (à partir de 0) a un champ `field` absent ou invalide.
    case invalidGoal(index: Int, field: String)
    /// Deux objectifs du fichier ont le même `id` (en minuscules).
    case duplicateId(String)

    /// Code stable, utilisé par les cas partagés.
    public var code: String {
        switch self {
        case .notJson: return "notJson"
        case .notAnExport: return "notAnExport"
        case .unsupportedVersion: return "unsupportedVersion"
        case .invalidGoal: return "invalidGoal"
        case .duplicateId: return "duplicateId"
        }
    }
}

/// Lecture/écriture du format d'export versionné.
public enum GoalExport {
    public static let format = "goal-widgets-export"
    public static let version = 1

    /// Nom suggéré à l'export : `goal-widgets-AAAA-MM-JJ.json`.
    public static func suggestedFileName(today: LocalDate) -> String {
        "goal-widgets-\(today.iso).json"
    }

    // MARK: Écriture

    /// Écrit `goals` (dans cet ordre) en JSON UTF-8 ; `exportedAt` est informatif.
    public static func encode(_ goals: [ExportedGoal], exportedAt: Date) -> Data {
        let file = FileJSON(exportedAt: formatInstant(exportedAt), goals: goals.map(GoalJSON.init))
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        // Les types encodés ne contiennent que des chaînes, entiers, booléens et null : ne peut pas échouer.
        var data = (try? encoder.encode(file)) ?? Data()
        data.append(0x0A)
        return data
    }

    private struct FileJSON: Encodable {
        let exportedAt: String
        let goals: [GoalJSON]

        enum CodingKeys: String, CodingKey { case format, version, exportedAt, goals }

        func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encode(GoalExport.format, forKey: .format)
            try c.encode(GoalExport.version, forKey: .version)
            try c.encode(exportedAt, forKey: .exportedAt)
            try c.encode(goals, forKey: .goals)
        }
    }

    private struct GoalJSON: Encodable {
        let value: ExportedGoal

        init(_ value: ExportedGoal) { self.value = value }

        enum CodingKeys: String, CodingKey {
            case id, title, kind, startDate, targetDate, color, emoji, notificationsEnabled, createdAt
        }

        func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encode(value.id.lowercased(), forKey: .id)
            try c.encode(value.title, forKey: .title)
            try c.encode(value.goal.kind.rawValue, forKey: .kind)
            try c.encode(value.goal.startDate.iso, forKey: .startDate)
            // Valeurs absentes écrites en `null` explicite (comme Android).
            let target = value.goal.kind == .countdown ? value.goal.targetDate?.iso : nil
            if let target { try c.encode(target, forKey: .targetDate) } else { try c.encodeNil(forKey: .targetDate) }
            if let color = value.color { try c.encode(color.rawValue, forKey: .color) } else { try c.encodeNil(forKey: .color) }
            if let emoji = value.emoji { try c.encode(emoji, forKey: .emoji) } else { try c.encodeNil(forKey: .emoji) }
            if let enabled = value.notificationsEnabled {
                try c.encode(enabled, forKey: .notificationsEnabled)
            } else {
                try c.encodeNil(forKey: .notificationsEnabled)
            }
            try c.encode(GoalExport.formatInstant(value.createdAt), forKey: .createdAt)
        }
    }

    // MARK: Lecture

    /// Lit un fichier entier ; au moindre objectif invalide, tout le fichier est refusé.
    public static func decode(_ data: Data) -> Result<[ExportedGoal], ImportError> {
        let root: JSONValue
        do {
            root = try JSONDecoder().decode(JSONValue.self, from: data)
        } catch {
            return .failure(.notJson)
        }
        guard case let .object(object) = root,
              case let .string(marker)? = object["format"], marker == Self.format,
              case let .int(version)? = object["version"],
              version >= 1
        else {
            return .failure(.notAnExport)
        }
        if version > Self.version { return .failure(.unsupportedVersion(version)) }
        guard case let .array(list)? = object["goals"] else { return .failure(.notAnExport) }

        var goals: [ExportedGoal] = []
        var seen = Set<String>()
        for (index, element) in list.enumerated() {
            let goal: ExportedGoal
            switch decodeGoal(element) {
            case let .success(decoded): goal = decoded
            case let .failure(invalid): return .failure(.invalidGoal(index: index, field: invalid.field))
            }
            guard seen.insert(goal.id).inserted else { return .failure(.duplicateId(goal.id)) }
            goals.append(goal)
        }
        return .success(goals)
    }

    private struct InvalidField: Error {
        let field: String
    }

    /// Champs vérifiés dans l'ordre : id, title, kind, startDate, targetDate, notificationsEnabled, createdAt.
    private static func decodeGoal(_ element: JSONValue) -> Result<ExportedGoal, InvalidField> {
        guard case let .object(o) = element else { return .failure(InvalidField(field: "goal")) }

        guard let id = o.string("id")?.lowercased(), isUUID(id) else { return .failure(InvalidField(field: "id")) }

        guard let title = o.string("title")?.trimmingCharacters(in: .whitespacesAndNewlines), !title.isEmpty else {
            return .failure(InvalidField(field: "title"))
        }

        guard let kind = o.string("kind").flatMap(GoalKind.init(rawValue:)) else {
            return .failure(InvalidField(field: "kind"))
        }

        guard case let .valid(start?) = o.date("startDate") else { return .failure(InvalidField(field: "startDate")) }

        var target: LocalDate?
        switch kind {
        case .countdown:
            guard case let .valid(date?) = o.date("targetDate") else { return .failure(InvalidField(field: "targetDate")) }
            target = date
        case .since:
            target = nil // ignorée, comme dans l'app
        }

        let notifications: Bool?
        switch o["notificationsEnabled"] {
        case nil, .null?: notifications = nil
        case let .bool(value)?: notifications = value
        default: return .failure(InvalidField(field: "notificationsEnabled"))
        }

        guard let createdAt = o.string("createdAt").flatMap(parseInstant) else {
            return .failure(InvalidField(field: "createdAt"))
        }

        return .success(ExportedGoal(
            id: id,
            title: title,
            goal: Goal(kind: kind, startDate: start, targetDate: target),
            color: GoalColor(wire: o.string("color")),
            emoji: GoalStyle.normalizedEmoji(o.string("emoji")),
            notificationsEnabled: notifications,
            createdAt: createdAt
        ))
    }

    /// UUID `8-4-4-4-12` hexadécimal minuscule (l'appelant a déjà mis en minuscules).
    private static func isUUID(_ text: String) -> Bool {
        let bytes = Array(text.utf8)
        guard bytes.count == 36 else { return false }
        for (i, b) in bytes.enumerated() {
            if i == 8 || i == 13 || i == 18 || i == 23 {
                if b != UInt8(ascii: "-") { return false }
            } else if !((b >= UInt8(ascii: "0") && b <= UInt8(ascii: "9")) || (b >= UInt8(ascii: "a") && b <= UInt8(ascii: "f"))) {
                return false
            }
        }
        return true
    }

    /// Date stricte `AAAA-MM-JJ` (date inexistante refusée).
    static func strictDate(_ text: String) -> LocalDate? {
        let bytes = Array(text.utf8)
        guard bytes.count == 10, bytes[4] == UInt8(ascii: "-"), bytes[7] == UInt8(ascii: "-") else { return nil }
        for i in [0, 1, 2, 3, 5, 6, 8, 9] where !isDigit(bytes[i]) { return nil }
        return LocalDate(iso: text)
    }

    // MARK: Instants (RFC 3339)

    /// Instant RFC 3339 : `Z` ou décalage `±HH:MM` obligatoire, secondes et fractions (1 à 9 chiffres)
    /// facultatives — même tolérance que `DateTimeFormatter.ISO_OFFSET_DATE_TIME` côté Android.
    public static func parseInstant(_ text: String) -> Date? {
        let b = Array(text.utf8)
        var i = 0

        func number(_ length: Int) -> Int? {
            guard i + length <= b.count else { return nil }
            var value = 0
            for k in i..<(i + length) {
                guard isDigit(b[k]) else { return nil }
                value = value * 10 + Int(b[k] - UInt8(ascii: "0"))
            }
            i += length
            return value
        }
        func expect(_ char: Character) -> Bool {
            guard i < b.count, b[i] == char.asciiValue else { return false }
            i += 1
            return true
        }

        guard b.count >= 10, let date = strictDate(String(decoding: b[0..<10], as: UTF8.self)) else { return nil }
        i = 10
        guard expect("T"), let hour = number(2), expect(":"), let minute = number(2) else { return nil }
        var second = 0
        var nanos = 0
        if expect(":") {
            guard let s = number(2) else { return nil }
            second = s
            if expect(".") {
                var digits = 0
                while i < b.count, isDigit(b[i]) {
                    if digits < 9 { nanos = nanos * 10 + Int(b[i] - UInt8(ascii: "0")) }
                    digits += 1
                    i += 1
                }
                guard (1...9).contains(digits) else { return nil }
                for _ in digits..<9 { nanos *= 10 }
            }
        }
        guard hour < 24, minute < 60, second < 60 else { return nil }

        var offsetSeconds = 0
        if expect("Z") {
            offsetSeconds = 0
        } else if i < b.count, b[i] == UInt8(ascii: "+") || b[i] == UInt8(ascii: "-") {
            let sign = b[i] == UInt8(ascii: "-") ? -1 : 1
            i += 1
            guard let oh = number(2), expect(":"), let om = number(2), oh <= 18, om < 60 else { return nil }
            var os = 0
            if expect(":") {
                guard let s = number(2), s < 60 else { return nil }
                os = s
            }
            offsetSeconds = sign * (oh * 3600 + om * 60 + os)
        } else {
            return nil
        }
        guard i == b.count else { return nil }

        let epochDays = LocalDate(year: 1970, month: 1, day: 1).days(until: date)
        let seconds = epochDays * 86_400 + hour * 3600 + minute * 60 + second - offsetSeconds
        return Date(timeIntervalSince1970: TimeInterval(seconds) + TimeInterval(nanos) / 1_000_000_000)
    }

    /// UTC, millisecondes toujours écrites : `2026-01-01T09:00:00.000Z`.
    public static func formatInstant(_ instant: Date) -> String {
        let totalMillis = Int64((instant.timeIntervalSince1970 * 1000).rounded())
        var seconds = totalMillis / 1000
        var millis = totalMillis % 1000
        if millis < 0 {
            millis += 1000
            seconds -= 1
        }
        var days = seconds / 86_400
        var secondOfDay = seconds % 86_400
        if secondOfDay < 0 {
            secondOfDay += 86_400
            days -= 1
        }
        let date = LocalDate(year: 1970, month: 1, day: 1).adding(days: Int(days))
        return date.iso + String(
            format: "T%02d:%02d:%02d.%03dZ",
            Int(secondOfDay / 3600),
            Int(secondOfDay % 3600 / 60),
            Int(secondOfDay % 60),
            Int(millis)
        )
    }

    private static func isDigit(_ b: UInt8) -> Bool {
        b >= UInt8(ascii: "0") && b <= UInt8(ascii: "9")
    }
}

/// Fusion d'un import avec les objectifs déjà présents.
public struct ImportPlan: Equatable, Sendable {
    public let toAdd: [ExportedGoal]
    public let skipped: Int
}

public enum GoalImport {
    /// Objectifs à ajouter : ceux dont l'id (comparé sans casse) n'existe pas encore localement.
    /// Un objectif déjà présent est ignoré, jamais écrasé : importer deux fois ne crée aucun doublon.
    public static func plan(existingIds: Set<String>, incoming: [ExportedGoal]) -> ImportPlan {
        var known = Set(existingIds.map { $0.lowercased() })
        let toAdd = incoming.filter { known.insert($0.id.lowercased()).inserted }
        return ImportPlan(toAdd: toAdd, skipped: incoming.count - toAdd.count)
    }
}

// MARK: - Arbre JSON minimal

/// Valeur JSON quelconque, lue avec `JSONDecoder` (portable, sans `JSONSerialization`/`NSNumber` :
/// un booléen ne se confond pas avec un nombre).
private enum JSONValue: Decodable {
    case null
    case bool(Bool)
    case int(Int)
    case double(Double)
    case string(String)
    case array([JSONValue])
    case object([String: JSONValue])

    init(from decoder: Decoder) throws {
        let c = try decoder.singleValueContainer()
        if c.decodeNil() {
            self = .null
        } else if let value = try? c.decode(Bool.self) {
            self = .bool(value)
        } else if let value = try? c.decode(Int.self) {
            self = .int(value)
        } else if let value = try? c.decode(Double.self) {
            self = .double(value)
        } else if let value = try? c.decode(String.self) {
            self = .string(value)
        } else if let value = try? c.decode([JSONValue].self) {
            self = .array(value)
        } else if let value = try? c.decode([String: JSONValue].self) {
            self = .object(value)
        } else {
            throw DecodingError.dataCorruptedError(in: c, debugDescription: "valeur JSON inattendue")
        }
    }
}

/// Résultat de la lecture d'un champ date : absent/null, ou présent (valide ou non).
private enum DateField {
    case valid(LocalDate?)
    case invalid
}

private extension Dictionary where Key == String, Value == JSONValue {
    /// Chaîne du champ ; nil si absent, `null` ou pas une chaîne.
    func string(_ key: String) -> String? {
        if case let .string(value)? = self[key] { return value }
        return nil
    }

    /// Date `AAAA-MM-JJ` : `.valid(nil)` si absente/null, `.invalid` si présente mais invalide.
    func date(_ key: String) -> DateField {
        switch self[key] {
        case nil, .null?:
            return .valid(nil)
        case let .string(text)?:
            return GoalExport.strictDate(text).map { .valid($0) } ?? .invalid
        default:
            return .invalid
        }
    }
}
