import os
import subprocess
import tempfile
import numpy as np
import soundfile as sf
from scipy.signal import butter, lfilter

SR = 44100
DURATION = 6.0          # seconds
FADE_MS = 40            # boundary crossfade in milliseconds (20–80ms is typical)
MP3_BITRATE = "192k"    # "128k", "192k", "256k", etc.

OUT_DIR = "exported_sounds"
os.makedirs(OUT_DIR, exist_ok=True)

# ----------------------------
# DSP utilities
# ----------------------------

def normalize(x, peak=0.95):
    m = np.max(np.abs(x)) + 1e-12
    return (x / m) * peak

def butter_filter(x, cutoff_hz, btype):
    b, a = butter(4, cutoff_hz / (SR / 2), btype=btype)
    return lfilter(b, a, x)

def lowpass(x, cutoff_hz):
    return butter_filter(x, cutoff_hz, "low")

def highpass(x, cutoff_hz):
    return butter_filter(x, cutoff_hz, "high")

def cycles_locked_sine(freq_hz, amp=1.0, duration=DURATION):
    """
    Adjust frequency so freq * duration is an integer number of cycles.
    Ensures sample-accurate periodic boundary for the sine.
    """
    n_cycles = max(1, int(round(freq_hz * duration)))
    f_adj = n_cycles / duration
    n = int(SR * duration)
    t = np.arange(n) / SR
    return amp * np.sin(2 * np.pi * f_adj * t)

def seamless_noise(duration=DURATION):
    """
    Generate seamless (circular) noise by creating a random spectrum and IFFT.
    The resulting time series is periodic over the buffer length.
    """
    n = int(SR * duration)
    # rfft size n -> n//2 + 1 bins
    mag = np.random.rand(n // 2 + 1)
    phase = np.random.rand(n // 2 + 1) * 2 * np.pi
    # DC and Nyquist should be real for perfect symmetry
    phase[0] = 0.0
    if n % 2 == 0:
        phase[-1] = 0.0
    spectrum = mag * np.exp(1j * phase)
    x = np.fft.irfft(spectrum, n=n).astype(np.float32)
    return x

def equal_power_crossfade_loop(x, fade_ms=FADE_MS):
    """
    Enforce seamless boundary by blending the last fade segment into the first fade segment.
    This removes clicks if there’s any residual mismatch.
    """
    n = len(x)
    fade = int(SR * (fade_ms / 1000.0))
    fade = max(8, min(fade, n // 4))

    head = x[:fade].copy()
    tail = x[-fade:].copy()

    # Equal-power windows
    w = np.linspace(0.0, 1.0, fade, endpoint=False)
    a = np.cos(w * np.pi / 2)  # goes 1 -> 0
    b = np.sin(w * np.pi / 2)  # goes 0 -> 1

    blended = tail * a + head * b
    y = x.copy()
    y[:fade] = blended
    y[-fade:] = blended
    return y

def make_loop_perfect(x):
    x = normalize(x, 0.95)
    x = equal_power_crossfade_loop(x, FADE_MS)
    x = normalize(x, 0.95)
    return x.astype(np.float32)

# ----------------------------
# Export utilities
# ----------------------------

def write_wav(path, x):
    sf.write(path, x, SR, subtype="PCM_16")

def export_mp3_ffmpeg(wav_path, mp3_path, bitrate=MP3_BITRATE):
    """
    Uses FFmpeg’s libmp3lame, which writes gapless metadata that many players honor.
    """
    cmd = [
        "ffmpeg", "-y",
        "-i", wav_path,
        "-codec:a", "libmp3lame",
        "-b:a", bitrate,
        "-joint_stereo", "1",
        "-q:a", "2",
        mp3_path
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def export_ogg_ffmpeg(wav_path, ogg_path, quality="5"):
    """
    OGG is typically the most reliable for seamless loops in games.
    """
    cmd = [
        "ffmpeg", "-y",
        "-i", wav_path,
        "-codec:a", "libvorbis",
        "-q:a", str(quality),
        ogg_path
    ]
    subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)

def export_sound(name, x, mp3=True, ogg_optional=True):
    """
    Writes WAV temp, then MP3 and optionally OGG.
    """
    x = make_loop_perfect(x)

    with tempfile.TemporaryDirectory() as td:
        wav_path = os.path.join(td, f"{name}.wav")
        write_wav(wav_path, x)

        if mp3:
            mp3_path = os.path.join(OUT_DIR, f"{name}.mp3")
            export_mp3_ffmpeg(wav_path, mp3_path)

        if ogg_optional:
            ogg_path = os.path.join(OUT_DIR, f"{name}.ogg")
            export_ogg_ffmpeg(wav_path, ogg_path)

# ----------------------------
# Sound designs (loop-friendly)
# ----------------------------

def forest_whispers():
    x = seamless_noise()
    x = lowpass(x, 1200)
    # slow “wind” amplitude modulation that is also periodic
    mod = 0.65 + 0.35 * cycles_locked_sine(0.10, amp=1.0)
    return x * mod

def crunchy_taps():
    # create a periodic impulse train and shape each impulse (wrap-safe)
    n = int(SR * DURATION)
    x = np.zeros(n, dtype=np.float32)
    step = int(SR * 0.18)  # taps every 180ms
    width = int(SR * 0.008)
    for i in range(0, n, step):
        seg = highpass(seamless_noise(duration=width / SR), 2500)[:width]
        x[i:i+width] += seg
    x = highpass(x, 3000)
    return x

def ocean_waves():
    x = seamless_noise()
    x = lowpass(x, 600)
    swell = 0.4 + 0.6 * (0.5 + 0.5 * cycles_locked_sine(0.08))
    return x * swell

def cat_purr():
    base = cycles_locked_sine(30, 0.9) + cycles_locked_sine(60, 0.35)
    breath = 0.7 + 0.3 * (0.5 + 0.5 * cycles_locked_sine(0.35))
    return base * breath

def cozy_fire():
    x = seamless_noise()
    x = lowpass(x, 2200)
    # add sparse “crackle” bursts
    n = len(x)
    crack = np.zeros(n, dtype=np.float32)
    step = int(SR * 0.12)
    width = int(SR * 0.004)
    for i in range(0, n, step):
        if np.random.rand() > 0.65:
            seg = highpass(seamless_noise(duration=width / SR), 3500)[:width]
            crack[i:i+width] += seg * 0.8
    return x * 0.7 + crack * 0.6

def magic_chimes():
    # layered locked sines with gentle periodic tremolo
    tones = (cycles_locked_sine(880, 0.35) +
             cycles_locked_sine(1320, 0.25) +
             cycles_locked_sine(1760, 0.18))
    trem = 0.6 + 0.4 * (0.5 + 0.5 * cycles_locked_sine(0.25))
    return tones * trem

def page_flips():
    x = highpass(seamless_noise(), 2500)
    mod = (np.random.rand(len(x)) > 0.997).astype(np.float32)
    # smooth the random gate a bit via lowpass to avoid harsh zipper
    mod = lowpass(mod, 40)
    return x * mod * 2.0

def snow_crunch():
    x = highpass(seamless_noise(), 1600)
    x = lowpass(x, 7000)
    mod = 0.5 + 0.5 * (0.5 + 0.5 * cycles_locked_sine(1.2))
    return x * mod

def keyboard_clicks():
    return crunchy_taps()

def ticking_clock():
    n = int(SR * DURATION)
    x = np.zeros(n, dtype=np.float32)
    step = int(SR * 1.0)
    click_len = int(SR * 0.02)
    click = cycles_locked_sine(1500, 1.0, duration=click_len / SR)[:click_len]
    click = highpass(click, 600)
    for i in range(0, n, step):
        x[i:i+click_len] += click * 0.6
    return x

def bubble_wrap():
    n = int(SR * DURATION)
    x = np.zeros(n, dtype=np.float32)
    step = int(SR * 0.13)
    pop_len = int(SR * 0.012)
    for i in range(0, n, step):
        if np.random.rand() > 0.5:
            burst = highpass(seamless_noise(duration=pop_len / SR), 2200)[:pop_len]
            env = np.linspace(1, 0, pop_len, endpoint=False)
            x[i:i+pop_len] += burst * env
    return x

def white_noise():
    return seamless_noise()

def deep_hum():
    return (cycles_locked_sine(50, 0.9) + cycles_locked_sine(100, 0.25)) * (0.7 + 0.3 * (0.5 + 0.5 * cycles_locked_sine(0.12)))

def rainforest():
    x = seamless_noise()
    x = lowpass(x, 3000)
    birds = (cycles_locked_sine(2500, 0.10) * (np.random.rand(len(x)) > 0.9992).astype(np.float32))
    birds = lowpass(birds, 3000)
    rain = lowpass(seamless_noise(), 3500) * 0.5
    return x * 0.35 + rain * 0.55 + birds

def stream_flow():
    x = lowpass(seamless_noise(), 900)
    return x * (0.7 + 0.3 * (0.5 + 0.5 * cycles_locked_sine(0.20)))

def zen_garden():
    x = lowpass(seamless_noise(), 700)
    grit = highpass(seamless_noise(), 2200) * 0.15
    return x * 0.85 + grit

def wind_chimes():
    base = (cycles_locked_sine(660, 0.28) +
            cycles_locked_sine(990, 0.20) +
            cycles_locked_sine(1320, 0.16))
    sway = 0.6 + 0.4 * (0.5 + 0.5 * cycles_locked_sine(0.18))
    return base * sway

def vinyl_static():
    x = highpass(seamless_noise(), 4000)
    x = lowpass(x, 12000)
    return x

def bowl_sing():
    # stable resonance: add a few harmonics, all cycle-locked
    x = (cycles_locked_sine(220, 0.7) +
         cycles_locked_sine(440, 0.18) +
         cycles_locked_sine(660, 0.10))
    return x * (0.85 + 0.15 * (0.5 + 0.5 * cycles_locked_sine(0.07)))

def rain_on_tin():
    x = highpass(seamless_noise(), 2200)
    # “raindrop” pings
    n = len(x)
    p = (np.random.rand(n) > 0.9995).astype(np.float32)
    p = lowpass(p, 90)
    return x * 0.7 + p * 0.6

def library_ambience():
    x = lowpass(seamless_noise(), 900) * 0.5
    air = lowpass(seamless_noise(), 2500) * 0.25
    return x + air

def coffee_shop():
    murmur = lowpass(seamless_noise(), 2000) * 0.6
    clink_gate = (np.random.rand(len(murmur)) > 0.9994).astype(np.float32)
    clinks = highpass(seamless_noise(), 2500) * lowpass(clink_gate, 50) * 1.2
    return murmur + clinks

def crickets():
    # periodic chirp oscillator with gated bursts
    carrier = cycles_locked_sine(4200, 0.25)
    gate = (cycles_locked_sine(2.8) > 0.85).astype(np.float32)
    gate = lowpass(gate, 40)
    return carrier * gate

def space_drone():
    x = cycles_locked_sine(22, 0.8) + cycles_locked_sine(44, 0.35)
    slow = 0.65 + 0.35 * (0.5 + 0.5 * cycles_locked_sine(0.05))
    return x * slow

def submarine():
    hum = cycles_locked_sine(28, 0.7) + cycles_locked_sine(56, 0.22)
    ping_gate = (np.random.rand(int(SR * DURATION)) > 0.9992).astype(np.float32)
    ping = cycles_locked_sine(900, 0.25) * lowpass(ping_gate, 30)
    return hum + ping

def train_tracks():
    # loop-safe rhythm
    n = int(SR * DURATION)
    x = np.zeros(n, dtype=np.float32)
    step = int(SR * 0.24)
    clack_len = int(SR * 0.02)
    clack = highpass(seamless_noise(duration=clack_len / SR), 1200)[:clack_len]
    env = np.linspace(1, 0, clack_len, endpoint=False)
    for i in range(0, n, step):
        x[i:i+clack_len] += clack * env * 1.2
    bed = lowpass(seamless_noise(), 500) * 0.25
    return x + bed

def thunder():
    x = lowpass(seamless_noise(), 180) * 1.2
    rumble = cycles_locked_sine(18, 0.35)
    return x + rumble

def grass_rustle():
    x = highpass(seamless_noise(), 1200)
    x = lowpass(x, 8000)
    return x * (0.6 + 0.4 * (0.5 + 0.5 * cycles_locked_sine(0.9)))

def sand_pour():
    x = lowpass(seamless_noise(), 1200)
    grit = highpass(seamless_noise(), 2500) * 0.25
    return x * 0.75 + grit

def plastic_crinkle():
    x = highpass(seamless_noise(), 3000)
    gate = (np.random.rand(len(x)) > 0.9988).astype(np.float32)
    gate = lowpass(gate, 70)
    return x * gate * 2.0

def soap_carving():
    x = highpass(seamless_noise(), 2000)
    x = lowpass(x, 9000)
    return x * (0.7 + 0.3 * (0.5 + 0.5 * cycles_locked_sine(1.1)))

def pencil_sketch():
    x = highpass(seamless_noise(), 1800)
    x = lowpass(x, 7000)
    return x * 0.8

def ice_clink():
    n = int(SR * DURATION)
    x = np.zeros(n, dtype=np.float32)
    step = int(SR * 0.9)
    hit_len = int(SR * 0.06)
    ring = cycles_locked_sine(2200, 0.35, duration=hit_len / SR)[:hit_len]
    env = np.exp(-np.linspace(0, 5, hit_len, endpoint=False))
    for i in range(0, n, step):
        x[i:i+hit_len] += ring * env
    return x

def fan_whir():
    hum = cycles_locked_sine(120, 0.75)
    air = lowpass(seamless_noise(), 900) * 0.18
    return hum + air

def heart_beat():
    n = int(SR * DURATION)
    x = np.zeros(n, dtype=np.float32)
    # two-beat pattern per second
    pattern = [0.0, 0.20]  # seconds offset within 1s
    beat_len = int(SR * 0.09)
    for sec in range(int(DURATION)):
        for off in pattern:
            i = int((sec + off) * SR)
            if i + beat_len <= n:
                thump = cycles_locked_sine(70, 0.8, duration=beat_len / SR)[:beat_len]
                env = np.exp(-np.linspace(0, 6, beat_len, endpoint=False))
                x[i:i+beat_len] += thump * env
    return lowpass(x, 500)

def boiling_water():
    x = lowpass(seamless_noise(), 1600)
    bubbles = (np.random.rand(len(x)) > 0.9992).astype(np.float32)
    bubbles = lowpass(bubbles, 80)
    fizz = highpass(seamless_noise(), 2500) * bubbles * 0.9
    return x * 0.65 + fizz

def windy_canyon():
    x = lowpass(seamless_noise(), 500)
    gust = 0.5 + 0.5 * (0.5 + 0.5 * cycles_locked_sine(0.07))
    return x * (0.4 + 0.6 * gust)

def scissor_snip():
    n = int(SR * DURATION)
    x = np.zeros(n, dtype=np.float32)
    step = int(SR * 0.7)
    snip_len = int(SR * 0.03)
    for i in range(0, n, step):
        snip = highpass(seamless_noise(duration=snip_len / SR), 3200)[:snip_len]
        env = np.linspace(1, 0, snip_len, endpoint=False)
        x[i:i+snip_len] += snip * env * 1.3
    return x

def brush_strokes():
    x = lowpass(seamless_noise(), 900)
    texture = highpass(seamless_noise(), 2000) * 0.12
    return x + texture

def bee_buzz():
    # add a little harmonic + slow drift
    x = cycles_locked_sine(220, 0.85) + cycles_locked_sine(440, 0.15)
    drift = 0.8 + 0.2 * (0.5 + 0.5 * cycles_locked_sine(0.3))
    return x * drift

def frogs():
    carrier = cycles_locked_sine(300, 0.35)
    gate = (np.random.rand(int(SR * DURATION)) > 0.9985).astype(np.float32)
    gate = lowpass(gate, 25)
    return carrier * gate

def dripping_tap():
    n = int(SR * DURATION)
    x = np.zeros(n, dtype=np.float32)
    step = int(SR * 1.1)
    drip_len = int(SR * 0.08)
    drip = cycles_locked_sine(1200, 0.45, duration=drip_len / SR)[:drip_len]
    env = np.exp(-np.linspace(0, 6, drip_len, endpoint=False))
    for i in range(0, n, step):
        x[i:i+drip_len] += drip * env
    return x

def paper_rip():
    x = highpass(seamless_noise(), 2500)
    gate = (np.random.rand(len(x)) > 0.999).astype(np.float32)
    gate = lowpass(gate, 35)
    return x * gate * 2.0

def wooden_blocks():
    n = int(SR * DURATION)
    x = np.zeros(n, dtype=np.float32)
    step = int(SR * 0.5)
    hit_len = int(SR * 0.05)
    hit = cycles_locked_sine(650, 0.5, duration=hit_len / SR)[:hit_len]
    env = np.exp(-np.linspace(0, 7, hit_len, endpoint=False))
    for i in range(0, n, step):
        x[i:i+hit_len] += hit * env
    return x

def clock_tower():
    # distant bell: low sine + mild modulation
    x = cycles_locked_sine(200, 0.7) + cycles_locked_sine(400, 0.18)
    return x * (0.8 + 0.2 * (0.5 + 0.5 * cycles_locked_sine(0.06)))

def dry_leaves():
    x = highpass(seamless_noise(), 1000)
    gate = (np.random.rand(len(x)) > 0.9986).astype(np.float32)
    gate = lowpass(gate, 60)
    return x * gate * 1.8

def marble_roll():
    # smooth rolling tone + subtle noise bed
    tone = cycles_locked_sine(820, 0.25)
    bed = lowpass(seamless_noise(), 900) * 0.12
    return tone + bed

def whale_song():
    x = cycles_locked_sine(15, 0.85) + cycles_locked_sine(25, 0.35)
    swell = 0.6 + 0.4 * (0.5 + 0.5 * cycles_locked_sine(0.03))
    return x * swell

def supernova():
    # cinematic: noise + sub drone + shimmer, all loop-safe
    n = seamless_noise()
    n = lowpass(n, 6000) * 0.6
    sub = cycles_locked_sine(28, 0.45)
    shimmer = highpass(seamless_noise(), 6000) * 0.12
    return n + sub + shimmer

# ----------------------------
# Mapping & batch export
# ----------------------------

SOUND_MAP = {
    "sound_002": forest_whispers,
    "sound_003": crunchy_taps,
    "sound_004": ocean_waves,
    "sound_005": cat_purr,
    "sound_006": cozy_fire,
    "sound_007": magic_chimes,
    "sound_008": page_flips,
    "sound_009": snow_crunch,
    "sound_010": keyboard_clicks,
    "sound_011": ticking_clock,
    "sound_012": bubble_wrap,
    "sound_013": white_noise,
    "sound_014": deep_hum,
    "sound_015": rainforest,
    "sound_016": stream_flow,
    "sound_017": zen_garden,
    "sound_018": wind_chimes,
    "sound_019": vinyl_static,
    "sound_020": bowl_sing,
    "sound_021": rain_on_tin,
    "sound_022": library_ambience,
    "sound_023": coffee_shop,
    "sound_024": crickets,
    "sound_025": space_drone,
    "sound_026": submarine,
    "sound_027": train_tracks,
    "sound_028": thunder,
    "sound_029": grass_rustle,
    "sound_030": sand_pour,
    "sound_031": plastic_crinkle,
    "sound_032": soap_carving,
    "sound_033": pencil_sketch,
    "sound_034": ice_clink,
    "sound_035": fan_whir,
    "sound_036": heart_beat,
    "sound_037": boiling_water,
    "sound_038": windy_canyon,
    "sound_039": scissor_snip,
    "sound_040": brush_strokes,
    "sound_041": bee_buzz,
    "sound_042": frogs,
    "sound_043": dripping_tap,
    "sound_044": paper_rip,
    "sound_045": wooden_blocks,
    "sound_046": clock_tower,
    "sound_047": dry_leaves,
    "sound_048": marble_roll,
    "sound_049": whale_song,
    "sound_050": supernova,
}

def main():
    # Quick check that ffmpeg exists
    try:
        subprocess.run(["ffmpeg", "-version"], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    except Exception:
        raise RuntimeError("FFmpeg not found on PATH. Install FFmpeg or add it to PATH to export MP3/OGG.")

    for name, fn in SOUND_MAP.items():
        x = fn()
        export_sound(name, x, mp3=True, ogg_optional=True)

    print(f"Done. Exported to: {OUT_DIR}/ (MP3 + OGG)")

if __name__ == "__main__":
    main()
