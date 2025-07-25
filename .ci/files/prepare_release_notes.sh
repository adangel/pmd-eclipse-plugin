#!/bin/bash
set -e +o pipefail

outfile="release_notes_prepared.md"
infile="ReleaseNotes.md"

if [ ! -e "$infile" ]; then
  echo "File ${infile} not found!"
  exit 1
fi


# Extract the release notes.
# Note: with "set +o pipefail", pipe errors are ignored. They might occur when
# head finishes before grep is done.
# Error message: "grep: write error: Broken pipe"
#
# Note2: "|| test $? -eq 141" is added to ignore SIGPIPE errors when head finishes before grep is done.
# See https://stackoverflow.com/questions/22464786/ignoring-bash-pipefail-for-error-code-141
# But that doesn't seem to work.
BEGIN_LINE=$( (grep -n "^## " "${infile}" || test $? -eq 141) | head -1|cut -d ":" -f 1)
END_LINE=$( (grep -n "^## " "${infile}" || test $? -eq 141) | head -2|tail -1|cut -d ":" -f 1)
END_LINE=$((END_LINE - 1))
EXTRACT=$(head -$END_LINE "${infile}" | tail -$((END_LINE - BEGIN_LINE)))

RELEASE_BODY="A new PMD for Eclipse plugin version has been released.
It is available via the update site: https://pmd.github.io/pmd-eclipse-plugin-p2-site/

$EXTRACT
"
echo "${RELEASE_BODY}" > "${outfile}"
echo "Created file ${outfile}"
