# PRoot x86_64 loader

Source: termux/proot v5.1.107.96, from the checksum-pinned archive in
runtime/source-lock.json. Original headers and GPL notices are retained.

Pocket Agent modification: loader/loader.c normalizes guest AT_PAGESZ to 4096
on x86_64. Android's 16 KB userspace simulation reports 16384 even though the
x86 kernel maps 4096-byte pages; glibc must receive the kernel value. ARM64
continues using the unmodified upstream loader and its actual kernel page size.

scripts/build-proot-loader.mjs reproduces the freestanding executable with the
Android NDK. This complete modified loader source is distributed in the APK.
