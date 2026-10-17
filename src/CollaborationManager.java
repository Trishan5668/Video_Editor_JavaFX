import java.util.HashMap;
import java.util.Map;

public class CollaborationManager {
    private final Map<String, String> binLocks = new HashMap<>();

    public synchronized boolean lockBin(String binId, String userId) {
        if (binId == null || userId == null) {
            return false;
        }
        if (binLocks.containsKey(binId) && !userId.equals(binLocks.get(binId))) {
            return false;
        }
        binLocks.put(binId, userId);
        return true;
    }

    public synchronized void unlockBin(String binId, String userId) {
        if (binId == null || userId == null) {
            return;
        }
        if (userId.equals(binLocks.get(binId))) {
            binLocks.remove(binId);
        }
    }

    public synchronized String getBinOwner(String binId) {
        return binLocks.get(binId);
    }
}
