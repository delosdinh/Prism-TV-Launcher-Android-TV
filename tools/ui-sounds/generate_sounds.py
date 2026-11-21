"""Render the fifteen Prism TV interface cues using Python 3 and NumPy."""

from pathlib import Path
import sys
import wave

import numpy as np


RATE = 48000
TAU = 2 * np.pi


def timeline(seconds):
    return np.arange(round(RATE * seconds), dtype=np.float64) / RATE


def smooth(value):
    u = np.clip(value, 0, 1)
    return u * u * (3 - 2 * u)


def release(t, length):
    return smooth((t[-1] - t) / length)


def centre(signal):
    return np.column_stack((signal, signal))


def place(mix, time, signal, gain=1.0, pan=0.0):
    start = round(RATE * time)
    count = min(len(signal), len(mix) - start)
    if count <= 0:
        return
    if signal.ndim == 1:
        signal = centre(signal)
    balance = np.sqrt([1 - pan, 1 + pan])
    mix[start:start + count] += signal[:count] * gain * balance


def silence(seconds):
    return np.zeros((round(seconds * RATE), 2))


def grain(t, frequency, spread, seed):
    # Broad frequency-domain noise band. No recorded impulse or sample.
    rng = np.random.Generator(np.random.PCG64(seed))
    bins = np.fft.rfftfreq(len(t), 1 / RATE)
    distance = np.log2(np.maximum(bins, 1) / frequency)
    shape = np.exp(-0.5 * (distance / spread) ** 2)
    spectrum = np.fft.rfft(rng.standard_normal(len(t))) * shape
    spectrum[0] = 0
    noise = np.fft.irfft(spectrum, n=len(t))
    return noise / np.sqrt(np.mean(noise * noise))


def touch(duration, frequency, rise, decay, bend, colour, edge=0.0):
    # A compliant surface with a soft pitch inflection and two short resonances.
    t = timeline(duration)
    hinge = min(decay * 0.75, 0.028)
    phase = TAU * (frequency * t + bend * hinge * (1 - np.exp(-t / hinge)))
    envelope = (1 - np.exp(-t / rise)) ** 2 * np.exp(-t / decay)
    body = np.sin(phase)
    body += colour * np.sin(1.86 * phase + 0.19) * np.exp(-t / (0.52 * decay))
    body += colour * 0.32 * np.sin(3.12 * phase - 0.55) * np.exp(-t / (0.34 * decay))
    attack = edge * grain(t, 1450, 0.51, 3109) * np.exp(-t / 0.006)
    return (body * envelope + attack * smooth(t / 0.002)) * release(t, 0.012)


def shutter():
    mix = silence(0.37)
    t = timeline(0.12)
    snap = grain(t, 2700, 0.88, 41) * np.exp(-t / 0.007) * smooth(t / 0.0014)
    place(mix, 0, snap * release(t, 0.02), 0.23)
    place(mix, 0.003, touch(0.18, 286, 0.003, 0.034, 47, 0.23), 0.85, -0.04)
    place(mix, 0.142, touch(0.19, 349, 0.005, 0.033, -37, 0.17), 0.52, 0.08)
    return mix


def contour(t, knots):
    """Amplitude envelope specified by time in seconds and level in dB."""
    times, db = np.asarray(knots).T
    env = 10 ** (np.interp(t, times, db) / 20)
    return env * smooth(t / min(0.0015, t[-1] / 20)) * release(t, min(0.009, t[-1] / 6))


def phase(t, hz, bend=0, time=.06, offset=0):
    return TAU * (hz * t + bend * time * (1 - np.exp(-t / time))) + offset


def delayed(y, seconds):
    shifted = np.zeros_like(y)
    n = round(RATE * seconds)
    if n and n < len(y):
        shifted[n:] = y[:-n]
    return shifted


def soft_focus(hz, peak, end, tail, length, reflection):
    t = timeline(length)
    env = contour(t, [(0,-65), (.007,-30), (peak*.49,-13), (peak*.79,-6),
                      (peak,0), (peak+.004,-.4), (end,-16), (tail,-31), (length,-90)])
    phi = phase(t,hz,4.1,.016)
    body = env * (np.sin(phi) + .025 * np.sin(2*phi+.22))
    wet = .69 * delayed(body,.0281) + .35 * delayed(body,.0546)
    side = reflection * (wet - .47 * delayed(body,.0159))
    return np.column_stack((body + side + .059*wet, body - side + .059*wet))


def key_focus():
    t=timeline(.074)
    e=contour(t,[(0,-56),(.005,-26),(.010,-9),(.018,0),(.023,-1.7),
                 (.033,-11),(.045,-27),(.053,-50),(.074,-88)])
    phi=phase(t,331,3,.013)
    return centre(e*(np.sin(phi)+.12*np.sin(2*phi+.4)*np.exp(-t/.023)))


def room_cloud(dry, side, width):
    # The direct signal stays common to the speakers. Staggered arrivals and
    # a separately phased voice add width that survives a mono fold-down.
    mid = dry + .11*delayed(dry,.039) + .08*delayed(dry,.097)
    sides = width * (side + .26*delayed(side,.023) - .18*delayed(side,.087))
    output = np.column_stack((mid+sides,mid-sides))
    fade_frames = min(round(.016 * RATE), len(dry))
    output[-fade_frames:] *= smooth(np.linspace(1, 0, fade_frames))[:,None]
    return output


def select_navigation():
    length = 1.046
    t = timeline(length)
    crest = .179
    attack = [(0,-61),(.022,-22.5),(.068,-11.8),(.116,-6.6),(.156,-1.6)]
    tail = [(crest,0),(crest+.077,-.7),(.35,-3.8),(.46,-10.7),(.59,-16.7),(.78,-28),(.96,-42),(length,-73)]
    env = contour(t, attack+tail)
    bloom = smooth((t-.069)/.191)
    settle = smooth((t-.263)/.28)
    voices = [
        (205.4,3.4,.25,.75+.30*bloom,-.05-.12*bloom,.25+.92*bloom),
        (237.7,3.9,1.54,1.05-.45*bloom-.05*settle,.025+.13*bloom-.22*settle,-.29-.57*bloom),
        (360.8,-2.8,.74,.54+.25*bloom-.13*settle,.10-.08*settle,.12+.25*bloom),
        (302.8,-3.3,2.62,.10*(1-.54*settle),.10,-.60)
    ]
    direct = silence(length)
    for hz, bend, initial, gain, pan, spread in voices:
        oscillator = phase(t,hz,bend,.10,initial)
        drift = .065*np.sin(TAU*1.19*t+initial)
        for ch, position in enumerate((-1,1)):
            angle = oscillator + position*(.55*spread+drift)
            weight = np.sqrt(1-position*pan)
            tone = np.sin(angle) + .020*np.sin(2*angle+.52)*(1-.54*bloom)
            direct[:,ch] += env*gain*weight*tone
    stereo = direct.copy()
    for ch in (0,1):
        stereo[:,ch] += .13*delayed(direct[:,1-ch], (.025,.032)[ch])
        stereo[:,ch] += .09*delayed(direct[:,ch], (.067,.075)[ch])
    stereo *= release(t,.021)[:,None]*np.array([.964,1.036])
    return stereo


def back_navigation():
    t = timeline(1.068)
    env = contour(t, [(0,-60),(.026,-26),(.073,-16.7),(.126,-9.1),(.186,-2.1),
                      (.233,-.15),(.283,0),(.316,-.50),(.358,-3.3),(.438,-8.85),
                      (.548,-14.7),(.692,-23.7),(.849,-33.8),(.989,-43.0),(1.068,-56)])
    # The chord begins near the centre. Its low notes open in opposite
    # directions and ease partway home as the upper resonance dies away.
    opening = smooth((t-.080)/.240)
    closing = smooth((t-.317)/.223)
    late = smooth((t-.54)/.43)
    width = opening*(1-.36*closing)*(1-.64*late)
    root_seat = .035 - .86*width
    inner_seat = .125 + .70*width
    upper_seat = -.06 - .60*smooth((t-.047)/.141)*(1-.85*smooth((t-.205)/.213))
    grow = smooth((t-.13)/.17)
    dim = smooth((t-.375)/.27)
    voices = [
        (181.8,1.9,.41, .84+.055*grow+.18*dim, root_seat, .13+1.28*opening-.28*closing-.41*late),
        (214.9,1.25,2.06, 1.04-.20*dim, inner_seat, .065-.29*opening+.12*closing+.57*late),
        (322.9,.78,-.88, .51-.045*grow+.16*dim, upper_seat,
         .13+.34*smooth((t-.04)/.14)+2.48*smooth((t-.173)/.123)+1.83*closing+.45*late),
        (271.2,-1.1,1.36, .15-.09*grow-.025*dim, .13+.16*opening, -.20+.83*closing)
    ]
    direct = np.zeros((len(t),2))
    for hz,bend,initial,level,seat,spread in voices:
        carrier = phase(t,hz,bend,.093,initial)
        drift = .043*np.sin(TAU*1.32*t+initial)
        for ch, position in enumerate((-1,1)):
            # Keeping the left oscillator stable lets the upper right voice
            # sweep through quadrature without pulling the whole chord sharp.
            angle = carrier + .42*drift + (spread+drift if ch else 0)
            tone = np.sin(angle)+.009*np.sin(2*angle+.36)
            direct[:,ch] += env*level*np.sqrt(1+position*seat)*tone
    # Quiet neighbouring resonances fill out the returning chord. Their slow
    # attack lets the three main pitches define the gesture.
    halo = env*(.34+.66*smooth((t-.065)/.16))
    for hz, gain, offset, seat in [(203.1,.027,.86,-.22),(229.4,.033,2.52,.31),(287.5,.024,1.80,-.18)]:
        for ch, side in enumerate((-1,1)):
            drift = .27*np.sin(TAU*(1.32+.13*ch)*t+offset)
            direct[:,ch] += halo*gain*(1+side*seat)*np.sin(TAU*hz*t+drift+offset+.61*side)
    stereo = direct.copy()
    for ch in (0,1):
        stereo[:,ch] += .086*delayed(direct[:,ch],(.041,.036)[ch])
        stereo[:,ch] += .060*delayed(direct[:,1-ch],(.073,.087)[ch])
        stereo[:,ch] += .039*delayed(direct[:,ch],(.134,.142)[ch])
    return stereo*release(t,.025)[:,None]


def keyboard_press():
    t=timeline(.126)
    body=contour(t,[(0,-90),(.0054,-64),(.0082,0),(.011,-2),(.015,-12),
                    (.023,-15),(.035,-24),(.063,-32),(.095,-42),(.126,-80)])
    attack=contour(t,[(0,-90),(.005,-79),(.0079,-1),(.0106,0),(.013,-12),
                      (.021,-32),(.052,-67),(.126,-100)])
    wood=.70*np.sin(phase(t,333,11,.011))+.49*np.sin(phase(t,404,-5,.010,.55))
    edge=.68*np.sin(phase(t,948,15,.007,.55))+.32*grain(t,680,.69,76)
    return centre(body*wood+attack*edge)


def key_latch(clear):
    length = .310 if clear else .174
    t=timeline(length)
    if clear:
        shape=[(0,-63),(.04,-39),(.078,-31),(.11,-23),(.134,-12),(.16,-14),
               (.179,-11),(.199,-3),(.213,0),(.238,-2.7),(.267,-3.4),(.276,-9),(.292,-47),(length,-85)]
        transient=[(0,-81),(.07,-51),(.128,-13),(.14,-25),(.18,-26),(.202,0),(.215,-25),(length,-90)]
    else:
        shape=[(0,-80),(.004,-62),(.0075,-1),(.010,0),(.018,-7),(.051,-7.9),
               (.079,-8.2),(.088,-12),(.101,-33),(.13,-62),(length,-89)]
        transient=[(0,-80),(.004,-65),(.007,0),(.013,-12),(.027,-37),(length,-84)]
    env=contour(t,shape); edge=contour(t,transient)
    phi=phase(t,450 if clear else 449,-5,.022)
    latch=.93*np.sin(phi) + .16*np.sin(3.02*phi-.4) + .06*np.sin(2.02*phi+.7)
    contact=.50*np.sin(phase(t,534,-7,.02))+ (.50 if clear else .24)*grain(t,650,.50,569 if clear else 807)
    return centre(latch*env+contact*edge)


def tick(notice):
    length=.027 if notice else .026
    t=timeline(length)
    if notice:
        phi=phase(t,3870,21,.0014)
        y=np.exp(-t/.0068)*(np.sin(phi) + .105*np.sin(1.49*phi+.3)*np.exp(-t/.003))
    else:
        fast=np.exp(-t/.00175)
        y=fast*(np.sin(phase(t,2048,-90,.0017))+.95*np.sin(phase(t,3760,145,.0018,.28)))
        y+=.35*np.exp(-t/.0075)*np.sin(phase(t,3270,140,.0022,-.35))
        y+=.10*grain(t,3900,.42,129)*fast
    return centre(y*smooth(t/.00033)*release(t,.004))


def confirm():
    t=timeline(.57)
    env=contour(t,[(0,-80),(.004,-54),(.009,0),(.016,-.7),(.045,-4.5),(.10,-7.1),
                   (.17,-10.7),(.25,-16.1),(.36,-25.4),(.49,-48),(.57,-76)])
    phi=phase(t,355,4,.027)
    upper=phase(t,539,-12,.023,.44)
    accent=.65*np.exp(-t/.102)
    dry=env*(np.sin(phi)+accent*np.sin(upper)+.24*np.exp(-t/.026)*np.sin(3.91*phi))
    diffuse=env*(.60*np.cos(phi)+.94*accent*np.cos(upper-.12))
    return room_cloud(dry,diffuse,.51)


def error():
    t=timeline(1.55)
    env=contour(t,[(0,-81),(.059,-49),(.19,-31),(.257,-23),(.29,-8),(.32,-11),
                   (.382,-8),(.462,-1.7),(.52,0),(.652,-4),(.80,-12.7),(1.06,-31),(1.55,-83)])
    low=phase(t,136,-2.8,.085,.13); upper=phase(t,270,3,.087,.82)
    top=.90*np.exp(-((t-.29)/.16)**2) + .23
    dry=env*(np.sin(low)+top*np.sin(upper)+.11*np.sin(3.018*low))
    sides=env*(.52*np.cos(low)+.63*top*np.cos(upper))
    return room_cloud(dry,sides,.83)


def swish():
    t=timeline(.70)
    env=contour(t,[(0,-80),(.091,-36),(.135,-17),(.182,-3),(.23,.0),
                   (.298,-3),(.367,-2.1),(.433,-8),(.516,-19),(.6,-40),(.7,-85)])
    bands=[(606,.75,.56),(925,.41,.44),(2310,.39,.31),(3800,.24,.12)]
    early=smooth((t-.13)/.19)
    def sweep(seed):
        out=np.zeros_like(t)
        for i,(hz,spread,weight) in enumerate(bands):
            shift=1.18-.27*early if i<2 else .76+.34*early
            out+=weight*shift*grain(t,hz,spread,seed+i)
        return out*env
    return room_cloud(sweep(181),sweep(291),.49)


def control_focus():
    # One soft, almost pure note for moving onto a small control.
    # Its energy builds over the first fifty milliseconds; a quiet room
    # response carries it away as the next control comes into focus.
    t = timeline(.29)
    pitch = phase(t, 797.0, -2.7, .030)
    swell = smooth(t/.054)**1.55
    decay = np.exp(-np.maximum(t-.050, 0)/.0082)
    direct = swell*decay*(np.sin(pitch) + .008*np.sin(2*pitch+.34))
    mid = direct + .113*delayed(direct,.028) + .042*delayed(direct,.071)
    mid += .018*delayed(direct,.096) + .006*delayed(direct,.156)
    side = .125*delayed(direct,.0065) + .052*delayed(direct,.023)
    return np.column_stack((mid+side, mid-side))*release(t,.032)[:,None]


def hall(strings):
    # A procedural, decorrelated room return. Lower registers linger longer
    # than bow noise. No impulse-response recording is needed.
    rate = RATE
    t = timeline(2.25)
    width = len(strings)+len(t)-1
    fft_size = 1 << (width-1).bit_length()
    mono = strings.mean(axis=1)
    out = np.zeros((width,2))
    for channel in range(2):
        ir = grain(t, 780, 1.45, 823+channel)*np.exp(-t/.275)
        ir += .42*grain(t, 2420, .78, 836+channel)*np.exp(-t/.168)
        ir *= smooth((t-.026)/.064)*release(t,.08)
        ir /= np.sqrt(np.dot(ir,ir))
        send = .58*strings[:,channel]+.42*mono
        wet = np.fft.irfft(np.fft.rfft(send,fft_size)*np.fft.rfft(ir,fft_size),fft_size)
        out[:,channel] = wet[:width]
    # Match wet energy to the dry orchestra for a reproducible room size.
    out *= .23*np.sqrt(np.sum(strings**2)/np.sum(out**2))
    return out


def poster_focus():
    # A softly swelling, nearly pure low-mid note. Only a trace of the lower
    # octave supports the body. Both oscillators share a slow, rounded onset.
    t = timeline(.335)
    shape = contour(t, [(0,-60),(.009,-22),(.022,-12),(.034,-5),
                        (.046,0),(.052,-.5),(.066,-6),(.087,-15),
                        (.111,-24),(.160,-33),(.230,-44),(.335,-88)])
    carrier = phase(t, 321.0, 1.0, .028)
    direct = shape * (np.sin(carrier) + .112*np.sin(.5*carrier+.27)
                      + .022*np.sin(2*carrier-.22))
    halo = .11*delayed(direct,.0227) + .055*delayed(direct,.047)
    side = .16*delayed(direct,.0135)
    return np.column_stack((direct+halo+side,direct+halo-side))*release(t,.025)[:,None]


INSTRUMENTS = {
    "horn_f": ("MOHorn_sus_F2_v1_1.wav", 53),
    "cello_g": ("susvib_G1_v1_1.wav", 43),
    "cello_d": ("susvib_D2_v1_1.wav", 50),
    "violin_low_a": ("VlnEns_susVib_A2_v1.wav", 57),
    "violin_d": ("VlnEns_susVib_D3_v1.wav", 62),
    "violin_fs": ("VlnEns_susVib_Fs3_v1.wav", 66),
    "violin_a": ("VlnEns_susVib_A3_v1.wav", 69),
    "violin_e": ("VlnEns_susVib_E4_v1.wav", 76),
}


def cues():
    yield "cursor", -27.3, control_focus()
    yield "focus-large", -22.35, poster_focus()
    yield "focus-app", -23.25, soft_focus(412,.050,.088,.125,.31,.23)
    yield "focus-key", -29.0, key_focus()
    yield "click", -24.0, select_navigation()
    yield "back", -24.0, back_navigation()
    yield "key", -23.15, keyboard_press()
    yield "delete", -23.25, key_latch(False)
    yield "clear", -23.45, key_latch(True)
    yield "toggle", -10.0, tick(False)
    yield "confirm", -8.6, confirm()
    yield "error", -10.7, error()
    yield "notify", -9.1, tick(True)
    yield "capture", -17.3, shutter()
    yield "swish", -14.3, swish()




def render(destination):
    destination.mkdir(parents=True, exist_ok=True)
    for name, peak_db, mix in cues():
        if peak_db is not None:
            mix *= (10 ** (peak_db / 20)) / np.abs(mix).max()
        pcm = np.rint(mix * 32767).astype("<i2")
        with wave.open(str(destination / (name + ".wav")), "wb") as f:
            f.setparams((2, 2, RATE, 0, "NONE", "not compressed"))
            f.writeframes(pcm.tobytes())


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python generate_sounds.py OUTPUT_DIRECTORY")
    render(Path(sys.argv[1]))
