package org.adangel.resticbrowser.models;

import java.time.OffsetDateTime;
import java.util.List;

// https://github.com/restic/restic/blob/master/internal/restic/snapshot.go
public record Snapshot(OffsetDateTime time, String hostname, List<String> tags, List<String> paths,
                       String tree,
                       String username, int uid, int gid, String program_version,
                       List<String> excludes,
                       String original,
                       String parent) { }

