/* MIT licensed, part of Pocket Agent.
 * Android's x86_64 16 KB simulator changes Bionic sysconf, while Linux's
 * executable mappings still use 4 KB kernel pages. PRoot must map guests
 * using the kernel's size, not the simulated Android application size.
 * Real 16 KB kernels keep their 16 KB size. This library runs only in PRoot.
 */
#include <dlfcn.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static long kernel_page_size;
static long (*original_sysconf)(int);

__attribute__((constructor)) static void initialize(void) {
    original_sysconf = (long (*)(int))dlsym(RTLD_NEXT, "sysconf");
    FILE *maps = fopen("/proc/self/smaps", "r");
    if (maps) {
        char line[512];
        while (fgets(line, sizeof(line), maps)) {
            unsigned long kb;
            if (sscanf(line, "KernelPageSize: %lu kB", &kb) == 1) {
                if (kb == 4 || kb == 16 || kb == 64) kernel_page_size = (long)kb * 1024;
                break;
            }
        }
        fclose(maps);
    }
    fprintf(stderr, "Pocket runtime pages: Android=%ld kernel=%ld\n", original_sysconf ? original_sysconf(_SC_PAGESIZE) : -1, kernel_page_size);
    /* Never inject an Android/Bionic library into the Linux/glibc guest. */
    unsetenv("LD_PRELOAD");
}

long sysconf(int name) {
    /* Bionic gives these synonymous POSIX queries distinct numeric IDs. */
    if ((name == _SC_PAGESIZE || name == _SC_PAGE_SIZE) && kernel_page_size) return kernel_page_size;
    if (!original_sysconf) original_sysconf = (long (*)(int))dlsym(RTLD_NEXT, "sysconf");
    return original_sysconf ? original_sysconf(name) : -1;
}
