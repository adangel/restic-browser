package org.adangel.resticbrowser.filesystem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;

import org.adangel.resticbrowser.models.SnapshotWithId;
import org.adangel.resticbrowser.models.Tree;

class ResticFileAttributes {
    static <A extends BasicFileAttributes> A fromNode(Tree.Node node) {
        @SuppressWarnings("unchecked")
        A attributes = (A) new BasicFileAttributes() {

            @Override
            public FileTime lastModifiedTime() {
                return FileTime.from(node.mtime().toInstant());
            }

            @Override
            public FileTime lastAccessTime() {
                return FileTime.from(node.atime().toInstant());
            }

            @Override
            public FileTime creationTime() {
                return FileTime.from(node.ctime().toInstant());
            }

            @Override
            public boolean isRegularFile() {
                return node.type() == Tree.NodeType.FILE;
            }

            @Override
            public boolean isDirectory() {
                return node.type() == Tree.NodeType.DIR;
            }

            @Override
            public boolean isSymbolicLink() {
                return node.type() == Tree.NodeType.SYMLINK;
            }

            @Override
            public boolean isOther() {
                return false;
            }

            @Override
            public long size() {
                return node.size();
            }

            @Override
            public Object fileKey() {
                return null;
            }
        };
        return attributes;
    }

    static <A extends BasicFileAttributes> A forRoot(Path repoPath) throws IOException {
        BasicFileAttributes repoAttributes = Files.readAttributes(repoPath, BasicFileAttributes.class);
        @SuppressWarnings("unchecked")
        A rootAttributes = (A) new BasicFileAttributes() {

            @Override
            public FileTime lastModifiedTime() {
                return repoAttributes.lastModifiedTime();
            }

            @Override
            public FileTime lastAccessTime() {
                return repoAttributes.lastAccessTime();
            }

            @Override
            public FileTime creationTime() {
                return repoAttributes.creationTime();
            }

            @Override
            public boolean isRegularFile() {
                return false;
            }

            @Override
            public boolean isDirectory() {
                return true;
            }

            @Override
            public boolean isSymbolicLink() {
                return false;
            }

            @Override
            public boolean isOther() {
                return false;
            }

            @Override
            public long size() {
                return 0;
            }

            @Override
            public Object fileKey() {
                return null;
            }
        };
        return rootAttributes;
    }

    public static <A extends BasicFileAttributes> A forSnapshot(SnapshotWithId snapshotWithId) {
        @SuppressWarnings("unchecked")
        A attributes = (A) new BasicFileAttributes() {

            @Override
            public FileTime lastModifiedTime() {
                return FileTime.from(snapshotWithId.snapshot().time().toInstant());
            }

            @Override
            public FileTime lastAccessTime() {
                return FileTime.from(snapshotWithId.snapshot().time().toInstant());
            }

            @Override
            public FileTime creationTime() {
                return FileTime.from(snapshotWithId.snapshot().time().toInstant());
            }

            @Override
            public boolean isRegularFile() {
                return false;
            }

            @Override
            public boolean isDirectory() {
                return true;
            }

            @Override
            public boolean isSymbolicLink() {
                return false;
            }

            @Override
            public boolean isOther() {
                return false;
            }

            @Override
            public long size() {
                return 0;
            }

            @Override
            public Object fileKey() {
                return null;
            }
        };
        return attributes;
    }
}
