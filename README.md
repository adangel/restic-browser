# restic-browser

https://restic.readthedocs.io/en/stable/100_references.html

## Ideas / Features
* Create a `java.nio.file.spi.FileSystemProvider` that can browse a restic repository
* Expose this FileSystem via `com.sun.net.httpserver.SimpleFileServer`
  (see [JEP 408: Simple Web Server](https://openjdk.org/jeps/408))
* Expose this FileSystem with FUSE, e.g. [winfsp](https://winfsp.dev/doc/Known-File-Systems/)
  / [jnr-fuse](https://github.com/SerCeMan/jnr-fuse).
