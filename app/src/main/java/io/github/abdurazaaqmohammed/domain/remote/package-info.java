/**
 * Remote-filesystem abstraction: the contract every network backend (FTP/FTPS,
 * SFTP, SMB, WebDAV, S3) implements.
 *
 * <p>Design notes:
 * <ul>
 *   <li>Paths are backend-native POSIX-style absolute paths ("/dir/file.txt").
 *       Backends that have no real directories (object storage) synthesise them
 *       from key prefixes and document that on the implementation.</li>
 *   <li>Not every operation exists everywhere. {@link RemoteFileSystem} declares
 *       each capability separately and every backend states which ones it
 *       supports via {@link #supports(Capability)} rather than silently
 *       throwing {@link UnsupportedOperationException} from a method call.</li>
 *   <li>Blocking by contract. Callers move work to a worker thread; nothing here
 *       touches the Android framework, so instances are unit-testable with a
 *       plain JVM.</li>
 *   <li>Streams are NOT closed by the interface for {@link #read}. The caller
 *       owns the returned stream. {@link #write} always consumes and closes the
 *       supplied stream.</li>
 * </ul>
 *
 * <p>Convention inherited from {@code domain.files}: no android.view /
 * android.widget imports.
 */
package io.github.abdurazaaqmohammed.domain.remote;