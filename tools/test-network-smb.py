"""Loopback-only SMB2 fixture for SmbClientIntegrationTest (requires impacket).

Uses a temporary, empty share and disposable credentials; never exports user files.
Stop with Ctrl+C. MP_TEST_SMB_* values are documented in docs/NETWORK_STORAGE.md.
"""

import argparse
import logging
import os
from pathlib import Path
import tempfile

from impacket import ntlm, smbserver, smb3structs
from impacket.nt_errors import STATUS_END_OF_FILE


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=1445)
    parser.add_argument("--pid-file", type=Path)
    parser.add_argument("--verbose", action="store_true")
    args = parser.parse_args()
    logging.basicConfig(level=logging.DEBUG if args.verbose else logging.WARNING)
    if args.pid_file:
        args.pid_file.write_text(str(os.getpid()), encoding="ascii")
    with tempfile.TemporaryDirectory(prefix="mp-smb-test-") as root:
        server = smbserver.SimpleSMBServer(listenAddress="127.0.0.1", listenPort=args.port)
        server.addShare("test", root)
        server.setSMB2Support(True)
        # Impacket 0.13.1 sends a READ response body with STATUS_END_OF_FILE.
        # MS-SMB2 requires an ERROR body for that status; strict clients otherwise
        # fail parsing EOF. Correct only the fixture's wire response, not the app.
        core = server.getServer()
        original_read = None

        def read_with_valid_eof(connection_id, smb_server, packet):
            commands, packets, status = original_read(connection_id, smb_server, packet)
            if status == STATUS_END_OF_FILE:
                return [smb3structs.SMB2Error()], None, status
            return commands, packets, status

        original_read = core.hookSmb2Command(smb3structs.SMB2_READ, read_with_valid_eof)
        if os.name == "nt":
            # Python's Windows file descriptors do not allow os.rename while
            # the rename-only SMB handle remains open. A real SMB server does.
            # Close/reopen only that fixture handle around the actual rename;
            # keep Impacket's containment/conflict checks and returned status.
            original_set_info = None

            def set_info_with_windows_rename(connection_id, smb_server, packet):
                request = smb3structs.SMB2SetInfo(packet["Data"])
                data = smb_server.getConnectionData(connection_id)
                entry = data["OpenedFiles"].get(request["FileID"].getData())
                if (request["InfoType"] != smb3structs.SMB2_0_INFO_FILE
                        or request["FileInfoClass"] != smb3structs.SMB2_FILE_RENAME_INFO
                        or not entry or entry["FileHandle"] < 0
                        or not os.path.isfile(entry["FileName"])):
                    return original_set_info(connection_id, smb_server, packet)
                position = os.lseek(entry["FileHandle"], 0, os.SEEK_CUR)
                os.close(entry["FileHandle"])
                entry["FileHandle"] = -1
                try:
                    return original_set_info(connection_id, smb_server, packet)
                finally:
                    entry["FileHandle"] = os.open(entry["FileName"], os.O_RDONLY | os.O_BINARY)
                    os.lseek(entry["FileHandle"], position, os.SEEK_SET)

            original_set_info = core.hookSmb2Command(smb3structs.SMB2_SET_INFO, set_info_with_windows_rename)
        server.addCredential("tester", 0, ntlm.compute_lmhash("test-only").hex(),
                             ntlm.compute_nthash("test-only").hex())
        print(f"SMB2 test fixture ready: 127.0.0.1:{args.port}/test (PID {os.getpid()})", flush=True)
        try:
            server.start()
        except KeyboardInterrupt:
            server.stop()


if __name__ == "__main__":
    main()
