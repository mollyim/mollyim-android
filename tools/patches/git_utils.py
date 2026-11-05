"""Small Git subprocess wrapper shared by source proofs and their fixtures.

Extracted from PR #7's replay helper. Repository and index selection is explicit;
the historical pilot's evidence and validation policy are not imported here.
"""
import subprocess

from forest import clean_git_env


class GitError(RuntimeError):
    pass


def git(repo, *args, check=True, env=None, binary=False, index_file=None):
    """Use the explicit repository, with an optional caller-created private index."""
    cmd = ['git', '-C', str(repo), *args]
    run_env = clean_git_env(env)
    if index_file is not None:
        run_env['GIT_INDEX_FILE'] = str(index_file)
    result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                            env=run_env)
    if check and result.returncode:
        detail = result.stdout.decode(errors='replace')
        raise GitError(f"Command failed ({result.returncode}): {' '.join(cmd)}\n{detail}")
    if not binary:
        result.stdout = result.stdout.decode(errors='replace')
    result.output = result.stdout
    return result
