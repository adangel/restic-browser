# Test Repositories

## repo1
* created restic repository 4d935de4a1 at repo1
* format: v2
* Password: `test` or `password`

```shell
RESTIC_PASSWORD=test restic --repo repo1 snapshots
```

## repo2
* created restic repository 3e4cff4464 at repo2
* format: v2
* Password: `test`
* Snapshots:
```
$ restic --repo repo2 snapshot
repository 3e4cff44 opened (version 2, compression level auto)
ID        Time                 Host        Tags        Paths
--------------------------------------------------------------------
2a9e949d  2023-11-03 07:55:16  johndoe                 /home/johndoe
--------------------------------------------------------------------
1 snapshots
```
* Content of snapshot 2a9e949d:
```
$ restic --repo repo2 ls 2a9e949d
repository 3e4cff44 opened (version 2, compression level auto)
[0:00] 100.00%  1 / 1 index files loaded
snapshot 2a9e949d of [/home/johndoe] filtered by [] at 2023-11-03 07:55:16.987988969 +0100 CET):
/home
/home/johndoe
/home/johndoe/subdir1
/home/johndoe/subdir1/a
/home/johndoe/subdir1/a/file1.txt
/home/johndoe/subdir1/a/file2.txt
/home/johndoe/subdir1/b
/home/johndoe/subdir1/b/file1.txt
/home/johndoe/subdir1/b/file2.txt
/home/johndoe/subdir1/c
/home/johndoe/subdir1/c/file1.txt
/home/johndoe/subdir1/c/file2.txt
/home/johndoe/subdir2
/home/johndoe/subdir2/d
/home/johndoe/subdir2/d/file1.txt
/home/johndoe/subdir2/d/file2.txt
/home/johndoe/subdir2/d/file3.txt
/home/johndoe/subdir2/e
/home/johndoe/subdir2/e/file1.txt
/home/johndoe/subdir2/e/file2.txt
/home/johndoe/subdir2/e/file3.txt
/home/johndoe/subdir2/f
/home/johndoe/subdir2/f/file1.txt
/home/johndoe/subdir2/f/file2.txt
/home/johndoe/subdir2/f/file3.txt
```

## repo3
* created restic repository 958c01b38c at repo3
* format: v2
* Password: `test`
* Snapshots:
```
$ restic --repo repo3 snapshots
repository 958c01b3 opened (version 2, compression level auto)
ID        Time                 Host        Tags        Paths
------------------------------------------------------------
f9bd1daf  2023-11-03 18:57:18  adangel                 /data
------------------------------------------------------------
1 snapshots
```
* Content of snapshot f9bd1daf
```
$ restic --repo repo3 ls f9bd1daf
snapshot f9bd1daf of [/data] filtered by [] at 2023-11-03 18:57:18.213904491 +0100 CET):
/data
/data/dir
/data/dir/file.txt
/data/regular.txt
/data/symlink.txt
/data/symlink2.txt
```

## repo4
* created restic repository 45bcb7fd8b at repo4 old
* created restic repository 14ed0f6cbb at repo4
* format: v2
* Password: `test`
* See also <https://restic.net/blog/2015-09-12/restic-foundation1-cdc/>

```
$ dd if=/dev/urandom of=file.raw bs=1M count=2
$ sha256sum file.raw
4360fffcd35689d5ae2ca95b28b289bebf15905d3db33f4e918ae814f9140841  file.raw
# file size is 2097152 bytes
```
* Snapshots:
```
$ restic --repo repo4 snapshots
repository 14ed0f6c opened (version 2, compression level auto)
ID        Time                 Host        Tags        Paths
----------------------------------------------------------------
a9e4ac2f  2023-11-11 22:38:09  adangel                 /file.raw
----------------------------------------------------------------
1 snapshots
```
* Content of snapshot a9e4ac2f:
```
$ restic --repo repo4 ls 36d7d9ee
snapshot a9e4ac2f of [/file.raw] filtered by [] at 2023-11-11 22:38:09.423735641 +0100 CET):
/file.raw
```
