"""
generate_pop_sound_option2.py

Option 2 (snappy bubble pop):
- No white/pink/brown noise
- Fast pitch fall with damped resonance
- Harmonics for "bubble" character
- Gentle saturation for punch without harshness

Output:
  pop.wav  (44.1 kHz, mono, 16-bit PCM)
"""

import math
import wave
import struct
from pathlib import Path

SAMPLE_RATE = 44100
OUTPUT_FILE = Path("app/src/main/res/raw/pop.wav")


def clamp(x: float) -> float:
    return -1.0 if x < -1.0 else 1.0 if x > 1.0 else x


def write_wav(path: Path, samples):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)  # 16-bit PCM
        w.setframerate(SAMPLE_RATE)
        frames = bytearray()
        for s in samples:
            frames += struct.pack("<h", int(clamp(s) * 32767))
        w.writeframes(frames)


def env_perc(n: int, attack: float = 0.001, decay: float = 0.08):
    A = max(1, int(attack * SAMPLE_RATE))
    D = max(1, int(decay * SAMPLE_RATE))
    env = [0.0] * n
    for i in range(n):
        if i < A:
            env[i] = i / A
        else:
            env[i] = math.exp(-(i - A) / D)
    return env


def fade(samples, seconds: float = 0.01):
    n = len(samples)
    f = int(seconds * SAMPLE_RATE)
    f = max(1, min(f, n // 2))
    for i in range(f):
        g = i / f
        samples[i] *= g
        samples[n - 1 - i] *= g
    return samples


def generate_pop_option2():
    duration = 0.14  # snappier/shorter than option A
    n = int(duration * SAMPLE_RATE)

    env = env_perc(n, attack=0.001, decay=0.08)

    f_start = 420.0
    f_end = 140.0

    samples = [0.0] * n

    for i in range(n):
        t_norm = i / (n - 1)
        t = i / SAMPLE_RATE

        # Faster fall than option A
        f = f_start * ((f_end / f_start) ** (t_norm * 1.8))

        # Add harmonics to feel like a "bubble" snap (still tonal, no noise)
        s = (
            1.00 * math.sin(2 * math.pi * f * t) +
            0.45 * math.sin(2 * math.pi * (2.1 * f) * t) +
            0.20 * math.sin(2 * math.pi * (3.0 * f) * t)
        )

        samples[i] = 0.55 * s * env[i]

    # Gentle saturation
    for i in range(n):
        samples[i] = math.tanh(1.8 * samples[i])

    # Micro transient (tiny click)
    if n > 3:
        samples[0] += 0.18
        samples[1] -= 0.08

    return fade(samples, 0.01)


if __name__ == "__main__":
    pop = generate_pop_option2()
    write_wav(OUTPUT_FILE, pop)
    print(f"Generated {OUTPUT_FILE.resolve()}")
