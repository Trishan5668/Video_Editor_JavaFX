public final class TimelineModelCloner {
    private TimelineModelCloner() {
    }

    public static TimelineModel deepCopy(TimelineModel source) {
        TimelineModel clone = new TimelineModel();
        if (source == null || source.getTracks().isEmpty()) {
            return clone;
        }

        for (int i = 1; i < source.getTracks().size(); i++) {
            clone.addTrack(source.getTracks().get(i).getType());
        }

        for (int t = 0; t < source.getTracks().size(); t++) {
            for (Clip clip : source.getTracks().get(t).getClips()) {
                clone.addClipToTrack(t, copyClip(clip));
            }
        }
        return clone;
    }

    private static Clip copyClip(Clip clip) {
        Clip copy = new Clip(
                clip.getClipId(),
                clip.getSourcePath(),
                clip.getStartSeconds(),
                clip.getEndSeconds(),
                clip.getVolume(),
                clip.getTransitionIn(),
                clip.getTransitionOut(),
                clip.getTransitionInDurationSeconds(),
                clip.getTransitionOutDurationSeconds(),
                clip.getTimelineStartSeconds(),
                clip.getPositionX(),
                clip.getPositionY(),
                clip.getScale(),
                clip.getOpacity(),
                clip.getTextContent(),
                clip.getZIndex(),
                clip.getTextFontSize()
        );
        copy.cacheWaveform(clip.getWaveformPeaks());
        return copy;
    }
}
