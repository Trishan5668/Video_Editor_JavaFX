public class NodeConnection {
    private final String fromNodeId;
    private final String toNodeId;

    public NodeConnection(String fromNodeId, String toNodeId) {
        this.fromNodeId = fromNodeId;
        this.toNodeId = toNodeId;
    }

    public String getFromNodeId() {
        return fromNodeId;
    }

    public String getToNodeId() {
        return toNodeId;
    }
}
