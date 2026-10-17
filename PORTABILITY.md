# Video Editor Portability Notes

This project is now path-independent.

## What changed

- All runtime paths are resolved from the project root using `System.getProperty("user.dir")`.
- Media imports are copied into the local `assets/` directory.
- FFmpeg is resolved from bundled binaries in `bin/` with OS-specific selection:
  - `bin/ffmpeg.exe` (Windows)
  - `bin/ffmpeg-macos` (macOS)
  - `bin/ffmpeg-linux` (Linux)
- Exports are written to `exports/` with project-relative paths.
- Archive targets are normalized to `archives/` and include missing-file reporting.
- Project metadata is stored in `project.json` using relative references.
- AI runtime folders are now auto-created:
  - `ai/scripts/` for local Python AI adapters
  - `exports/subtitles/` for generated `.srt` files

## Required project structure

- `assets/` (media)
- `bin/` (FFmpeg binaries)
- `exports/` (render output)
- `archives/` (archive output)
- `ai/scripts/` (local AI bridges for Whisper/PySceneDetect/OpenCV)
- `project.json` (project metadata)

## AI setup (local, free/open-source)

The editor now integrates local AI workflows for:
- **Whisper** subtitle generation + translation (`Generate Subtitles`, `Export Subtitles`)
- **PySceneDetect** timeline auto-cut (`Auto-Cut` + threshold slider)
- **OpenCV** motion tracking/face tracking + chroma/background replacement (`Track Motion` in VFX)

Install Python dependencies once:

```bash
pip install -r ai/requirements.txt
```

The Java UI calls Python bridge scripts in `ai/scripts/` and handles failures gracefully:
- missing Python package/model
- invalid media input
- unsupported media format

Errors are surfaced to the workspace status bar and dialogs without crashing the editor.

## Usage

You can move this entire project folder to any machine/location.

As long as you run the app from the project root directory, media loading, FFmpeg rendering,
archiving, and metadata references remain valid.
