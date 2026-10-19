@echo off
rem Vision service on port 8001 (ONNX, no PyTorch). Details: README.md "ONNX".
rem Needs models\ with: image_encoder.fp16.onnx, text_encoder.fp16.onnx, tokenizer.json
rem Optional: set HAWE_VISION_MODEL_DIR to use another model folder.
cd /d "%~dp0"

set MODELS=models
if defined HAWE_VISION_MODEL_DIR set MODELS=%HAWE_VISION_MODEL_DIR%
for %%F in (image_encoder.fp16.onnx text_encoder.fp16.onnx tokenizer.json) do (
  if not exist "%MODELS%\%%F" (
    echo Missing model file: %MODELS%\%%F
    echo Copy the 3 files into %MODELS%\ and run again.
    pause
    exit /b 1
  )
)

if exist ".venv\Scripts\python.exe" goto run

echo .venv not found. Creating it and installing requirements ^(first time only^)...
py -3.12 -m venv .venv 1>nul 2>nul
if not exist ".venv\Scripts\python.exe" py -3 -m venv .venv
if not exist ".venv\Scripts\python.exe" (
  echo Could not create .venv. Install Python 3.12+ ^(with the py launcher^) and run again.
  pause
  exit /b 1
)
".venv\Scripts\python.exe" -m pip install -r requirements.txt
if errorlevel 1 (
  echo pip install failed. See the messages above.
  rmdir /s /q ".venv"
  pause
  exit /b 1
)

:run
set HF_HUB_OFFLINE=1
set HAWE_VISION_BACKEND=onnx
set HAWE_VISION_ONNX_VARIANT=fp16
set HAWE_VISION_MODEL_DIR=%MODELS%
echo Starting vision service on http://127.0.0.1:8001  ^(check: /healthz -^> backend "onnx.fp16"^)
".venv\Scripts\python.exe" -m uvicorn app:app --host 127.0.0.1 --port 8001
echo.
echo Server stopped. See the messages above.
pause
