#!/usr/bin/env bash
set -euo pipefail

run_tests=true
profile=full
if [[ "$GITHUB_EVENT_NAME" == push && "$GITHUB_REF_NAME" != "${DEFAULT_BRANCH:-main}" ]]; then
  if pr_repositories=$(gh api --method GET \
    "repos/$GITHUB_REPOSITORY/pulls" \
    -f state=open -f head="$GITHUB_REPOSITORY_OWNER:$GITHUB_REF_NAME" \
    -f per_page=100 --paginate --jq '.[].head.repo.full_name'); then
    while IFS= read -r pr_repository; do
      if [[ "$pr_repository" == "$GITHUB_REPOSITORY" ]]; then
        run_tests=false
        break
      fi
    done <<< "$pr_repositories"
  else
    echo '::warning::Could not check open pull requests; running full push tests.'
  fi
elif [[ "$GITHUB_EVENT_NAME" == pull_request ]]; then
  if gh api "repos/$GITHUB_REPOSITORY/pulls/$PR_NUMBER/files" \
    --paginate --jq '.[] | .filename, (.previous_filename // empty)' > "$RUNNER_TEMP/changed-paths"; then
    profile=$(python3 .github/scripts/ci_profile.py < "$RUNNER_TEMP/changed-paths")
  else
    echo '::warning::Could not check changed paths; running full PR tests.'
  fi
elif [[ "$GITHUB_EVENT_NAME" == workflow_dispatch && "$REQUESTED_PROFILE" == representative ]]; then
  profile=representative
fi
echo "run_tests=$run_tests" >> "$GITHUB_OUTPUT"
echo "profile=$profile" >> "$GITHUB_OUTPUT"
echo "Verification scope: $profile; run tests: $run_tests" >> "$GITHUB_STEP_SUMMARY"
