package org.adangel.resticbrowser.models;

import java.math.BigInteger;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

// https://github.com/restic/restic/blob/master/internal/restic/tree.go
public record Tree(List<Node> nodes) {
    public enum NodeType {
        @JsonProperty("dir")
        DIR,
        @JsonProperty("file")
        FILE,
        @JsonProperty("symlink")
        SYMLINK
    }

    // https://github.com/restic/restic/blob/master/internal/restic/node.go
    public record Node(String name,
                NodeType type,
                long mode,
                OffsetDateTime mtime,
                OffsetDateTime atime,
                OffsetDateTime ctime,
                int uid,
                int gid,
                String user,
                String group,
                BigInteger inode,
                long device_id,
                long size,
                int links,
                String linktarget,
                List<String> content,
                String subtree) {};
}
