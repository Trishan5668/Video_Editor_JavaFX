public class PrecisionTrimService {

    public void applyTrim(TimelineModel timeline, int trackIndex, int clipIndex, TrimMode mode, long framePosition, FrameRate frameRate) {
        if (timeline == null || frameRate == null || mode == null) {
            return;
        }
        if (mode == TrimMode.RAZOR) {
            double sourceSeconds = frameRate.toSeconds(framePosition);
            timeline.splitClipAtSourceTimeInTrack(trackIndex, clipIndex, sourceSeconds);
            return;
        }

        java.util.List<Clip> clips = timeline.getClipsInTrack(trackIndex);
        if (clipIndex < 0 || clipIndex >= clips.size()) {
            throw new IllegalArgumentException("Invalid clip index");
        }
        Clip target = clips.get(clipIndex);
        double cutTime = frameRate.toSeconds(framePosition);
        double start = target.getStartSeconds();
        double end = target.getEndSeconds();
        double minLen = 1.0 / Math.max(1.0, frameRate.getFps());
        double clampedCut = Math.max(start + minLen, Math.min(end - minLen, cutTime));

        switch (mode) {
            case RIPPLE -> {
                Clip trimmed = target.withRange(start, clampedCut);
                timeline.replaceClipInTrack(trackIndex, clipIndex, trimmed);
            }
            case ROLLING -> {
                if (clipIndex < clips.size() - 1) {
                    Clip right = clips.get(clipIndex + 1);
                    double rightStart = Math.max(clampedCut, right.getStartSeconds());
                    if (right.getEndSeconds() - rightStart >= minLen) {
                        Clip leftUpdated = target.withRange(start, clampedCut);
                        Clip rightUpdated = right.withRange(rightStart, right.getEndSeconds())
                                .withTimelineStart(leftUpdated.getTimelineStartSeconds() + leftUpdated.getDurationSeconds());
                        timeline.replaceClipInTrack(trackIndex, clipIndex, leftUpdated);
                        timeline.replaceClipInTrack(trackIndex, clipIndex + 1, rightUpdated);
                    } else {
                        timeline.replaceClipInTrack(trackIndex, clipIndex, target.withRange(start, clampedCut));
                    }
                } else {
                    timeline.replaceClipInTrack(trackIndex, clipIndex, target.withRange(start, clampedCut));
                }
            }
            case SLIP -> {
                double duration = target.getDurationSeconds();
                double newStart = Math.max(0, clampedCut - (duration / 2.0));
                double newEnd = newStart + duration;
                timeline.replaceClipInTrack(trackIndex, clipIndex, target.withRange(newStart, newEnd));
            }
            case SLIDE -> {
                double timelineTarget = Math.max(0, frameRate.toSeconds(framePosition));
                Clip moved = target.withRange(clampedCut, Math.max(clampedCut + minLen, end))
                        .withTimelineStart(timelineTarget);
                timeline.removeClipFromTrack(trackIndex, clipIndex);
                timeline.addClipToTrack(trackIndex, moved);
            }
            default -> {
            }
        }
    }
}
