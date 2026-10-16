import Foundation
import Testing
@testable import CourseKit

@Suite struct FolderUsageTests {
    func makeFolder() throws -> URL {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent("FolderUsageTests-\(UUID().uuidString)")
        let nested = folder.appendingPathComponent("Wallpapers")
        try FileManager.default.createDirectory(at: nested, withIntermediateDirectories: true)
        try Data(count: 1000).write(to: folder.appendingPathComponent("a.tmp"))
        try Data(count: 250).write(to: nested.appendingPathComponent("lake.jpg"))
        try Data(count: 50).write(to: nested.appendingPathComponent("moon.jpg"))
        return folder
    }

    @Test func sizesCountEveryFileBelow() throws {
        let folder = try makeFolder()
        defer { try? FileManager.default.removeItem(at: folder) }
        #expect(FolderUsage.bytes(in: folder) == 1300)
        #expect(FolderUsage.files(in: folder).count == 3)
        #expect(FolderUsage.bytes(in: folder.appendingPathComponent("Wallpapers")) == 300)
        #expect(FolderUsage.bytes(in: folder.appendingPathComponent("missing")) == 0)
    }

    @Test func emptyingKeepsTheFolder() throws {
        let folder = try makeFolder()
        defer { try? FileManager.default.removeItem(at: folder) }
        #expect(FolderUsage.removeContents(of: folder) == 0)
        var isFolder: ObjCBool = false
        #expect(FileManager.default.fileExists(atPath: folder.path, isDirectory: &isFolder) && isFolder.boolValue)
        #expect(try FileManager.default.contentsOfDirectory(atPath: folder.path).isEmpty)
        #expect(FolderUsage.removeContents(of: folder.appendingPathComponent("missing")) == 0)
    }
}
