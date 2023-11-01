package org.adangel.resticbrowser.models;

import java.time.OffsetDateTime;

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
