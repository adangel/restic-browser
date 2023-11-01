package org.adangel.resticbrowser.models;

public record Masterkey(
        String encrypt,
        MAC mac
) {
    public record MAC(String k, String r) {
    }
}
