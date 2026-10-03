# MNN kFdChunk ODR patch (common-base)

Copied from `integrations/operit-upstream-build/mnn-link-fix/portable/` (apply script, hashes, review diff). Fixtures and `verify.cmake` were not copied.

Pinned MNN commit: `451006a22d059db759eb036585f2ad6b711feaeb`.

Runtime insert is equivalent to `patches/0001-kFdChunk-odr-out-of-class-definition.patch`:

```cpp
constexpr int AttentionBufExecution::kFdChunk;
```

Does not change MNN commit, backend flags, or `-std`. Does not call `patch(1)`. Paths are relative to this directory (`CMAKE_CURRENT_FUNCTION_LIST_DIR`).

## Apply

```text
cmake -P apply_kFdChunk_odr.cmake -- <MNN_SOURCE_DIR>
```

- Unpatched hashes: write cpp, hpp unchanged.
- Already patched: idempotent skip.
- Unknown source: refuse, do not overwrite.

## CMakeLists hook

`llm/mnn/CMakeLists.txt` includes this apply script after `operit_prepare_git_source` for MNN and before `add_subdirectory`. Include failure is fatal. If `OPERIT_MNN_GIT_REF` is already cached to any other ref, configure refuses rather than silently changing versions.
