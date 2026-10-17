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
    parser.add_argument("--detect", choices=["face", "object"], default="object")
    parser.add_argument("--chroma", default="1")
    parser.add_argument("--output-mask", required=True)
    parser.add_argument("--bg", default="")
    args = parser.parse_args()

    if not os.path.exists(args.input):
        emit_error(f"Input clip not found: {args.input}")

    try:
        import cv2
        import numpy as np
    except Exception as ex:
        emit_error(f"OpenCV dependency missing: {ex}")

    cap = cv2.VideoCapture(args.input)
    if not cap.isOpened():
        emit_error("Unable to open input video")

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    width = int(cap.get(cv2.CAP_PROP_FRAME_WIDTH) or 1280)
    height = int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT) or 720)

    os.makedirs(os.path.dirname(args.output_mask), exist_ok=True)
    writer = cv2.VideoWriter(
        args.output_mask,
        cv2.VideoWriter_fourcc(*"mp4v"),
        fps,
        (width, height),
        True,
    )

    face_cascade = None
    if args.detect == "face":
        cascade_path = cv2.data.haarcascades + "haarcascade_frontalface_default.xml"
        face_cascade = cv2.CascadeClassifier(cascade_path)

    bg_sub = cv2.createBackgroundSubtractorMOG2(history=500, detectShadows=False)
    bg_replacement = None
    if args.bg and os.path.exists(args.bg):
        if args.bg.lower().endswith((".png", ".jpg", ".jpeg", ".bmp", ".webp")):
            bg_replacement = cv2.imread(args.bg)

    points = []
    frame_idx = 0
    while True:
        ok, frame = cap.read()
        if not ok:
            break

        cx, cy = width // 2, height // 2
        mask = np.zeros((height, width), dtype=np.uint8)

        if face_cascade is not None:
            gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
            faces = face_cascade.detectMultiScale(gray, 1.1, 4)
            if len(faces) > 0:
                x, y, w, h = faces[0]
                cx = int(x + w / 2)
                cy = int(y + h / 2)
                cv2.rectangle(mask, (x, y), (x + w, y + h), 255, -1)
            else:
                fg = bg_sub.apply(frame)
                mask = cv2.threshold(fg, 180, 255, cv2.THRESH_BINARY)[1]
        else:
            fg = bg_sub.apply(frame)
            mask = cv2.threshold(fg, 180, 255, cv2.THRESH_BINARY)[1]
            contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            if contours:
                c = max(contours, key=cv2.contourArea)
                x, y, w, h = cv2.boundingRect(c)
                cx = int(x + w / 2)
                cy = int(y + h / 2)
                cv2.rectangle(mask, (x, y), (x + w, y + h), 255, -1)

        points.append((frame_idx, round(cx / max(1, width), 4), round(cy / max(1, height), 4)))

        if args.chroma == "1" and bg_replacement is not None:
            bg = cv2.resize(bg_replacement, (width, height))
            inv = cv2.bitwise_not(mask)
            fg = cv2.bitwise_and(frame, frame, mask=mask)
            bg_part = cv2.bitwise_and(bg, bg, mask=inv)
            composed = cv2.add(fg, bg_part)
            writer.write(composed)
        else:
            mask_bgr = cv2.cvtColor(mask, cv2.COLOR_GRAY2BGR)
            writer.write(mask_bgr)

        frame_idx += 1

    cap.release()
    writer.release()

    packed_points = "|".join(f"{f},{x},{y}" for f, x, y in points[::max(1, int(fps // 2) or 1)])
    print(f"SUBJECT: cv-{os.path.basename(args.input)}")
    print(f"POINTS: {packed_points}")
    print(f"MASK: {args.output_mask}")


if __name__ == "__main__":
    main()
