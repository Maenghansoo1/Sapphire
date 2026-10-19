@echo off
rem Vision service on port 8001. First-time setup is described in README.md.
set HF_HUB_OFFLINE=1
cd /d "%~dp0"
if not exist ".venv\Scripts\python.exe" goto novenv
if exist "models\tokenizer.json" goto run
".venv\Scripts\python.exe" -c "import sentence_transformers" 1>nul 2>nul
if errorlevel 1 goto nomodels
echo models\ ONNX files not found. Using PyTorch instead (slower start). See README "ONNX".
:run
".venv\Scripts\python.exe" -m uvicorn app:app --port 8001
echo.
echo Server stopped. See the messages above.
pause
exit /b
:novenv
echo .venv not found. Run first:
echo   py -3.12 -m venv .venv
echo   .venv\Scripts\python.exe -m pip install -r requirements.txt
pause
exit /b 1
:nomodels
echo models\ ONNX files not found. Either copy these 3 files from a teammate into models\
echo   image_encoder.fp16.onnx, text_encoder.fp16.onnx, tokenizer.json
echo or make them once (needs PyTorch, a few minutes):
echo   .venv\Scripts\python.exe -m pip install -r requirements-export.txt
echo   .venv\Scripts\python.exe export_onnx.py
pause
exit /b 1
