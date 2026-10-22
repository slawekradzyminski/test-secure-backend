# Java certificate mitigation

The frozen `blocked.certs` file implements OpenJDK's recommended mitigation for
[CVE-2026-91403](https://openjdk.org/groups/vulnerability/advisories/2026-10-21). The file includes the upstream historical blocks
and compromised intermediate CA certificates, including alternate DER encodings.
Both Docker stages copy it into the JVM before Java executes.

`provenance.json` records immutable upstream inputs and SHA-256 checksums. The
complete fixture, generator instructions, offline JVM probe and Compose runtime
verification live in sibling `awesome-localstack/security/java/`. The generated
file checksum is `2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10`.

This Dockerfile change is local source pending a normal source-owned image
release. Existing published image tags/digests are not rebuilt or overwritten.
Awesome LocalStack can protect those images with its reviewed read-only mount.
Keep existing custom/vendor blocks when renewing the snapshot; never claim that
the unchanged Java version fixes unrelated vulnerabilities.
