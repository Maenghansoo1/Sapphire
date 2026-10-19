"""그림 인식 모델(M1 int8 TFLite)을 iOS 용 Core ML(`QuickDrawCNN.mlpackage`)로 옮겨요. (A5 항목 9)

coremltools 는 TFLite 를 바로 읽지 못하고 Keras 원본도 저장소에 없어서, TFLite 안의 int8 가중치를 float 로 풀어
같은 신경망(합성곱 5 · 최대 풀링 2 · 전역 평균 · 완전 연결 · softmax)을 MIL 로 다시 쌓아요.
층마다 int8 활성값 양자화(scale·zero point)도 그대로 흉내 내서, Android 가 쓰는 int8 모델과 같은 확률이 나와요.

    python3.13 -m venv venv && venv/bin/pip install coremltools numpy tflite
    venv/bin/python ios/Tools/quickdraw_coreml.py            # 변환 + 골든 확인 (Mac 에서만 확인 가능)

결과: ios/HAWE/Resources/QuickDraw/QuickDrawCNN.mlpackage, quickdraw-categories.json(Android categories.json 과 같은 파일)
골든: ml/quickdraw/golden/*.json 의 expected 를 넣었을 때 golden_outputs.json 과 모든 확률 차이 ≤ 0.02.
"""

import json
import shutil
import sys
from pathlib import Path

import coremltools as ct
import numpy as np
import tflite
from coremltools.converters.mil import Builder as mb
from coremltools.converters.mil.mil import types

ROOT = Path(__file__).resolve().parents[2]
TFLITE = ROOT / "ml/quickdraw/export/quickdraw_cnn.tflite"
GOLDEN_OUTPUTS = ROOT / "ml/quickdraw/export/golden_outputs.json"
GOLDEN_DIR = ROOT / "ml/quickdraw/golden"
CATEGORIES = ROOT / "android/app/src/main/assets/quickdraw/categories.json"
OUT_DIR = ROOT / "ios/HAWE/Resources/QuickDraw"
OUT = OUT_DIR / "QuickDrawCNN.mlpackage"
TOLERANCE = 0.02


def load_graph(path):
    buf = path.read_bytes()
    model = tflite.Model.GetRootAsModel(buf, 0)
    graph = model.Subgraphs(0)

    def tensor(index):
        t = graph.Tensors(index)
        q = t.Quantization()
        data = model.Buffers(t.Buffer()).DataAsNumpy()
        dtype = {tflite.TensorType.INT8: np.int8, tflite.TensorType.INT32: np.int32,
                 tflite.TensorType.FLOAT32: np.float32}.get(t.Type())
        values = None
        if isinstance(data, np.ndarray) and data.size and dtype is not None:
            values = data.view(dtype).reshape(t.ShapeAsNumpy())
        scale = q.ScaleAsNumpy() if q and q.ScaleLength() else None
        zero = q.ZeroPointAsNumpy() if q and q.ZeroPointLength() else None
        return {"values": values, "scale": scale, "zero": zero}

    ops = []
    for k in range(graph.OperatorsLength()):
        op = graph.Operators(k)
        code = model.OperatorCodes(op.OpcodeIndex()).BuiltinCode()
        ops.append((code, [tensor(i) for i in op.InputsAsNumpy() if i >= 0],
                    [tensor(i) for i in op.OutputsAsNumpy()]))
    return ops


def fake_quant(x, q, name):
    """int8 활성값 흉내: clip(round(x / s) + zp, -128, 127) → (q − zp) × s"""
    scale, zero = float(q["scale"][0]), float(q["zero"][0])
    levels = mb.round(x=mb.real_div(x=x, y=np.float32(scale)))
    clipped = mb.clip(x=levels, alpha=np.float32(-128 - zero), beta=np.float32(127 - zero))
    return mb.mul(x=clipped, y=np.float32(scale), name=name)


def build(ops):
    Code = tflite.BuiltinOperator

    @mb.program(input_specs=[mb.TensorSpec(shape=(1, 28, 28, 1), dtype=types.fp32)], opset_version=ct.target.iOS17)
    def program(image):
        x = None
        conv = pool = 0
        for code, inputs, outputs in ops:
            out_q = outputs[0]
            if code == Code.QUANTIZE:
                x = fake_quant(image, out_q, "input_q")
                x = mb.transpose(x=x, perm=[0, 3, 1, 2])  # NHWC → NCHW
            elif code == Code.CONV_2D:
                conv += 1
                w, b = inputs[1], inputs[2]
                weight = w["values"].astype(np.float32) * w["scale"].reshape(-1, 1, 1, 1)  # [O, H, W, I]
                weight = np.transpose(weight, (0, 3, 1, 2)).astype(np.float32)          # [O, I, H, W]
                bias = (b["values"].astype(np.float64) * b["scale"]).astype(np.float32)
                x = mb.conv(x=x, weight=weight, bias=bias, pad_type="same", strides=[1, 1])
                x = mb.relu(x=x)
                x = fake_quant(x, out_q, f"conv{conv}")
            elif code == Code.MAX_POOL_2D:
                pool += 1
                x = mb.max_pool(x=x, kernel_sizes=[2, 2], strides=[2, 2], pad_type="valid", name=f"pool{pool}")
            elif code == Code.MEAN:
                x = mb.reduce_mean(x=x, axes=[2, 3], keep_dims=False)
                x = fake_quant(x, out_q, "gap")
            elif code == Code.FULLY_CONNECTED:
                w, b = inputs[1], inputs[2]
                weight = (w["values"].astype(np.float32) * w["scale"].reshape(-1, 1)).astype(np.float32)
                bias = (b["values"].astype(np.float64) * b["scale"]).astype(np.float32)
                x = mb.linear(x=x, weight=weight, bias=bias)
                x = fake_quant(x, out_q, "logits")
            elif code == Code.SOFTMAX:
                x = mb.softmax(x=x, axis=-1)
                x = fake_quant(x, out_q, "probabilities_q")
            elif code == Code.DEQUANTIZE:
                x = mb.identity(x=x, name="probabilities")
            else:
                raise SystemExit(f"모르는 연산이에요: {code}")
        return x

    return program


def check(model):
    expected = json.loads(GOLDEN_OUTPUTS.read_text())
    worst = 0.0
    for item in expected["items"]:
        golden = json.loads((GOLDEN_DIR / f"{item['name']}.json").read_text())
        image = np.array(golden["expected"], dtype=np.float32).reshape(1, 28, 28, 1)
        probs = model.predict({"image": image})["probabilities"].reshape(-1)
        want = np.array(item["probabilities"], dtype=np.float32)
        diff = float(np.abs(probs - want).max())
        worst = max(worst, diff)
        top = int(np.argmax(probs))
        ok = diff <= TOLERANCE and (item["margin"] < 0.05 or top == item["top3"][0]["index"])
        print(f"{'OK ' if ok else 'BAD'} {item['name']}: 최대 차이 {diff:.4f}, 1등 {top} (기대 {item['top3'][0]['index']})")
        if not ok:
            raise SystemExit("골든과 달라요")
    print(f"골든 {len(expected['items'])}개 통과 (최대 차이 {worst:.4f})")


def main():
    program = build(load_graph(TFLITE))
    model = ct.convert(program, convert_to="mlprogram", minimum_deployment_target=ct.target.iOS17,
                       compute_precision=ct.precision.FLOAT32, compute_units=ct.ComputeUnit.CPU_ONLY)
    model.short_description = "HA'WE 그림 맞히기 (Quick, Draw! 50범주, M1 int8 TFLite 를 옮김)"
    model.input_description["image"] = "PREPROCESS_SPEC.md 대로 만든 28×28, 0~1, 행 우선 [1, 28, 28, 1]"
    model.output_description["probabilities"] = "범주별 확률 [1, 50] — categories.json 의 id 순서"
    if sys.platform == "darwin":
        check(model)
    else:
        print("Mac 이 아니라 골든 확인을 건너뛰어요")
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    if OUT.exists():
        shutil.rmtree(OUT)
    model.save(str(OUT))
    shutil.copyfile(CATEGORIES, OUT_DIR / "quickdraw-categories.json")
    print(f"저장: {OUT.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
