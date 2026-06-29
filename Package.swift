// swift-tools-version:5.5
import PackageDescription

let package = Package(
    name: "SuperkassaOfflineQueue",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "SuperkassaOfflineQueue",
            targets: ["SuperkassaOfflineQueue"]
        ),
    ],
    dependencies: [],
    targets: [
        .binaryTarget(
            name: "SuperkassaOfflineQueue",
            url: "https://github.com/texport/superkassa-offline-queue/releases/download/v1.0.2/SuperkassaOfflineQueue.xcframework.zip",
            checksum: "f39a708113660a42ad17122bab7d8d90e71ebace5fc839f0f668b5cc26cb2bd4"
        )
    ]
)
