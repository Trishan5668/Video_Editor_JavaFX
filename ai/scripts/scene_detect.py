#!/usr/bin/env python
import argparse
import os
import sys


def emit_error(message: str, code: int = 1):
    print(f"ERROR: {message}")
    sys.exit(code)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--threshold", type=float, default=27.0)
    args = parser.parse_args()

    if not os.path.exists(args.input):
        emit_error(f"Input clip not found: {args.input}")

    try:
        from scenedetect import open_video, SceneManager
        from scenedetect.detectors import ContentDetector
    except Exception as ex:
        emit_error(f"PySceneDetect dependency missing: {ex}")

    try:
        video = open_video(args.input)
        scene_manager = SceneManager()
        scene_manager.add_detector(ContentDetector(threshold=max(1.0, args.threshold)))
        scene_manager.detect_scenes(video)
        scenes = scene_manager.get_scene_list()

        cuts = []
        for idx, (start, _end) in enumerate(scenes):
            if idx == 0:
                continue
            cuts.append(round(start.get_seconds(), 3))

        print("CUTS: " + ",".join(str(c) for c in cuts))
        print(f"SCENES: {len(scenes)}")
    except Exception as ex:
        emit_error(str(ex))


if __name__ == "__main__":
    main()
