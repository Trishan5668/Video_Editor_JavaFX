#!/usr/bin/env python
import argparse
import os
import sys


def emit_error(message: str, code: int = 1):
    print(f"ERROR: {message}")
    sys.exit(code)


def _try_translate_locally(text: str, src: str, tgt: str) -> str:
    if not text.strip() or src == tgt:
        return text
    try:
        from transformers import pipeline

        translator = pipeline("translation", model="facebook/m2m100_418M")
        translated = translator(text, src_lang=src, tgt_lang=tgt, max_length=512)
        if translated and isinstance(translated, list):
            candidate = translated[0].get("translation_text", "")
            if candidate:
                return candidate
    except Exception:
        # Graceful fallback: keep original text when translation model isn't available.
        pass
    return text


def write_srt(segments, output_file: str):
    os.makedirs(os.path.dirname(output_file), exist_ok=True)
    with open(output_file, "w", encoding="utf-8") as f:
        for idx, seg in enumerate(segments, start=1):
            start = seg.get("start", 0.0)
            end = seg.get("end", max(0.2, start + 1.0))
            text = (seg.get("text", "") or "").strip()
            if not text:
                continue
            f.write(f"{idx}\n")
            f.write(f"{format_srt_time(start)} --> {format_srt_time(end)}\n")
            f.write(f"{text}\n\n")


def format_srt_time(seconds: float) -> str:
    ms = int(max(0, seconds) * 1000)
    h = ms // 3600000
    ms %= 3600000
    m = ms // 60000
    ms %= 60000
    s = ms // 1000
    ms %= 1000
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--source-lang", default="auto")
    parser.add_argument("--target-lang", default="auto")
    args = parser.parse_args()

    if not os.path.exists(args.input):
        emit_error(f"Input media not found: {args.input}")

    try:
        import whisper
    except Exception as ex:
        emit_error(f"Whisper dependency missing: {ex}")

    try:
        model = whisper.load_model("base")
        task = "translate" if args.target_lang == "en" and args.source_lang != "en" else "transcribe"

        result = model.transcribe(
            args.input,
            language=None if args.source_lang == "auto" else args.source_lang,
            task=task,
            fp16=False,
        )
        language = result.get("language", args.source_lang)
        segments = result.get("segments", [])

        # Optional local translation for non-English targets.
        if args.target_lang not in ("auto", "", language):
            for seg in segments:
                seg["text"] = _try_translate_locally(seg.get("text", ""), language or "en", args.target_lang)

        write_srt(segments, args.output)
        print(f"OUTPUT: {args.output}")
        print(f"LANGUAGE: {language}")
        print(f"SEGMENTS: {len(segments)}")
    except Exception as ex:
        emit_error(str(ex))


if __name__ == "__main__":
    main()
