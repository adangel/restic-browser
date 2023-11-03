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
