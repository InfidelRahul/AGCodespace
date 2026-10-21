# Remote Codespaces and Antigravity CLI flow

The Remote screen uses the Ubuntu shell as a local transport terminal and GitHub CLI/OpenSSH to connect to a remote Codespace. It does not use a VS Code editor integration.

1. Install `gh`, `openssh-client`, `curl`, and CA certificates inside Ubuntu.
2. Authenticate with `gh auth login --web` and follow GitHub's sign-in flow.
3. Run `gh codespace list` and enter a Codespace name from the results.
4. Connect using `gh codespace ssh -c <codespace-name>`.
5. Confirm in the app UI that the SSH session is connected to the intended Codespace. This confirmation gates remote Antigravity actions to reduce accidental execution on the local shell.
6. Install Antigravity CLI using Google's documented Linux installer: `curl -fsSL https://antigravity.google/cli/install.sh | bash`.
7. Launch the remote session using `agy --remote-control --dangerously-skip-permissions` only after reviewing the risk. The app does not auto-launch this command.
8. The Remote screen detects the latest HTTP(S) URL printed in terminal output and offers to open it in Android's browser. Verify the URL before opening it. Complete the browser login and paste the returned code back into the terminal when prompted.

Official Antigravity CLI installation/auth documentation: https://www.antigravity.google/docs/cli/install/

The current terminal renderer is a compact VT screen implementation. It handles common cursor addressing, erase, scroll, and alternate-screen sequences, but it is not yet a complete xterm implementation. Test the actual `agy` TUI, browser OAuth loop, resize behavior, and Codespaces connection on a physical Android device before release.
