package org.adangel.resticbrowser.models;

import java.time.OffsetDateTime;

// https://github.com/restic/restic/blob/master/internal/repository/key.go
public record Key(
        String hostname,
        String username,
        String kdf,
        int N,
        int r,
        int p,
        OffsetDateTime created,
        String data,
        String salt
) {
}
