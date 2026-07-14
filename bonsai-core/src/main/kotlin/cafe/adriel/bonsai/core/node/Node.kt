package cafe.adriel.bonsai.core.node

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cafe.adriel.bonsai.core.BonsaiScope
import cafe.adriel.bonsai.core.node.extension.ExpandableNode
import cafe.adriel.bonsai.core.node.extension.ExpandableNodeHandler
import cafe.adriel.bonsai.core.node.extension.SelectableNode
import cafe.adriel.bonsai.core.node.extension.SelectableNodeHandler
import cafe.adriel.bonsai.core.util.randomUUID

typealias NodeComponent<T> = @Composable BonsaiScope<T>.(Node<T>) -> Unit

sealed interface Node<T> {

    val key: String

    val content: T

    val name: String

    val depth: Int

    val isSelected: Boolean

    val iconComponent: NodeComponent<T>

    val nameComponent: NodeComponent<T>
}

// content/name are snapshot-state backed so that recompositions with updated data (e.g. a
// refreshed file list) propagate into already-created nodes and their rows re-render; the
// original library only assigned them at node creation, leaving rows stale forever.
class LeafNode<T>
internal constructor(
    content: T,
    override val depth: Int,
    override val key: String = randomUUID,
    name: String = content.toString(),
    override val iconComponent: NodeComponent<T> = { DefaultNodeIcon(it) },
    override val nameComponent: NodeComponent<T> = { DefaultNodeName(it) },
) : Node<T>, SelectableNode by SelectableNodeHandler() {
    override var content: T by mutableStateOf(content)
        internal set

    override var name: String by mutableStateOf(name)
        internal set
}

class BranchNode<T>
internal constructor(
    content: T,
    override val depth: Int,
    override val key: String = randomUUID,
    name: String = content.toString(),
    override val iconComponent: NodeComponent<T> = { DefaultNodeIcon(it) },
    override val nameComponent: NodeComponent<T> = { DefaultNodeName(it) },
) : Node<T>, SelectableNode by SelectableNodeHandler(), ExpandableNode by ExpandableNodeHandler() {
    override var content: T by mutableStateOf(content)
        internal set

    override var name: String by mutableStateOf(name)
        internal set
}
