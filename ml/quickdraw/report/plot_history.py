"""REPORT.md 의 학습 곡선 그림. 정확도·손실을 두 칸으로(축 하나씩).

사용: python report/plot_history.py runs/full/history.csv report/learning_curve.png
matplotlib 이 필요하다(학습에는 필요 없어서 requirements.txt 에는 없음): pip install "matplotlib==3.10.*"
"""
import csv
import sys
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager

history, out = Path(sys.argv[1]), Path(sys.argv[2])
rows = list(csv.DictReader(history.open(encoding="utf-8")))
epoch = [int(r["epoch"]) + 1 for r in rows]
series = {key: [float(r[key]) for r in rows] for key in ("accuracy", "val_accuracy", "loss", "val_loss")}

SURFACE, INK, INK2, GRID = "#fcfcfb", "#0b0b0b", "#52514e", "#e4e3df"
TRAIN, VAL = "#2a78d6", "#eb6834"  # 기본 팔레트 1·2번(검증 통과)
for name in ("Malgun Gothic", "맑은 고딕"):
    if any(f.name == name for f in font_manager.fontManager.ttflist):
        plt.rcParams["font.family"] = name
        break
plt.rcParams.update({"axes.unicode_minus": False, "font.size": 10, "text.color": INK,
                     "axes.labelcolor": INK2, "xtick.color": INK2, "ytick.color": INK2})

fig, axes = plt.subplots(1, 2, figsize=(10, 3.8), dpi=150, facecolor=SURFACE)
panels = [
    (axes[0], "정확도(top-1)", "accuracy", "val_accuracy", lambda v: f"{v * 100:.1f}%"),
    (axes[1], "손실(loss)", "loss", "val_loss", lambda v: f"{v:.2f}"),
]
for ax, title, train_key, val_key, fmt in panels:
    ax.set_facecolor(SURFACE)
    for spine in ("top", "right"):
        ax.spines[spine].set_visible(False)
    for spine in ("left", "bottom"):
        ax.spines[spine].set_color(GRID)
    ax.grid(axis="y", color=GRID, linewidth=0.8)
    ax.set_axisbelow(True)
    ax.plot(epoch, series[train_key], color=TRAIN, linewidth=2, label="학습(증강한 그림)")
    ax.plot(epoch, series[val_key], color=VAL, linewidth=2, label="검증(고정 설정 그림)")
    # 끝점에만 값 붙이기(모든 점에 숫자 X), 글자는 잉크색
    for key, color in ((train_key, TRAIN), (val_key, VAL)):
        ax.plot(epoch[-1], series[key][-1], "o", color=color, markersize=6, markeredgecolor=SURFACE, markeredgewidth=1.5)
        ax.annotate(fmt(series[key][-1]), (epoch[-1], series[key][-1]), xytext=(6, 0), textcoords="offset points",
                    va="center", fontsize=9, color=INK)
    ax.set_title(title, loc="left", fontsize=11, color=INK, pad=10)
    ax.set_xlabel("epoch")
    ax.set_xlim(1, epoch[-1] + 3)
    ax.tick_params(length=0)
axes[0].yaxis.set_major_formatter(matplotlib.ticker.FuncFormatter(lambda v, _: f"{v * 100:.0f}%"))
axes[0].legend(loc="lower right", frameon=False, fontsize=9)
fig.suptitle("본 학습 30 epoch (Colab T4, 학습 120만 · 검증 15만 장)", x=0.01, ha="left", fontsize=12, color=INK)
fig.tight_layout()
out.parent.mkdir(parents=True, exist_ok=True)
fig.savefig(out, facecolor=SURFACE)
print("saved", out)
