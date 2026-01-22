import math
import random
import wave
import struct
from pathlib import Path

SR = 44100
OUT_DIR = Path("app/src/main/res/raw")
OUT_DIR.mkdir(parents=True, exist_ok=True)

# ---------- helpers ----------

def clamp(x):
    return -1.0 if x < -1.0 else 1.0 if x > 1.0 else x

def write_wav(path, samples):
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        frames = bytearray()
        for s in samples:
            frames += struct.pack("<h", int(clamp(s) * 32767))
        w.writeframes(frames)

def fade(x, secs=0.1):
    n = len(x)
    f = int(secs * SR)
    for i in range(f):
        x[i] *= i / f
        x[n - 1 - i] *= i / f
    return x

def env_perc(n, a=0.002, d=0.4):
    A = max(1, int(a * SR))
    D = max(1, int(d * SR))
    e = [0.0]*n
    for i in range(n):
        if i < A:
            e[i] = i / A
        else:
            e[i] = math.exp(-(i-A)/D)
    return e

def sine(freq, n, amp=1.0):
    out = []
    ph = 0.0
    step = 2 * math.pi * freq / SR
    for _ in range(n):
        ph += step
        out.append(math.sin(ph) * amp)
    return out

# ---------- textures (NO NOISE) ----------

def rain_taps(seconds=12):
    n = int(seconds * SR)
    out = [0.0]*n
    drops = int(seconds * 14)

    for _ in range(drops):
        t0 = random.randint(0, n-1)
        dur = int(0.05 * SR)
        env = env_perc(dur, 0.001, 0.08)
        f = random.uniform(1800, 4200)
        tone = sine(f, dur, 0.18)
        for j in range(dur):
            i = t0 + j
            if i < n:
                out[i] += tone[j] * env[j]

    return fade(out, 0.15)

def ocean_waves(seconds=12):
    n = int(seconds * SR)
    out = [0.0]*n
    base = random.choice([90, 110, 130])

    for i in range(n):
        t = i / SR
        swell = 0.5 + 0.5 * math.sin(2*math.pi*0.07*t)
        out[i] = 0.25 * math.sin(2*math.pi*base*t) * swell

    return fade(out, 0.2)

def singing_bowl(seconds=12):
    n = int(seconds * SR)
    out = [0.0]*n
    f0 = random.choice([220, 246.94, 261.63])

    strikes = 4
    for _ in range(strikes):
        t0 = random.randint(0, n-1)
        dur = int(3.5 * SR)
        env = env_perc(dur, 0.003, 2.8)

        for j in range(dur):
            i = t0 + j
            if i < n:
                s = (
                    0.6*math.sin(2*math.pi*f0*j/SR) +
                    0.3*math.sin(2*math.pi*f0*2*j/SR) +
                    0.1*math.sin(2*math.pi*f0*3*j/SR)
                )
                out[i] += s * env[j] * 0.18

    return fade(out, 0.25)

def crystal_chimes(seconds=12):
    n = int(seconds * SR)
    out = [0.0]*n
    hits = int(seconds * 6)

    for _ in range(hits):
        t0 = random.randint(0, n-1)
        dur = int(1.2 * SR)
        env = env_perc(dur, 0.002, 1.2)
        f = random.choice([523.25, 659.25, 783.99])

        tone = sine(f, dur, 0.25)
        for j in range(dur):
            i = t0 + j
            if i < n:
                out[i] += tone[j] * env[j]

    return fade(out, 0.2)

def soft_drips(seconds=12):
    n = int(seconds * SR)
    out = [0.0]*n
    drips = int(seconds * 3)

    for _ in range(drips):
        t0 = random.randint(0, n-1)
        dur = int(0.4 * SR)
        env = env_perc(dur, 0.002, 0.35)
        f = random.choice([320, 420, 520])

        tone = sine(f, dur, 0.22)
        for j in range(dur):
            i = t0 + j
            if i < n:
                out[i] += tone[j] * env[j]

    return fade(out, 0.2)

def harmonic_pad(seconds=12):
    n = int(seconds * SR)
    out = [0.0]*n
    f = random.choice([110, 130.81, 146.83])

    for i in range(n):
        t = i / SR
        lfo = 0.7 + 0.3 * math.sin(2*math.pi*0.04*t)
        out[i] = lfo * (
            0.25*math.sin(2*math.pi*f*t) +
            0.15*math.sin(2*math.pi*f*1.5*t)
        )

    return fade(out, 0.25)

# ---------- pop ----------

def pop_sound():
    dur = 0.16
    n = int(dur * SR)
    env = env_perc(n, 0.001, 0.14)
    out = []

    for i in range(n):
        t = i / SR
        f = 240 * ((90/240) ** (t / dur))
        out.append(math.sin(2*math.pi*f*t) * env[i] * 0.9)

    out[0] += 0.4
    return out

# ---------- build ----------

def build():
    make = []
    make += [rain_taps]*10
    make += [ocean_waves]*8
    make += [singing_bowl]*8
    make += [crystal_chimes]*8
    make += [soft_drips]*8
    make += [harmonic_pad]*8
    make = make[:50]
    random.shuffle(make)

    for i in range(1, 51):
        wav = make[i-1](seconds=random.choice([10,12,14]))
        write_wav(OUT_DIR / f"soundpack_{i:03d}.wav", wav)

    write_wav(OUT_DIR / "pop.wav", pop_sound())

if __name__ == "__main__":
    random.seed(2026)
    build()
    print("Generated NO-NOISE ASMR library")
