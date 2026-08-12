# Postchain-gtv

GTV is split across two modules. `postchain-gtv-min` holds the format itself — the types, ASN.1 DER encoding and
decoding, merkle hashing with proofs and virtual GTV, and the textual notation — and depends on nothing beyond
`postchain-common-min`, so it survives GraalVM native-image. This module adds the parts that need a library:
`GtvObjectMapper` and its annotations (`kotlin-reflect`), the Gson bindings, GtvML (JAXB) and `GtvFileReader`.
Everything keeps the package names it always had, so which module a class comes from does not affect callers.

A third module, `postchain-gtv-jackson`, implements `postchain-gtv-min`'s `GtvJsonCodec` on Jackson's streaming
API. It is an alternative to the Gson bindings here, not a replacement, and pulls in no reflection.

## GTV

GTV is a general purpose protocol, used to express data in

- Primitive types (integers, strings, byte arrays, etc)
- Complex types (only Array and Dictionary)

## ASN.1 as underlying protocol

GTV is usually converted to ASN.1 DER when it's transported over the wire, and this is what we mean when
we say "binary" format.

The schema for GTV encoding can be found [here](../postchain-gtv-min/src/main/resources/asn/gtv_messages.asn),
alongside the codec that implements it.

## XML and JSON

Sometimes we use XML or JSON to express GTV, and we have code to translate between these format to GTV kotlin types
and back. For XML we use "gtvml" classes.

For JSON there is one mapping and two implementations of it, both behind `postchain-gtv-min`'s `GtvJsonCodec`:
`net.postchain.gtv.json.gson.GtvJson` here, and `net.postchain.gtv.json.jackson.GtvJson` in
`postchain-gtv-jackson`. They are interchangeable down to the byte, which `JsonCodecEquivalenceTest` holds them
to over generated values and generated malformed documents. Gson is the one that defines those bytes, so it is
also checked against the older `make_gtv_gson()` entry points in `gtvjson.kt`, which remain for existing
callers.

## GTV merkle hash calculation

The GTV merkle hash calculation is a process for creating a cryptographic hash of a GTV structure.
This hash can be used to verify the integrity of the data and to create merkle proofs for specific parts of the data.

### Overview

The merkle hash calculation process involves the following steps:

1. Transform the GTV structure into a binary tree
2. Calculate hashes for each node in the tree
3. Combine these hashes to produce a single merkle root hash

### Binary Tree Construction

The GTV structure is transformed into a binary tree as follows:

1. **Primitive Values**: Integers, strings, byte arrays, etc. become leaf nodes

2. **Collections**: Arrays and dictionaries become specialized container nodes with subtrees

    - **Arrays**:
        - Elements are processed in their original order
        - Each element becomes a subtree (either a leaf for primitives or a container node for nested collections)
        - These subtrees are arranged in a balanced binary tree structure

    - **Dictionaries**:
        - Key-value pairs are sorted by lexicographically ordering the keys as strings in ascending order
        - For example, a dictionary with keys "c", "a", "b" will always be processed in the order "a", "b", "c"
        - Each key becomes a leaf node
        - Each value becomes either a leaf node (for primitives) or a subtree (for nested collections)
        - Keys and their corresponding values are paired together in the tree

3. **Binary Tree Structure**: The tree maintains a strict "full binary tree" structure, where each node must have either
   zero or two children. This is achieved by:

    - **Empty Collections**: When an array or dictionary is empty, it's represented by a container node with two empty
      leaf children.

    - **Single-Element Arrays**: When an array has a single element, it's represented by an array node with the single
      element as the left child and an empty leaf as the right child.

    - **Multi-Element Collections**:
        - Pairing adjacent elements to create nodes
        - If there's an odd number of elements, the last one is carried to the next layer
        - Repeating this process recursively until a single root node remains

### Hash Calculation

Hashes are calculated as follows:

1. **Leaf Nodes (Primitive Values)**:
    - Serialize the GTV primitive value to a binary format using the ASN.1 GTV schema
    - Prefix the serialized data with a leaf prefix byte (0x01)
    - Calculate the hash of the prefixed data using `SHA-256`
    - Empty leaves are represented by a predefined EMPTY_HASH value (all zeroes)

2. **Container Root Nodes**:
    - **Array Root Nodes**: Use a special prefix byte (0x07) when combining their children's hashes
    - **Dictionary Root Nodes**: Use a special prefix byte (0x08) when combining their children's hashes

3. **Regular Internal Nodes**:
    - Use a generic node prefix byte (0x00) when combining their children's hashes
    - These nodes form the internal structure of the tree for collections with more than two elements

4. **Hash Calculation Process**:
    - The hash is calculated bottom-up, from leaves to the root
    - For each node: prefix + hash(left child) + hash(right child) is hashed together
    - The root node's hash becomes the merkle root hash of the entire structure

### Pseudocode

```
// Constants
HASH_PREFIX_NODE = 0x00
HASH_PREFIX_LEAF = 0x01
HASH_PREFIX_NODE_ARRAY = 0x07
HASH_PREFIX_NODE_DICT = 0x08
EMPTY_HASH = 32 bytes of zeros (0x00)

// ==================== DATA STRUCTURES ====================

// Base types for tree elements
interface BinaryTreeElement

class Node implements BinaryTreeElement:
    left: BinaryTreeElement
    right: BinaryTreeElement

class Leaf implements BinaryTreeElement:
    content: GTV value

class EmptyLeaf implements BinaryTreeElement

class ContainerNode implements BinaryTreeElement:
    content: Container content
    size: Number of items

class ArrayHeadNode extends ContainerNode

class DictHeadNode extends ContainerNode

// ==================== MAIN FUNCTIONS ====================

// Main entry point for calculating merkle hash
function calculateMerkleHash(gtv):
    treeRoot = buildTree(gtv)
    return calculateNodeHash(treeRoot)

// ==================== TREE BUILDING ====================

// Build a merkle tree from a GTV value
function buildTree(gtv):
    if gtv is primitive (integer, string, etc.):
        return Leaf(gtv)
    else if gtv is array:
        return handleArrayContainer(gtv)
    else if gtv is dictionary:
        return handleDictContainer(gtv)

// Handle array containers
function handleArrayContainer(array):
    if array is empty:
        return ArrayHeadNode(EmptyLeaf(), EmptyLeaf(), array, 0)
    
    // Build leaves recursively for all items
    leaves = []
    for each item in array:
        leaves.add(buildTree(item))

    // Build tree structure for multiple items
    treeRoot = buildHigherLayer(leaves)

    if treeRoot is Node:
        return ArrayHeadNode(treeRoot.left, treeRoot.right, array, array.size)
    else:
        // If treeRoot is not a Node (i.e. a leaf or a container node), it becomes the left child
        return ArrayHeadNode(treeRoot, EmptyLeaf(), array, array.size)

// Handle dictionary containers
function handleDictContainer(dict):
    if dict is empty:
        return DictHeadNode(EmptyLeaf(), EmptyLeaf(), dict, 0)
    
    // Sort keys for deterministic hashing
    sortedItems = sort dict items by key
    
    // Build leaves recursively
    leaves = []
    for each (key, value) in sortedItems:
        leaves.add(Leaf(key as GTV string))  // Key as leaf
        leaves.add(buildTree(value))         // Value subtree
    
    if leaves.size == 2:  // Single key-value pair
        return DictHeadNode(leaves[0], leaves[1], dict, dict.size)
    
    // Build tree structure for multiple key-value pairs
    treeRoot = buildHigherLayer(leaves)

    if treeRoot is not Node:
        throw Error("Tree root for a multi-element dictionary must be a Node")

    return DictHeadNode(treeRoot.left, treeRoot.right, dict, dict.size)

// Build higher layers of the tree (balanced binary tree construction)
function buildHigherLayer(elements):
    if elements is empty:
        throw Error("Must pass at least one element")
    
    if elements.size == 1:
        return elements[0]
    
    // Create the next layer by pairing adjacent elements
    result = []
    i = 0
    
    // Pair up elements and create nodes
    while i < elements.size - 1:
        result.add(Node(elements[i], elements[i + 1]))
        i += 2
    
    // Handle odd number of elements
    if i < elements.size:
        result.add(elements[i])
    
    // Recursively build the next layer
    return buildHigherLayer(result)

// ==================== HASH CALCULATION ====================

// Calculate hash for any node type
function calculateNodeHash(node):
    if node is EmptyLeaf:
        return EMPTY_HASH
    
    else if node is Leaf:
        serialized = serialize_gtv_to_asn1(node.content)
        return SHA256(HASH_PREFIX_LEAF + serialized)
    
    else if node is ArrayHeadNode:
        leftHash = calculateNodeHash(node.left)
        rightHash = calculateNodeHash(node.right)
        return SHA256(HASH_PREFIX_NODE_ARRAY + leftHash + rightHash)
    
    else if node is DictHeadNode:
        leftHash = calculateNodeHash(node.left)
        rightHash = calculateNodeHash(node.right)
        return SHA256(HASH_PREFIX_NODE_DICT + leftHash + rightHash)
    
    else if node is Node:
        leftHash = calculateNodeHash(node.left)
        rightHash = calculateNodeHash(node.right)
        return SHA256(HASH_PREFIX_NODE + leftHash + rightHash)

    else:
        throw Error("Unexpected node type")
```

### Merkle Proofs

Merkle proofs allow verification that a specific part of a GTV structure is included in the merkle root hash without
requiring the entire structure. A merkle proof consists of:

1. The value to be proven
2. A path from the value to the root, including all necessary sibling hashes

To verify a merkle proof:

1. Calculate the hash of the value
2. Combine it with the sibling hashes according to the path
3. Compare the resulting root hash with the expected root hash

### Version Differences in GTV Merkle Hash Calculation

The GTV Merkle hash implementation has two versions:

- **V1 (Original Implementation)**: Contains a bug that causes hash collisions between different array structures.

  **The Bug**: In V1, when an array contained only a single collection element, that element was "flattened" since its
  left and right child was set as the left and right child of the parent collection. Meaning it did not e.g. properly
  distinguish between cases like:

    1. An array containing a single primitive value (e.g., `[5]`)
    2. An array containing a single array (e.g., `[[5]]`)

  This caused both structures to generate identical merkle hashes despite representing different data structures.

- **V2 (Fixed Implementation)**: Resolves the hash collision bug in V1.

  **The Fix**: V2 handles arrays containing a single collection element correctly by not flattening it but rather
  setting the single element collection root node as the left child and an empty leaf as the right child.
