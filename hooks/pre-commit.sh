#!/bin/sh

echo '[githooks] formatting files using spotless'
echo

./gradlew spotlessApply

# Only re-check the files actually staged for this commit - not the whole working tree, which
# would abort on unrelated unstaged changes elsewhere in the repo (e.g. when landing several
# pending changes as separate atomic commits).
staged_files="$(git diff --cached --name-only)"
changed_files="$(git diff --name-only -- $staged_files)"
echo

# check if spotlessApply reformatted any of the staged files after they were staged
if [[ -n "$changed_files" ]];
then
    echo '[githooks] aborting commit, spotless reformatted staged files:'
    echo "$changed_files"
    exit 1
else
    echo '[githooks] continuing commit, staged files are already formatted'
fi
