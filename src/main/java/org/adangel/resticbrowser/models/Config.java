package org.adangel.resticbrowser.models;

// https://github.com/restic/restic/blob/master/internal/restic/config.go
public record Config(int version, String id, String chunker_polynomial) {
}
