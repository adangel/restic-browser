package org.adangel.resticbrowser.models;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Tree(List<Node> nodes) {
    public enum NodeType {
        @JsonProperty("dir")
        DIR,
        @JsonProperty("file")
        FILE,
        @JsonProperty("symlink")
        SYMLINK
    }
    public record Node(String name,
                NodeType type,
                int mode,
                String mtime,
                String atime,
                String ctime,
                int uid,
                int gid,
                String user,
                int inode,
                int size,
                int links,
                List<String> content,
                String subtree) {};
}
