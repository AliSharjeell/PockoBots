/* MIT licensed, part of Pocket Agent.
 * Android's x86_64 app syscall policy permits renameat but rejects the legacy
 * rename syscall used by glibc/libuv. Preserve atomic rename semantics using
 * the supported equivalent. ARM64 uses renameat already.
 */
#define _GNU_SOURCE
#include <fcntl.h>
#include <sys/syscall.h>
#include <unistd.h>

int rename(const char *oldpath, const char *newpath) {
    return syscall(SYS_renameat, AT_FDCWD, oldpath, AT_FDCWD, newpath);
}
