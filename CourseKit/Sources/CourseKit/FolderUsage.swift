import Foundation

/// The size of a folder the app keeps files in, and emptying it (Settings → Storage).
public enum FolderUsage {
    /// The regular files in `folder` and its subfolders, with their sizes in bytes; none if it doesn't exist.
    public static func files(in folder: URL, fileManager: FileManager = .default) -> [(url: URL, bytes: Int)] {
        let keys: [URLResourceKey] = [.isRegularFileKey, .fileSizeKey]
        guard let walker = fileManager.enumerator(at: folder, includingPropertiesForKeys: keys) else { return [] }
        var found: [(url: URL, bytes: Int)] = []
        for case let url as URL in walker {
            guard let values = try? url.resourceValues(forKeys: Set(keys)), values.isRegularFile == true else { continue }
            found.append((url, values.fileSize ?? 0))
        }
        return found
    }

    /// Bytes taken by the files in `folder` and its subfolders.
    public static func bytes(in folder: URL, fileManager: FileManager = .default) -> Int {
        files(in: folder, fileManager: fileManager).reduce(0) { $0 + $1.bytes }
    }

    /// Deletes everything inside `folder` but keeps the folder. Returns how many items couldn't be deleted.
    @discardableResult
    public static func removeContents(of folder: URL, fileManager: FileManager = .default) -> Int {
        guard let items = try? fileManager.contentsOfDirectory(at: folder, includingPropertiesForKeys: nil) else { return 0 }
        var failures = 0
        for item in items {
            do {
                try fileManager.removeItem(at: item)
            } catch {
                failures += 1
            }
        }
        return failures
    }
}
