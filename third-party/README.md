# Third-party notices

`THIRD_PARTY_NOTICES.txt` is the reviewed, offline notice bundle shipped in the APK
and displayed after the project license in About > Open-source licenses.
`components.txt` records the union of resolved external Debug and Release runtime
artifact coordinates, including transitive dependencies. Build tools, unit tests,
and instrumentation-test dependencies are outside this inventory. Runtime tooling
artifacts are included conservatively even if packaging removes them.

## Evidence

- Component versions and license names come from the resolved artifacts and their
  published Maven POMs, not from the direct dependency declarations alone.
- Embedded `LICENSE`, `NOTICE`, and `COPYRIGHT` files were extracted from those
  JAR/AAR files, including `classes.jar` inside AARs. Identical texts are stored once;
  component entries identify the original archive paths and the shared text IDs.
- The javax.inject and listenablefuture sources JARs supply their Apache-2.0
  declarations and copyright holders, absent from their POM license fields.
- The jsr305 sources JAR contains Brian Goetz's CC-BY-2.5 attribution for the JCIP
  concurrency annotations; this is retained in addition to the POM's Apache license.
- The Protocol Buffers POM declares BSD-3-Clause; the upstream BSD text is included.
  Its cited license URL is a text reference, not a claim about the relocated runtime's
  precise upstream revision.
- OkHttp's Public Suffix List NOTICE and the MPL-2.0 text are included, together with
  source availability links. jsoup's embedded MIT license includes its copyright.

The inventory does not establish the provenance of Nagi's own implementation or
replace a review of overall license compatibility.

## Updating dependencies

1. Resolve both runtime configurations using JDK 21:

   ```sh
   ./gradlew :app:dependencies --configuration releaseRuntimeClasspath
   ./gradlew :app:dependencies --configuration debugRuntimeClasspath
   ```

2. Inspect the resolved JAR/AAR files and published POMs for added or changed
   components. Check embedded notices (including nested `classes.jar`) and relevant
   source headers, rather than assuming every dependency uses its POM's license.
3. Update `components.txt` and the corresponding entries and full applicable texts
   in `THIRD_PARTY_NOTICES.txt`. Text IDs are the first 12 hex characters of the
   SHA-256 of the stripped text plus a final newline. Preserve upstream wording.
4. Run `./gradlew :app:verifyThirdPartyNotices :app:assembleDebug` and the license
   tests. The verifier rejects changed coordinates or missing component entries;
   it cannot automatically verify the legal accuracy of reviewed text.
5. Inspect the APK's `assets/LICENSE` and `assets/THIRD_PARTY_NOTICES.txt`. For a
   Release, also provide the exact corresponding project source and build instructions.

Normal builds use only these checked-in texts; they do not fetch license pages.
