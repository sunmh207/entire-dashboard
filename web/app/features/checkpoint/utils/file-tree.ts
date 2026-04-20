/**
 * Build file tree from flat file changes
 */

export interface FileTreeNode {
  name: string
  fullPath: string
  children: FileTreeNode[]
  isFile: boolean
  additions?: number
  deletions?: number
}

export function buildFileTree(
  fileChanges: { file: string; additions: number; deletions: number }[]
): FileTreeNode[] {
  const root: FileTreeNode = {
    name: '',
    fullPath: '',
    children: [],
    isFile: false,
  }

  for (const fc of fileChanges) {
    const path = typeof fc?.file === 'string' ? fc.file.trim() : ''
    if (!path) continue
    const parts = path.split('/').filter(Boolean)
    if (parts.length === 0) continue

    let current = root
    let pathSoFar = ''

    for (let i = 0; i < parts.length; i++) {
      const part = parts[i]
      const isLast = i === parts.length - 1
      pathSoFar = pathSoFar ? `${pathSoFar}/${part}` : part

      if (isLast) {
        current.children.push({
          name: part,
          fullPath: path,
          children: [],
          isFile: true,
          additions: fc.additions ?? 0,
          deletions: fc.deletions ?? 0,
        })
      } else {
        let folder = current.children.find((c) => !c.isFile && c.name === part)
        if (!folder) {
          folder = {
            name: part,
            fullPath: pathSoFar,
            children: [],
            isFile: false,
          }
          current.children.push(folder)
        }
        current = folder
      }
    }
  }

  function sortNode(current: FileTreeNode) {
    current.children.sort((a, b) => {
      if (a.isFile !== b.isFile) return a.isFile ? 1 : -1
      return a.name.localeCompare(b.name)
    })
    current.children.forEach(sortNode)
  }
  sortNode(root)

  return root.children
}

/**
 * Collapse consecutive single-child folders (no files in between) into one display node.
 * Only branching folders (multiple children) or folders with direct files get hierarchical display.
 */
export function collapseSingleChildChains(nodes: FileTreeNode[]): FileTreeNode[] {
  return nodes.map(collapseNode)
}

function collapseNode(node: FileTreeNode): FileTreeNode {
  if (node.isFile) return node

  let current = node
  const pathParts = [node.name]

  while (current.children.length === 1 && !current.children[0].isFile) {
    current = current.children[0]
    pathParts.push(current.name)
  }

  return {
    name: pathParts.join('/'),
    fullPath: current.fullPath,
    children: current.children.map((child) => collapseNode(child)),
    isFile: false,
  }
}

export interface FlatNode {
  node: FileTreeNode
  depth: number
}

export function collectFolderPaths(nodes: FileTreeNode[]): Set<string> {
  const paths = new Set<string>()
  function walk(n: FileTreeNode) {
    if (!n.isFile && n.fullPath) paths.add(n.fullPath)
    n.children.forEach(walk)
  }
  nodes.forEach(walk)
  return paths
}

export function flattenTree(
  nodes: FileTreeNode[],
  expandedPaths: Set<string>,
  depth = 0
): FlatNode[] {
  const result: FlatNode[] = []
  for (const node of nodes) {
    result.push({ node, depth })
    if (!node.isFile && node.fullPath && expandedPaths.has(node.fullPath)) {
      result.push(...flattenTree(node.children, expandedPaths, depth + 1))
    }
  }
  return result
}
