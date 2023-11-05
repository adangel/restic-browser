# restic-browser
[![Java CI with Maven](https://github.com/adangel/restic-browser/actions/workflows/maven.yml/badge.svg)](https://github.com/adangel/restic-browser/actions/workflows/maven.yml)

Java-based implementation of a browser for restic repositories.
[restic](https://restic.net/) is a modern backup program.

## Status

* Early prototype status, not fully functional.
* PoC working: a `java.nio.file.FileSystem` that allows browsing a restic repository with e.g. `Files.walkTree`.
* PoC working: simple webserver that exposes this filesystem
* PoC working: fuse mount of this filesystem

## TODOs

* modularize (module-info.java)
* create jpackage versions/installers
* finish implementation:
  * support v1 and v2 repo formats and legacy layout
  * don't load the files completely into memory, use streams
  * respect locks in restic repo
  * cleanup
  * error handling
  * support all json fields (see package org.adangel.resticbrowser.model) including correct types (e.g. uint64)
* simple GUI for starting either webserver or mount
* simple CLI for starting either webserver or mount
* add symlinks for latest, shorten sha256 ids/filenames
  * layout similar to restic: https://github.com/restic/restic/blob/master/internal/fuse/root.go#L62-L65
* testing with real world repository

## Implementation details

* restic repository specification: <https://restic.readthedocs.io/en/stable/100_references.html>
* custom nio FileSystem: see [java.nio.file.spi.FileSystemProvider](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/nio/file/spi/FileSystemProvider.html)
* Expose this FileSystem via `com.sun.net.httpserver.SimpleFileServer`
  (see [JEP 408: Simple Web Server](https://openjdk.org/jeps/408))
* Expose this FileSystem with FUSE, e.g. [winfsp](https://winfsp.dev/doc/Known-File-Systems/)
  / [jnr-fuse](https://github.com/SerCeMan/jnr-fuse).

## License

MIT License
