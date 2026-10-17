import java.util.UUID;

public class EffectNode {
    private final String nodeId = UUID.randomUUID().toString();
    private final NodeType type;
    private final String label;
    private final NodeParameterSet parameters = new NodeParameterSet();

    public EffectNode(NodeType type, String label) {
        this.type = type == null ? NodeType.EFFECT : type;
        this.label = label == null ? this.type.name() : label;
    }

    public String getNodeId() {
        return nodeId;
    }

    public NodeType getType() {
        return type;
    }

    public String getLabel() {
        return label;
    }

    public NodeParameterSet getParameters() {
        return parameters;
    }
}
