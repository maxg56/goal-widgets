// swift-tools-version:5.9
// Non vérifié : écrit sans Xcode (machine Linux).
// Package Swift pur (aucune dépendance UIKit/SwiftUI) : moteur de compte à rebours
// et planificateur de jalons, testés contre ../../shared-cases.
import PackageDescription

let package = Package(
    name: "GoalCore",
    platforms: [.iOS(.v17), .macOS(.v14)],
    products: [
        .library(name: "GoalCore", targets: ["GoalCore"]),
    ],
    targets: [
        .target(name: "GoalCore"),
        .testTarget(name: "GoalCoreTests", dependencies: ["GoalCore"]),
    ]
)
