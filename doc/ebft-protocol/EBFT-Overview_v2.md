# Changes in EBFT version 2

This document only elaborates on what has changed in version 2. Please refer to the `EBFT-Overview.md` document for a
full overview.

## Signature fetching

The signature fetching when node is in `Prepared` state has been optimized. Nodes will include block signature in the
status message when it moves to `HaveBlock`. This means that nodes in prepared state normally will not have to ask other
nodes for their signatures (since it should already have them unless there were some issues receiving status messages).