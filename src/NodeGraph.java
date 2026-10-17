import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NodeGraph {
    private final List<EffectNode> nodes = new ArrayList<>();
    private final List<NodeConnection> connections = new ArrayList<>();

    public EffectNode addNode(NodeType type, String label) {
        EffectNode node = new EffectNode(type, label);
        nodes.add(node);
        return node;
    }

    public void connect(String fromNodeId, String toNodeId) {
        connections.add(new NodeConnection(fromNodeId, toNodeId));
    }

    public List<EffectNode> getNodes() {
        return Collections.unmodifiableList(nodes);
    }

    public List<NodeConnection> getConnections() {
        return Collections.unmodifiableList(connections);
    }
}
