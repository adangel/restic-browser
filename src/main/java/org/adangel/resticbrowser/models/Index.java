package org.adangel.resticbrowser.models;

import java.util.List;

public record Index(List<String> supersedes, List<Pack> packs) {
    public record Pack(String id, List<Blob> blobs) {
        public record Blob(String id, String type, int offset, int length, int uncompressed_length) {}
    }
}
