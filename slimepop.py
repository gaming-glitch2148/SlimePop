import os
import math
import random
import wave
import struct
from pathlib import Path

SR = 44100  # sample rate
OUT_DIR = Path("C:/Users/khann/AndroidStudioProjects/SlimePop/app/src/main/res/raw")
OUT_DIR.mkdir(parents=True, exist_ok=True)

def write_wav(path: Path, samples, sr=SR):
    # samples: float [-1, 1]
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)  # 16-bit
        w.setframerate(sr)
        frames = bytearray()
        for s in samples:
            s = max(-1.0, min(1.0, s))
            frames += struct.pack("<h", int(s * 32767))
        w.writeframes(frames)

def env_perc(n, attack=0.005, decay=0.12):
    # simple percussive envelope
    env = [0.0] * n
    a = max(1, int(attack * SR))
    d = max(1, int(decay * SR))
    for i in range(n):
        if i < a:
            env[i] = i / a
        else:
            t = (i - a) / d
            env[i] = math.exp(-4.0 * t)
    return env

def lowpass_onepole(x, cutoff_hz):
    # one-pole lowpass
    if cutoff_hz <= 0:
        return x
    rc = 1.0 / (2 * math.pi * cutoff_hz)
    dt = 1.0 / SR
    alpha = dt / (rc + dt)
    y = 0.0
    out = []
    for s in x:
        y = y + alpha * (s - y)
        out.append(y)
    return out

def band_limited_noise(n):
    return [random.uniform(-1, 1) for _ in range(n)]

def make_pop_wav():
    # "squishy pop": short noise burst + downward sine sweep
    dur = 0.18
    n = int(dur * SR)
    env = env_perc(n, attack=0.002, decay=0.16)
    noise = band_limited_noise(n)
    noise = lowpass_onepole(noise, 1800)  # soften
    out = []
    phase = 0.0
    for i in range(n):
        t = i / SR
        # sweep 220->90Hz
        f = 220 * ((90/220) ** (t / dur))
        phase += 2 * math.pi * f / SR
        sine = math.sin(phase) * 0.6
        s = (0.55 * noise[i] + 0.45 * sine) * env[i]
        out.append(s * 0.9)
    # tiny click at start for crispness
    if n > 2:
        out[0] += 0.35
        out[1] -= 0.20
    write_wav(OUT_DIR / "pop.wav", out)

def make_loop_base(kind, seconds=12):
    n = int(seconds * SR)

    if kind == "white":
        x = band_limited_noise(n)
        return [s * 0.08 for s in x]

    if kind == "pink":
        # crude pink-ish by filtering white
        x = band_limited_noise(n)
        x = lowpass_onepole(x, 1200)
        return [s * 0.12 for s in x]

    if kind == "brown":
        # integrated noise (brown-ish)
        x = band_limited_noise(n)
        y = 0.0
        out = []
        for s in x:
            y = 0.98 * y + 0.02 * s
            out.append(y * 0.22)
        return out

    if kind == "wind":
        x = band_limited_noise(n)
        x = lowpass_onepole(x, 350)
        # slow amplitude flutter
        out = []
        for i, s in enumerate(x):
            t = i / SR
            amp = 0.5 + 0.5 * math.sin(2*math.pi*0.08*t + 1.1)  # 0.08 Hz
            out.append(s * (0.05 + 0.12 * amp))
        return out

    if kind == "drone":
        # soft pad (two sines + subtle noise)
        out = []
        p1 = p2 = 0.0
        f1 = random.choice([110, 130, 146, 164])
        f2 = f1 * 1.5
        noise = lowpass_onepole(band_limited_noise(n), 900)
        for i in range(n):
            p1 += 2*math.pi*f1/SR
            p2 += 2*math.pi*f2/SR
            s = 0.06*math.sin(p1) + 0.04*math.sin(p2) + 0.02*noise[i]
            # gentle LFO
            t = i / SR
            lfo = 0.85 + 0.15*math.sin(2*math.pi*0.05*t)
            out.append(s * lfo)
        return out

    if kind == "chimes":
        # sparse bell hits
        out = [0.0]*n
        hit_times = sorted(random.sample(range(int(0.5*SR), n-int(0.5*SR)), 10))
        for ht in hit_times:
            dur = int(0.8*SR)
            env = env_perc(dur, attack=0.002, decay=0.75)
            f = random.choice([523.25, 659.25, 783.99, 987.77])  # C5/E5/G5/B5-ish
            phase = 0.0
            for j in range(dur):
                i = ht + j
                if i >= n:
                    break
                phase += 2*math.pi*f/SR
                s = math.sin(phase) + 0.35*math.sin(2*phase) + 0.20*math.sin(3*phase)
                out[i] += 0.06 * s * env[j]
        # add a quiet bed
        bed = lowpass_onepole(band_limited_noise(n), 1400)
        for i in range(n):
            out[i] += 0.01*bed[i]
        return out

    # fallback
    x = band_limited_noise(n)
    return [s * 0.06 for s in x]

def make_50_soundpacks():
    # Curate 50 loops by cycling through “kinds”
    kinds = [
        "white","pink","brown","wind","drone","chimes",
        "pink","wind","drone","white",
    ]
    # Expand to 50
    kinds = (kinds * 10)[:50]

    for idx in range(1, 51):
        kind = kinds[idx-1]
        wav = make_loop_base(kind, seconds=12)
        name = f"soundpack_{idx:03d}.wav"
        write_wav(OUT_DIR / name, wav)

def main():
    random.seed(7)
    make_pop_wav()
    make_50_soundpacks()
    print("WAV assets generated in:", OUT_DIR)

if __name__ == "__main__":
    main()
