import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ExportRequest {
    private final String outputPath;
    private final ExportFormat format;
    private final boolean useRawMedia;
    private final int bitDepth;
    private final Map<String, String> metadata;

    public ExportRequest(String outputPath, ExportFormat format, boolean useRawMedia, int bitDepth) {
        this(outputPath, format, useRawMedia, bitDepth, new HashMap<>());
    }

    public ExportRequest(String outputPath, ExportFormat format, boolean useRawMedia, int bitDepth, Map<String, String> metadata) {
        this.outputPath = outputPath;
        this.format = format == null ? ExportFormat.H264_MP4 : format;
        this.useRawMedia = useRawMedia;
        this.bitDepth = Math.max(8, bitDepth);
        this.metadata = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    public String getOutputPath() {
        return outputPath;
    }

    public ExportFormat getFormat() {
        return format;
    }

    public boolean isUseRawMedia() {
        return useRawMedia;
    }

    public int getBitDepth() {
        return bitDepth;
    }

    public Map<String, String> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }
}
