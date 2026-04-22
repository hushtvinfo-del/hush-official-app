import React, { useState, useEffect } from 'react';

const Section = ({ title, children }) => (
  <div className="mb-16">
    <div className="flex items-center gap-4 mb-8">
      <div className="h-px flex-1 bg-gradient-to-r from-cyan-500/50 to-transparent" />
      <h2 className="text-cyan-400 text-xs font-bold tracking-[0.3em] uppercase">{title}</h2>
      <div className="h-px flex-1 bg-gradient-to-l from-cyan-500/50 to-transparent" />
    </div>
    {children}
  </div>
);

const ColorSwatch = ({ hex, name, usage }) => (
  <div className="flex flex-col gap-2">
    <div className="w-full h-20 rounded-xl border border-white/10 shadow-lg" style={{ backgroundColor: hex }} />
    <div>
      <p className="text-white font-bold text-sm">{name}</p>
      <p className="text-cyan-400 font-mono text-xs">{hex}</p>
      <p className="text-gray-400 text-xs mt-1">{usage}</p>
    </div>
  </div>
);

const SpecRow = ({ label, value, mono }) => (
  <div className="flex items-start justify-between py-3 border-b border-white/5">
    <span className="text-gray-400 text-sm">{label}</span>
    <span className={`text-white text-sm text-right max-w-xs ${mono ? 'font-mono text-cyan-300' : 'font-medium'}`}>{value}</span>
  </div>
);

const CodeBlock = ({ code }) => (
  <pre className="bg-black/60 border border-white/10 rounded-xl p-4 text-cyan-300 font-mono text-xs overflow-x-auto leading-relaxed">
    {code}
  </pre>
);

const MockupCard = ({ label, children }) => (
  <div className="flex flex-col gap-3">
    <p className="text-gray-400 text-xs uppercase tracking-widest">{label}</p>
    {children}
  </div>
);

// App Icon Mockup
const AppIconMockup = ({ size = 120, variant = 'primary' }) => {
  const variants = {
    primary: { bg: '#000000', dot: '#06B6D4' },
    rounded: { bg: '#0F172A', dot: '#06B6D4' },
    glow: { bg: '#000000', dot: '#06B6D4' },
  };
  const v = variants[variant];
  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: size * 0.22,
        background: v.bg,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexDirection: 'column',
        boxShadow: variant === 'glow' ? `0 0 40px rgba(6,182,212,0.5), 0 0 80px rgba(6,182,212,0.2)` : '0 8px 32px rgba(0,0,0,0.8)',
        border: '1px solid rgba(6,182,212,0.2)',
        position: 'relative',
        overflow: 'hidden',
      }}
    >
      {variant === 'glow' && (
        <div style={{
          position: 'absolute', top: '50%', left: '50%',
          transform: 'translate(-50%, -50%)',
          width: size * 1.2, height: size * 1.2,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(6,182,212,0.15) 0%, transparent 70%)',
        }} />
      )}
      <div style={{ fontFamily: "'Inter', sans-serif", fontWeight: 900, fontSize: size * 0.22, lineHeight: 1, letterSpacing: '-0.02em', zIndex: 1 }}>
        <span style={{ color: '#FFFFFF' }}>hush</span>
        <span style={{ color: '#06B6D4' }}>tv.</span>
      </div>
      <div style={{ width: size * 0.1, height: 2, background: v.dot, borderRadius: 2, marginTop: size * 0.06, opacity: 0.6 }} />
    </div>
  );
};

// TV Focus Ring Demo
const FocusDemo = () => {
  const [focused, setFocused] = useState(1);
  const items = ['Live TV', 'Movies', 'Series', 'Search'];
  return (
    <div className="flex gap-3 flex-wrap">
      {items.map((item, i) => (
        <button
          key={i}
          onClick={() => setFocused(i)}
          style={{
            padding: '12px 24px',
            borderRadius: 12,
            border: focused === i ? '2px solid #06B6D4' : '2px solid rgba(255,255,255,0.1)',
            background: focused === i ? 'rgba(6,182,212,0.15)' : 'rgba(255,255,255,0.05)',
            color: focused === i ? '#06B6D4' : '#9CA3AF',
            fontWeight: 600,
            fontSize: 14,
            transform: focused === i ? 'scale(1.06)' : 'scale(1)',
            boxShadow: focused === i ? '0 0 0 3px rgba(6,182,212,0.3), 0 8px 24px rgba(6,182,212,0.2)' : 'none',
            transition: 'all 0.15s ease',
            cursor: 'pointer',
          }}
        >
          {item}
        </button>
      ))}
    </div>
  );
};

// Animated Loading Screen Mockup
const LoadingScreenMockup = () => {
  const [progress, setProgress] = React.useState(0);
  const [logoOpacity, setLogoOpacity] = React.useState(0);
  const [logoScale, setLogoScale] = React.useState(0.85);
  const [dotOpacity, setDotOpacity] = React.useState(0);
  const [taglineOpacity, setTaglineOpacity] = React.useState(0);
  const [running, setRunning] = React.useState(false);

  const runAnimation = () => {
    if (running) return;
    setRunning(true);
    setProgress(0); setLogoOpacity(0); setLogoScale(0.85); setDotOpacity(0); setTaglineOpacity(0);

    setTimeout(() => { setLogoOpacity(1); setLogoScale(1); }, 300);
    setTimeout(() => { setDotOpacity(1); }, 450);
    setTimeout(() => {
      let p = 0;
      const interval = setInterval(() => {
        p += 1.2;
        setProgress(Math.min(p, 100));
        if (p >= 100) clearInterval(interval);
      }, 14);
    }, 900);
    setTimeout(() => { setTaglineOpacity(1); }, 1800);
    setTimeout(() => { setRunning(false); }, 3200);
  };

  React.useEffect(() => { runAnimation(); }, []);

  return (
    <div style={{ position: 'relative' }}>
      <div style={{
        background: '#000000',
        borderRadius: 16,
        height: 320,
        display: 'flex', alignItems: 'center', justifyContent: 'center', flexDirection: 'column',
        border: '1px solid rgba(255,255,255,0.1)',
        overflow: 'hidden',
        position: 'relative',
      }}>
        {/* Subtle radial glow behind logo */}
        <div style={{
          position: 'absolute', top: '50%', left: '50%',
          transform: 'translate(-50%, -50%)',
          width: 400, height: 400, borderRadius: '50%',
          background: `radial-gradient(circle, rgba(6,182,212,${logoOpacity * 0.07}) 0%, transparent 65%)`,
          transition: 'background 0.8s ease',
          pointerEvents: 'none',
        }} />

        {/* Logo */}
        <div style={{
          opacity: logoOpacity,
          transform: `scale(${logoScale})`,
          transition: 'opacity 0.6s ease, transform 0.6s cubic-bezier(0.16,1,0.3,1)',
          fontFamily: 'Inter, sans-serif', fontWeight: 900,
          fontSize: 72, letterSpacing: '-0.03em', lineHeight: 1,
          marginBottom: 16,
        }}>
          <span style={{ color: '#FFFFFF' }}>hush</span>
          <span style={{ color: '#06B6D4', opacity: dotOpacity, transition: 'opacity 0.4s ease' }}>tv.</span>
        </div>

        {/* Tagline */}
        <div style={{
          opacity: taglineOpacity,
          transition: 'opacity 0.5s ease',
          color: '#475569', fontSize: 14, letterSpacing: '0.14em',
          fontFamily: 'Inter, sans-serif', fontWeight: 400, textTransform: 'uppercase',
        }}>
          Your Stream. Your Way.
        </div>

        {/* Progress bar pinned to bottom */}
        <div style={{
          position: 'absolute', bottom: 0, left: 0, right: 0,
          height: 2, background: 'rgba(255,255,255,0.06)',
        }}>
          <div style={{
            height: '100%',
            width: `${progress}%`,
            background: '#06B6D4',
            borderRadius: '0 2px 2px 0',
            transition: 'width 0.01s linear',
            boxShadow: '0 0 8px rgba(6,182,212,0.8)',
          }} />
        </div>

        {/* TV badge */}
        <div style={{
          position: 'absolute', top: 16, right: 16,
          fontSize: 10, color: '#334155', fontFamily: 'monospace', letterSpacing: '0.1em',
        }}>1920×1080</div>
      </div>

      <button
        onClick={runAnimation}
        disabled={running}
        style={{
          marginTop: 12, padding: '8px 20px', borderRadius: 8,
          background: running ? 'rgba(255,255,255,0.05)' : 'rgba(6,182,212,0.15)',
          border: '1px solid rgba(6,182,212,0.3)',
          color: running ? '#475569' : '#06B6D4',
          fontSize: 13, fontWeight: 600, cursor: running ? 'not-allowed' : 'pointer',
          transition: 'all 0.2s ease',
        }}
      >
        {running ? 'Playing…' : '▶ Replay Animation'}
      </button>
    </div>
  );
};

// ─── Top Nav Mockup ───────────────────────────────────────────
const TopNavMockup = () => {
  const [active, setActive] = React.useState('Home');
  const tabs = [
    { id: 'Home', icon: '⌂' },
    { id: 'Live TV', icon: '📡' },
    { id: 'Movies', icon: '🎬' },
    { id: 'Series', icon: '📺' },
    { id: 'Search', icon: '🔍' },
    { id: 'My List', icon: '♡' },
  ];
  return (
    <div style={{ background: '#0F172A', borderRadius: 12, overflow: 'hidden', border: '1px solid rgba(255,255,255,0.06)' }}>
      <div style={{
        display: 'flex', alignItems: 'center', gap: 0,
        padding: '0 24px', height: 64,
        background: 'rgba(0,0,0,0.6)',
        borderBottom: '1px solid rgba(255,255,255,0.05)',
      }}>
        {/* Logo */}
        <div style={{ fontWeight: 900, fontSize: 22, letterSpacing: '-0.02em', marginRight: 40, flexShrink: 0 }}>
          <span style={{ color: '#fff' }}>hush</span><span style={{ color: '#06B6D4' }}>tv.</span>
        </div>
        {/* Tabs */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 4, flex: 1 }}>
          {tabs.map(t => (
            <button key={t.id} onClick={() => setActive(t.id)} style={{
              padding: '0 16px', height: 64, background: 'transparent', border: 'none',
              borderBottom: active === t.id ? '3px solid #06B6D4' : '3px solid transparent',
              color: active === t.id ? '#FFFFFF' : '#64748B',
              fontWeight: active === t.id ? 700 : 500, fontSize: 14,
              cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 6,
              transition: 'all 0.15s ease', fontFamily: 'Inter, sans-serif',
              whiteSpace: 'nowrap',
            }}>
              <span style={{ fontSize: 13 }}>{t.icon}</span> {t.id}
            </button>
          ))}
        </div>
        {/* Avatar */}
        <div style={{
          width: 32, height: 32, borderRadius: '50%', flexShrink: 0,
          background: 'linear-gradient(135deg, #3B82F6, #06B6D4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: 13, fontWeight: 700, color: 'white',
        }}>G</div>
      </div>
      <div style={{ padding: '14px 24px', display: 'flex', alignItems: 'center', gap: 8 }}>
        <div style={{ width: 6, height: 6, borderRadius: '50%', background: '#06B6D4' }} />
        <p style={{ color: '#475569', fontSize: 12, fontFamily: 'monospace' }}>
          Active tab: <span style={{ color: '#06B6D4' }}>{active}</span> — click tabs above to preview focus states
        </p>
      </div>
    </div>
  );
};

// ─── Hero Billboard Mockup ────────────────────────────────────
const HeroBillboardMockup = () => {
  const [current, setCurrent] = React.useState(0);
  const slides = [
    { title: 'Breaking Bad', genre: ['Crime', 'Drama', 'Thriller'], synopsis: 'A high school chemistry teacher turned methamphetamine producer partners with a former student to secure his family\'s future.', badge: 'SERIES', color: '#F59E0B' },
    { title: 'Dune: Part Two', genre: ['Sci-Fi', 'Adventure', 'Action'], synopsis: 'Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.', badge: 'MOVIE', color: '#3B82F6' },
    { title: 'Premier League Live', genre: ['Live', 'Sports', 'Football'], synopsis: 'Manchester City vs Arsenal — live from the Etihad Stadium. Kick-off 20:45 BST.', badge: '🔴 LIVE', color: '#EF4444' },
  ];
  React.useEffect(() => {
    const t = setInterval(() => setCurrent(c => (c + 1) % slides.length), 5000);
    return () => clearInterval(t);
  }, []);
  const s = slides[current];
  return (
    <div style={{ position: 'relative', borderRadius: 12, overflow: 'hidden', height: 260, background: '#0F172A', border: '1px solid rgba(255,255,255,0.06)' }}>
      {/* fake backdrop */}
      <div style={{
        position: 'absolute', inset: 0,
        background: current === 0
          ? 'linear-gradient(135deg, #1E0A00 0%, #2D1A00 50%, #0F172A 100%)'
          : current === 1
          ? 'linear-gradient(135deg, #000D1A 0%, #001833 50%, #0F172A 100%)'
          : 'linear-gradient(135deg, #0A0000 0%, #1A0000 50%, #0F172A 100%)',
        transition: 'background 0.6s ease',
      }} />
      {/* Gradient overlay */}
      <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to right, rgba(0,0,0,0.95) 0%, rgba(0,0,0,0.6) 60%, transparent 100%)' }} />
      {/* Content */}
      <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, padding: '0 32px 28px' }}>
        {/* Badge */}
        <div style={{ display: 'inline-block', padding: '3px 10px', borderRadius: 4, background: s.color, color: '#000', fontWeight: 700, fontSize: 10, letterSpacing: '0.1em', marginBottom: 10, fontFamily: 'Inter, sans-serif' }}>{s.badge}</div>
        {/* Title */}
        <div style={{ fontWeight: 900, fontSize: 36, color: '#fff', lineHeight: 1.1, marginBottom: 10, fontFamily: 'Inter, sans-serif', letterSpacing: '-0.02em' }}>{s.title}</div>
        {/* Genres */}
        <div style={{ display: 'flex', gap: 6, marginBottom: 10, flexWrap: 'wrap' }}>
          {s.genre.map(g => (
            <span key={g} style={{ padding: '3px 10px', borderRadius: 999, background: 'rgba(255,255,255,0.1)', color: '#CBD5E1', fontSize: 11, fontWeight: 600, fontFamily: 'Inter, sans-serif' }}>{g}</span>
          ))}
        </div>
        {/* Synopsis */}
        <p style={{ color: '#94A3B8', fontSize: 13, lineHeight: 1.6, marginBottom: 16, maxWidth: 480, fontFamily: 'Inter, sans-serif' }}>{s.synopsis}</p>
        {/* CTAs */}
        <div style={{ display: 'flex', gap: 10 }}>
          <button style={{ padding: '10px 24px', borderRadius: 8, background: '#fff', color: '#000', fontWeight: 700, fontSize: 14, border: 'none', cursor: 'pointer', fontFamily: 'Inter, sans-serif' }}>▶ Play</button>
          <button style={{ padding: '10px 20px', borderRadius: 8, background: 'rgba(255,255,255,0.12)', color: '#fff', fontWeight: 600, fontSize: 14, border: '1px solid rgba(255,255,255,0.2)', cursor: 'pointer', fontFamily: 'Inter, sans-serif' }}>+ My List</button>
          <button style={{ padding: '10px 16px', borderRadius: 8, background: 'transparent', color: '#94A3B8', fontWeight: 600, fontSize: 14, border: '1px solid rgba(255,255,255,0.1)', cursor: 'pointer', fontFamily: 'Inter, sans-serif' }}>ℹ More Info</button>
        </div>
      </div>
      {/* Slide dots */}
      <div style={{ position: 'absolute', bottom: 14, right: 24, display: 'flex', gap: 6 }}>
        {slides.map((_, i) => (
          <button key={i} onClick={() => setCurrent(i)} style={{
            width: i === current ? 20 : 6, height: 6, borderRadius: 3,
            background: i === current ? '#06B6D4' : 'rgba(255,255,255,0.2)',
            border: 'none', cursor: 'pointer', transition: 'all 0.3s ease', padding: 0,
          }} />
        ))}
      </div>
      {/* Label */}
      <div style={{ position: 'absolute', top: 12, right: 12, background: 'rgba(0,0,0,0.6)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 6, padding: '4px 10px' }}>
        <p style={{ color: '#475569', fontSize: 10, fontFamily: 'monospace' }}>Auto-rotates every 5s · click dots to switch (on TV: auto-only, stops when D-pad focuses hero)</p>
      </div>
    </div>
  );
};

// ─── Content Rows Mockup ──────────────────────────────────────
const ContentRowsMockup = () => {
  const [focusedLive, setFocusedLive] = React.useState(null);
  const [focusedTrend, setFocusedTrend] = React.useState(null);
  const liveChannels = [
    { name: 'Sky Sports', show: 'Premier League', progress: 62, live: true },
    { name: 'BBC One', show: 'The Graham Norton Show', progress: 35, live: true },
    { name: 'CNN', show: 'Breaking News', progress: 88, live: true },
    { name: 'Discovery', show: 'Planet Earth III', progress: 15, live: true },
    { name: 'HBO', show: 'House of the Dragon', progress: 50, live: true },
  ];
  const trending = [
    { title: 'Oppenheimer', rank: 1 },
    { title: 'The Bear', rank: 2 },
    { title: 'Poor Things', rank: 3 },
    { title: 'Succession', rank: 4 },
    { title: 'Dune 2', rank: 5 },
    { title: 'True Det.', rank: 6 },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 32 }}>
      {/* Live Now Row */}
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 14 }}>
          <div style={{ width: 8, height: 8, borderRadius: '50%', background: '#EF4444', boxShadow: '0 0 6px #EF4444', animation: 'pulse 1.5s infinite' }} />
          <p style={{ color: '#fff', fontWeight: 700, fontSize: 16, fontFamily: 'Inter, sans-serif' }}>Live Now</p>
          <span style={{ background: '#EF4444', color: '#fff', fontSize: 9, fontWeight: 700, padding: '2px 6px', borderRadius: 3, letterSpacing: '0.08em', fontFamily: 'Inter, sans-serif' }}>LIVE</span>
        </div>
        <div style={{ display: 'flex', gap: 12, overflowX: 'auto', paddingBottom: 8 }}>
          {liveChannels.map((ch, i) => (
            <div key={i}
              onMouseEnter={() => setFocusedLive(i)} onMouseLeave={() => setFocusedLive(null)}
              style={{
                flexShrink: 0, width: 200, borderRadius: 10, overflow: 'hidden',
                border: focusedLive === i ? '2px solid #06B6D4' : '2px solid rgba(255,255,255,0.06)',
                transform: focusedLive === i ? 'scale(1.06)' : 'scale(1)',
                boxShadow: focusedLive === i ? '0 0 0 3px rgba(6,182,212,0.25), 0 12px 32px rgba(0,0,0,0.8)' : 'none',
                transition: 'all 0.15s ease', cursor: 'pointer', background: '#0F172A',
              }}>
              <div style={{ height: 110, background: `linear-gradient(135deg, hsl(${i * 60}, 40%, 12%), #0F172A)`, display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative' }}>
                <span style={{ fontSize: 28 }}>📺</span>
                <div style={{ position: 'absolute', top: 8, left: 8, display: 'flex', alignItems: 'center', gap: 4, background: '#EF4444', borderRadius: 3, padding: '2px 6px' }}>
                  <div style={{ width: 5, height: 5, borderRadius: '50%', background: '#fff' }} />
                  <span style={{ color: '#fff', fontSize: 9, fontWeight: 700, letterSpacing: '0.05em', fontFamily: 'Inter, sans-serif' }}>LIVE</span>
                </div>
              </div>
              <div style={{ padding: '10px 12px' }}>
                <p style={{ color: '#fff', fontWeight: 600, fontSize: 12, marginBottom: 2, fontFamily: 'Inter, sans-serif' }}>{ch.name}</p>
                <p style={{ color: '#64748B', fontSize: 10, marginBottom: 8, fontFamily: 'Inter, sans-serif', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{ch.show}</p>
                <div style={{ height: 3, background: 'rgba(255,255,255,0.08)', borderRadius: 2 }}>
                  <div style={{ height: '100%', width: `${ch.progress}%`, background: '#06B6D4', borderRadius: 2 }} />
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Trending Row */}
      <div>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14 }}>
          <p style={{ color: '#fff', fontWeight: 700, fontSize: 16, fontFamily: 'Inter, sans-serif' }}>🔥 Trending This Week</p>
          <span style={{ color: '#06B6D4', fontSize: 12, fontFamily: 'Inter, sans-serif', cursor: 'pointer' }}>See All →</span>
        </div>
        <div style={{ display: 'flex', gap: 10, overflowX: 'auto', paddingBottom: 8 }}>
          {trending.map((item, i) => (
            <div key={i}
              onMouseEnter={() => setFocusedTrend(i)} onMouseLeave={() => setFocusedTrend(null)}
              style={{
                flexShrink: 0, width: 110, borderRadius: 10, overflow: 'hidden', position: 'relative',
                border: focusedTrend === i ? '2px solid #06B6D4' : '2px solid rgba(255,255,255,0.06)',
                transform: focusedTrend === i ? 'scale(1.08)' : 'scale(1)',
                boxShadow: focusedTrend === i ? '0 0 0 3px rgba(6,182,212,0.25)' : 'none',
                transition: 'all 0.15s ease', cursor: 'pointer', background: '#0F172A',
              }}>
              <div style={{ height: 165, background: `linear-gradient(160deg, hsl(${220 + i * 30}, 50%, 15%), #000)`, display: 'flex', alignItems: 'center', justifyContent: 'center', position: 'relative' }}>
                <span style={{ fontSize: 24 }}>{['🎬','📺','🎭','👑','🚀','🕵️'][i]}</span>
                {/* Ghost rank number */}
                <div style={{ position: 'absolute', bottom: -6, left: 6, fontWeight: 900, fontSize: 64, color: 'rgba(255,255,255,0.08)', lineHeight: 1, fontFamily: 'Inter, sans-serif', userSelect: 'none' }}>{item.rank}</div>
              </div>
              <div style={{ padding: '8px 10px' }}>
                <p style={{ color: '#E2E8F0', fontWeight: 600, fontSize: 11, fontFamily: 'Inter, sans-serif', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{item.title}</p>
              </div>
            </div>
          ))}
        </div>
        <p style={{ color: '#334155', fontSize: 11, fontFamily: 'monospace', marginTop: 10 }}>↑ Mouse-over cards to preview D-pad focus states (on TV this is driven by remote navigation)</p>
      </div>
    </div>
  );
};

// ─── Full Home Screen Overview Mockup ────────────────────────
const HomeScreenMockup = () => (
  <div style={{ background: '#000', borderRadius: 16, border: '1px solid rgba(255,255,255,0.08)', overflow: 'hidden' }}>
    {/* Mock nav bar */}
    <div style={{ background: 'rgba(0,0,0,0.9)', borderBottom: '1px solid rgba(255,255,255,0.04)', padding: '0 24px', height: 52, display: 'flex', alignItems: 'center', gap: 24 }}>
      <span style={{ fontWeight: 900, fontSize: 18, letterSpacing: '-0.02em', flexShrink: 0, fontFamily: 'Inter, sans-serif' }}><span style={{ color: '#fff' }}>hush</span><span style={{ color: '#06B6D4' }}>tv.</span></span>
      {['Home','Live TV','Movies','Series','Search'].map((t, i) => (
        <span key={t} style={{ fontSize: 12, fontWeight: i === 0 ? 700 : 500, color: i === 0 ? '#fff' : '#475569', fontFamily: 'Inter, sans-serif', borderBottom: i === 0 ? '2px solid #06B6D4' : 'none', paddingBottom: i === 0 ? 2 : 0, whiteSpace: 'nowrap' }}>{t}</span>
      ))}
    </div>
    {/* Mock hero */}
    <div style={{ height: 180, background: 'linear-gradient(135deg, #1a0a00, #0F172A)', position: 'relative', display: 'flex', alignItems: 'flex-end' }}>
      <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to right, rgba(0,0,0,0.9) 0%, rgba(0,0,0,0.3) 60%, transparent 100%)' }} />
      <div style={{ position: 'relative', padding: '0 24px 20px', zIndex: 1 }}>
        <div style={{ display: 'inline-block', background: '#F59E0B', color: '#000', fontSize: 9, fontWeight: 700, padding: '2px 8px', borderRadius: 3, marginBottom: 6, fontFamily: 'Inter, sans-serif' }}>SERIES</div>
        <div style={{ fontSize: 26, fontWeight: 900, color: '#fff', letterSpacing: '-0.02em', marginBottom: 6, fontFamily: 'Inter, sans-serif' }}>Breaking Bad</div>
        <div style={{ display: 'flex', gap: 6, marginBottom: 10 }}>
          {['Crime','Drama','Thriller'].map(g => <span key={g} style={{ background: 'rgba(255,255,255,0.1)', color: '#CBD5E1', fontSize: 9, fontWeight: 600, padding: '2px 8px', borderRadius: 999, fontFamily: 'Inter, sans-serif' }}>{g}</span>)}
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button style={{ padding: '6px 16px', borderRadius: 6, background: '#fff', color: '#000', fontWeight: 700, fontSize: 11, border: 'none', cursor: 'pointer', fontFamily: 'Inter, sans-serif' }}>▶ Play</button>
          <button style={{ padding: '6px 12px', borderRadius: 6, background: 'rgba(255,255,255,0.1)', color: '#fff', fontWeight: 600, fontSize: 11, border: '1px solid rgba(255,255,255,0.15)', cursor: 'pointer', fontFamily: 'Inter, sans-serif' }}>+ My List</button>
        </div>
      </div>
      <div style={{ position: 'absolute', bottom: 10, right: 16, display: 'flex', gap: 5 }}>
        {[0,1,2].map(i => <div key={i} style={{ width: i===0?14:5, height: 5, borderRadius: 3, background: i===0?'#06B6D4':'rgba(255,255,255,0.2)' }} />)}
      </div>
    </div>
    {/* Row labels as placeholders */}
    {[
      { label: '🔴 Live Now', color: '#EF4444', cards: 5, wide: true },
      { label: '▶ Continue Watching', color: '#F59E0B', cards: 4, wide: true },
      { label: '🔥 Trending This Week', color: '#8B5CF6', cards: 6, wide: false },
      { label: '📺 Featured Series', color: '#06B6D4', cards: 6, wide: false },
      { label: '🎬 New Arrivals — Movies', color: '#3B82F6', cards: 6, wide: false },
    ].map(row => (
      <div key={row.label} style={{ padding: '16px 24px 8px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
          <span style={{ color: '#fff', fontWeight: 700, fontSize: 13, fontFamily: 'Inter, sans-serif' }}>{row.label}</span>
          <span style={{ color: '#334155', fontSize: 11, fontFamily: 'Inter, sans-serif' }}>See All →</span>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          {Array.from({ length: row.cards }).map((_, i) => (
            <div key={i} style={{
              flexShrink: 0,
              width: row.wide ? 100 : 70,
              height: row.wide ? 56 : 100,
              borderRadius: 8,
              background: `linear-gradient(135deg, hsl(${row.wide ? 200 : 220 + i*25}, 40%, ${10+i*2}%), #000)`,
              border: '1px solid rgba(255,255,255,0.05)',
              position: 'relative', overflow: 'hidden',
            }}>
              {row.label.includes('Live') && (
                <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: 3, background: 'rgba(255,255,255,0.08)' }}>
                  <div style={{ height: '100%', width: `${30 + i * 15}%`, background: '#06B6D4' }} />
                </div>
              )}
              {row.label.includes('Continue') && (
                <div style={{ position: 'absolute', bottom: 0, left: 0, right: 0, height: 3, background: 'rgba(255,255,255,0.08)' }}>
                  <div style={{ height: '100%', width: `${20 + i * 18}%`, background: '#06B6D4' }} />
                </div>
              )}
              {row.label.includes('Trending') && (
                <div style={{ position: 'absolute', bottom: -4, left: 4, fontWeight: 900, fontSize: 36, color: 'rgba(255,255,255,0.07)', fontFamily: 'Inter, sans-serif', lineHeight: 1 }}>{i+1}</div>
              )}
            </div>
          ))}
        </div>
      </div>
    ))}
    <div style={{ padding: '8px 24px 16px' }}>
      <p style={{ color: '#1E293B', fontSize: 10, fontFamily: 'monospace' }}>↑ Full home screen overview — rows continue scrolling below (Genre rows, Recommended, etc.)</p>
    </div>
  </div>
);

// ─── Login Screen Mockup ──────────────────────────────────────
const LoginScreenMockup = () => {
  const [step, setStep] = React.useState(0);
  const [host, setHost] = React.useState('');
  const [user, setUser] = React.useState('');
  const [pass, setPass] = React.useState('');
  const [showPass, setShowPass] = React.useState(false);
  const [loading, setLoading] = React.useState(false);
  const [success, setSuccess] = React.useState(false);
  const [error, setError] = React.useState('');
  const [shake, setShake] = React.useState(false);

  const handleConnect = () => {
    if (!host || !user || !pass) {
      setError('Please fill in all fields.');
      setShake(true);
      setTimeout(() => setShake(false), 500);
      return;
    }
    setError('');
    setLoading(true);
    setTimeout(() => {
      setLoading(false);
      setSuccess(true);
    }, 1800);
  };

  const reset = () => { setHost(''); setUser(''); setPass(''); setSuccess(false); setError(''); setLoading(false); setStep(0); };

  const inputStyle = (focused) => ({
    width: '100%', height: 52, borderRadius: 10, border: `1px solid ${error && !focused ? '#EF4444' : focused ? '#06B6D4' : '#1E293B'}`,
    background: '#0F172A', color: '#fff', padding: '0 14px', fontSize: 14,
    fontFamily: 'Inter, sans-serif', outline: 'none', boxSizing: 'border-box',
    boxShadow: focused ? '0 0 0 3px rgba(6,182,212,0.15)' : 'none',
    transition: 'all 0.2s ease',
  });

  return (
    <div style={{ background: '#000', borderRadius: 16, padding: '48px 24px', display: 'flex', flexDirection: 'column', alignItems: 'center', border: '1px solid rgba(255,255,255,0.06)', minHeight: 480 }}>
      {/* Logo */}
      <div style={{ fontWeight: 900, fontSize: 36, letterSpacing: '-0.03em', marginBottom: 8, fontFamily: 'Inter, sans-serif' }}>
        <span style={{ color: '#fff' }}>hush</span><span style={{ color: '#06B6D4' }}>tv.</span>
      </div>
      <p style={{ color: '#475569', fontSize: 13, fontFamily: 'Inter, sans-serif', marginBottom: 36, letterSpacing: '0.08em', textTransform: 'uppercase', fontWeight: 400 }}>Your Stream. Your Way.</p>

      {/* Step dots */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 28 }}>
        {[0,1,2].map(i => (
          <div key={i} style={{ width: i === step ? 20 : 8, height: 8, borderRadius: 4, background: i < step ? '#fff' : i === step ? '#06B6D4' : '#1E293B', transition: 'all 0.3s ease' }} />
        ))}
      </div>

      {/* Card */}
      <div style={{
        width: '100%', maxWidth: 380,
        background: 'rgba(255,255,255,0.03)', border: '1px solid rgba(255,255,255,0.08)',
        borderRadius: 16, padding: '32px 28px',
        animation: shake ? 'shake 0.4s ease' : 'none',
      }}>
        <style>{`
          @keyframes shake {
            0%,100% { transform: translateX(0); }
            20%      { transform: translateX(-6px); }
            40%      { transform: translateX(6px); }
            60%      { transform: translateX(-4px); }
            80%      { transform: translateX(4px); }
          }
        `}</style>

        {success ? (
          <div style={{ textAlign: 'center', padding: '20px 0' }}>
            <div style={{ fontSize: 48, marginBottom: 16 }}>✅</div>
            <p style={{ color: '#22C55E', fontWeight: 700, fontSize: 18, fontFamily: 'Inter, sans-serif', marginBottom: 8 }}>Connected!</p>
            <p style={{ color: '#64748B', fontSize: 13, fontFamily: 'Inter, sans-serif', marginBottom: 20 }}>Loading your content…</p>
            <button onClick={reset} style={{ background: 'transparent', border: '1px solid rgba(255,255,255,0.1)', color: '#64748B', borderRadius: 8, padding: '8px 20px', cursor: 'pointer', fontSize: 13, fontFamily: 'Inter, sans-serif' }}>← Reset demo</button>
          </div>
        ) : (
          <>
            <p style={{ color: '#fff', fontWeight: 700, fontSize: 16, fontFamily: 'Inter, sans-serif', marginBottom: 6 }}>Connect Your Account</p>
            <p style={{ color: '#475569', fontSize: 13, fontFamily: 'Inter, sans-serif', marginBottom: 24 }}>Enter your Xtream Codes credentials below.</p>

            <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
              <div>
                <label style={{ display: 'block', color: '#64748B', fontSize: 12, fontWeight: 500, marginBottom: 6, fontFamily: 'Inter, sans-serif', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Server URL</label>
                <input value={host} onChange={e => setHost(e.target.value)} placeholder="https://your-iptv-host.com" style={inputStyle(false)} />
              </div>
              <div>
                <label style={{ display: 'block', color: '#64748B', fontSize: 12, fontWeight: 500, marginBottom: 6, fontFamily: 'Inter, sans-serif', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Username</label>
                <input value={user} onChange={e => setUser(e.target.value)} placeholder="Username" style={inputStyle(false)} />
              </div>
              <div style={{ position: 'relative' }}>
                <label style={{ display: 'block', color: '#64748B', fontSize: 12, fontWeight: 500, marginBottom: 6, fontFamily: 'Inter, sans-serif', textTransform: 'uppercase', letterSpacing: '0.06em' }}>Password</label>
                <input value={pass} onChange={e => setPass(e.target.value)} type={showPass ? 'text' : 'password'} placeholder="Password" style={{ ...inputStyle(false), paddingRight: 44 }} />
                <button onClick={() => setShowPass(s => !s)} style={{ position: 'absolute', right: 12, top: 34, background: 'transparent', border: 'none', color: '#475569', cursor: 'pointer', fontSize: 16 }}>
                  {showPass ? '🙈' : '👁'}
                </button>
              </div>
            </div>

            {error && <p style={{ color: '#EF4444', fontSize: 12, fontFamily: 'Inter, sans-serif', marginTop: 12 }}>⚠ {error}</p>}

            <button
              onClick={handleConnect}
              disabled={loading}
              style={{
                width: '100%', height: 52, borderRadius: 10, border: 'none', marginTop: 20,
                background: loading ? 'rgba(6,182,212,0.4)' : '#06B6D4',
                color: '#000', fontWeight: 700, fontSize: 15, fontFamily: 'Inter, sans-serif',
                cursor: loading ? 'not-allowed' : 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 8,
                transition: 'all 0.2s ease',
              }}
            >
              {loading ? (
                <span style={{ display: 'inline-block', width: 20, height: 20, border: '2px solid rgba(0,0,0,0.2)', borderTop: '2px solid #000', borderRadius: '50%', animation: 'spin 0.7s linear infinite' }} />
              ) : 'Connect →'}
            </button>
            <style>{`@keyframes spin { to { transform: rotate(360deg); } }`}</style>

            <p style={{ textAlign: 'center', color: '#334155', fontSize: 12, fontFamily: 'Inter, sans-serif', marginTop: 16, cursor: 'pointer' }}>
              Need help finding your credentials? <span style={{ color: '#06B6D4' }}>Contact support →</span>
            </p>
          </>
        )}
      </div>
      <p style={{ color: '#1E293B', fontSize: 10, fontFamily: 'monospace', marginTop: 20 }}>↑ Web preview only — on Android TV all input is via D-pad navigation + system on-screen keyboard</p>
    </div>
  );
};

export default function DesignSpec() {
  return (
    <div style={{ background: '#000000', minHeight: '100vh', fontFamily: "'Inter', sans-serif", color: 'white' }}>
      {/* Hero */}
      <div style={{
        background: 'linear-gradient(135deg, #000000 0%, #0F172A 50%, #000000 100%)',
        borderBottom: '1px solid rgba(6,182,212,0.2)',
        padding: '80px 40px 60px',
        textAlign: 'center',
        position: 'relative',
        overflow: 'hidden',
      }}>
        <div style={{
          position: 'absolute', top: '50%', left: '50%',
          transform: 'translate(-50%, -50%)',
          width: 600, height: 600,
          borderRadius: '50%',
          background: 'radial-gradient(circle, rgba(6,182,212,0.08) 0%, transparent 70%)',
          pointerEvents: 'none',
        }} />
        <div style={{ fontWeight: 900, fontSize: 64, letterSpacing: '-0.03em', lineHeight: 1, marginBottom: 16 }}>
          <span style={{ color: '#FFFFFF' }}>hush</span>
          <span style={{ color: '#06B6D4' }}>tv.</span>
        </div>
        <p style={{ color: '#94A3B8', fontSize: 18, marginBottom: 8 }}>Design Specification Document</p>
        <p style={{ color: '#475569', fontSize: 14 }}>For Emergent — Android TV App Development</p>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center', marginTop: 32, flexWrap: 'wrap' }}>
          {['Android TV', 'Fire TV', 'IPTV Player', 'Xtream Codes'].map(tag => (
            <span key={tag} style={{
              padding: '6px 16px', borderRadius: 999,
              border: '1px solid rgba(6,182,212,0.3)',
              color: '#06B6D4', fontSize: 12, fontWeight: 600,
              background: 'rgba(6,182,212,0.08)',
            }}>{tag}</span>
          ))}
        </div>
      </div>

      <div style={{ maxWidth: 960, margin: '0 auto', padding: '60px 24px' }}>

        {/* Overview */}
        <Section title="Project Overview">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 16 }}>
            {[
              { label: 'App Name', value: 'HushTV' },
              { label: 'Platform', value: 'Android TV / Fire TV' },
              { label: 'Content', value: 'IPTV via Xtream Codes + Plex' },
              { label: 'Style', value: 'Premium dark streaming UI' },
              { label: 'Reference App', value: 'hushtv.com web player' },
              { label: 'Branding', value: 'hushtv. — white + cyan "tv." wordmark' },
            ].map(item => (
              <div key={item.label} style={{
                background: 'rgba(255,255,255,0.03)',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 12, padding: '20px 24px',
              }}>
                <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 8 }}>{item.label}</p>
                <p style={{ color: '#FFFFFF', fontWeight: 600, fontSize: 16 }}>{item.value}</p>
              </div>
            ))}
          </div>
        </Section>

        {/* App Icon */}
        <Section title="App Icon">
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 32 }}>
            <p style={{ color: '#94A3B8', marginBottom: 32, lineHeight: 1.7, fontSize: 15 }}>
              The icon must use a <strong style={{ color: '#FFFFFF' }}>pure black (#000000) background</strong> with the wordmark "hush" in white and "tv." in cyan (#06B6D4). 
              No gradients on the background. The icon must look clean and premium at all sizes — from 48px notifications to 512px store listings.
            </p>
            <div style={{ display: 'flex', gap: 40, alignItems: 'flex-end', flexWrap: 'wrap', marginBottom: 40 }}>
              <MockupCard label="512px — Store Listing">
                <AppIconMockup size={160} variant="primary" />
              </MockupCard>
              <MockupCard label="192px — Launcher">
                <AppIconMockup size={96} variant="primary" />
              </MockupCard>
              <MockupCard label="With Glow Effect (optional)">
                <AppIconMockup size={120} variant="glow" />
              </MockupCard>
              <MockupCard label="48px — Notification">
                <AppIconMockup size={48} variant="primary" />
              </MockupCard>
            </div>
            <div style={{ borderTop: '1px solid rgba(255,255,255,0.08)', paddingTop: 24 }}>
              <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 16 }}>Icon Spec</p>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12 }}>
                {[
                  ['Background', '#000000 solid black'],
                  ['Border Radius', '~22% of icon size (adaptive)'],
                  ['Primary Text', '"hush" — white, weight 900'],
                  ['Accent', '"tv." — #06B6D4 cyan, weight 900'],
                  ['Font', 'Inter Black / 900 weight'],
                  ['Glow (optional)', 'rgba(6,182,212,0.5) radial'],
                  ['Sizes Required', '48, 96, 192, 512px'],
                  ['Format', 'PNG with transparency support'],
                ].map(([k, v]) => (
                  <div key={k} style={{ background: 'rgba(0,0,0,0.4)', borderRadius: 8, padding: '12px 16px' }}>
                    <p style={{ color: '#64748B', fontSize: 11, marginBottom: 4 }}>{k}</p>
                    <p style={{ color: '#E2E8F0', fontSize: 13, fontFamily: 'monospace' }}>{v}</p>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </Section>

        {/* Color Palette */}
        <Section title="Color Palette">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: 16, marginBottom: 32 }}>
            <ColorSwatch hex="#000000" name="Pure Black" usage="App background, icon BG" />
            <ColorSwatch hex="#0F172A" name="Navy Dark" usage="Cards, sidebars, surfaces" />
            <ColorSwatch hex="#1E293B" name="Slate 800" usage="Elevated surfaces, inputs" />
            <ColorSwatch hex="#334155" name="Slate 600" usage="Borders, dividers" />
            <ColorSwatch hex="#06B6D4" name="Cyan 400 ★" usage="PRIMARY ACCENT — focus rings, logo dot, highlights" />
            <ColorSwatch hex="#3B82F6" name="Blue 500" usage="Secondary accent, buttons" />
            <ColorSwatch hex="#FFFFFF" name="White" usage="Primary text, logo 'hush'" />
            <ColorSwatch hex="#94A3B8" name="Slate 400" usage="Secondary text, labels" />
            <ColorSwatch hex="#475569" name="Slate 600" usage="Muted text, captions" />
          </div>
          <div style={{ background: 'rgba(6,182,212,0.08)', border: '1px solid rgba(6,182,212,0.3)', borderRadius: 12, padding: 20 }}>
            <p style={{ color: '#06B6D4', fontWeight: 700, fontSize: 13, marginBottom: 8 }}>⚠ CRITICAL — Cyan is the Brand</p>
            <p style={{ color: '#94A3B8', fontSize: 14, lineHeight: 1.6 }}>
              <strong style={{ color: 'white' }}>#06B6D4</strong> (Tailwind cyan-400) must be used consistently for ALL interactive focus states, the logo accent, active tab indicators, 
              progress bars, and any "selected" state. This is the single most recognizable brand color.
            </p>
          </div>
        </Section>

        {/* Typography */}
        <Section title="Typography">
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 32 }}>
            <div style={{ marginBottom: 40 }}>
              <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 24 }}>Primary Font — Inter</p>
              <div style={{ fontFamily: 'Inter, sans-serif' }}>
                <div style={{ fontSize: 64, fontWeight: 900, color: 'white', lineHeight: 1.05, marginBottom: 8, letterSpacing: '-0.03em' }}>
                  hush<span style={{ color: '#06B6D4' }}>tv.</span>
                </div>
                <p style={{ color: '#64748B', fontSize: 12, marginBottom: 32 }}>Logo / Display — Inter Black (900) · -0.03em tracking</p>

                <div style={{ fontSize: 36, fontWeight: 700, color: 'white', marginBottom: 4 }}>Movies & Series</div>
                <p style={{ color: '#64748B', fontSize: 12, marginBottom: 24 }}>Page Title — Inter Bold (700) · 36px</p>

                <div style={{ fontSize: 20, fontWeight: 600, color: 'white', marginBottom: 4 }}>Breaking Bad · S3 E7</div>
                <p style={{ color: '#64748B', fontSize: 12, marginBottom: 24 }}>Section Heading — Inter SemiBold (600) · 20px</p>

                <div style={{ fontSize: 16, fontWeight: 500, color: '#94A3B8', marginBottom: 4 }}>Drama · Crime · Thriller · 2008</div>
                <p style={{ color: '#64748B', fontSize: 12, marginBottom: 24 }}>Body / Metadata — Inter Medium (500) · 16px · #94A3B8</p>

                <div style={{ fontSize: 12, fontWeight: 600, color: '#06B6D4', letterSpacing: '0.12em', textTransform: 'uppercase', marginBottom: 4 }}>Now Playing</div>
                <p style={{ color: '#64748B', fontSize: 12 }}>Label / Tag — Inter SemiBold (600) · 12px · Cyan · 0.12em tracking</p>
              </div>
            </div>

            <div style={{ borderTop: '1px solid rgba(255,255,255,0.08)', paddingTop: 24 }}>
              <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 16 }}>Font Scale</p>
              <CodeBlock code={`// Android TV — sp units (scale-independent pixels)
Display/Logo:   56–72sp  · weight 900  · tracking -0.03em
Page Title:     32–40sp  · weight 700  · tracking -0.01em
Section Title:  22–24sp  · weight 600
Card Title:     16–18sp  · weight 600
Body Text:      14–16sp  · weight 400–500
Metadata:       12–14sp  · weight 400  · color #94A3B8
Label/Tag:      10–12sp  · weight 600  · ALL CAPS  · cyan

// Google Font Import
https://fonts.google.com/specimen/Inter
Weights needed: 400, 500, 600, 700, 900`} />
            </div>
          </div>
        </Section>

        {/* TV Focus System */}
        <Section title="TV D-Pad Navigation & Focus States">
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 32 }}>
            <p style={{ color: '#94A3B8', marginBottom: 24, lineHeight: 1.7 }}>
              This is the most critical TV-specific design element. Every focusable item must have a clear, animated focus state driven by D-pad navigation. Click items below to preview:
            </p>
            <div style={{ marginBottom: 32 }}>
              <FocusDemo />
            </div>
            <CodeBlock code={`// TV Focus State Specification
Border:          2px solid #06B6D4
Background:      rgba(6, 182, 212, 0.15)
Box Shadow:      0 0 0 3px rgba(6,182,212,0.35), 0 16px 48px rgba(0,0,0,0.8)
Scale:           1.06–1.08 (transform: scale)
Transition:      all 0.15s ease
Text Color:      #06B6D4 (when focused)

// Unfocused state
Border:          2px solid rgba(255,255,255,0.08)
Background:      rgba(255,255,255,0.04)
Text Color:      #9CA3AF

// Accessibility
- D-pad: UP/DOWN/LEFT/RIGHT arrow keys
- Select: ENTER / OK button  
- Back: BACKSPACE / Back button on remote
- All interactive elements MUST be focusable via TV remote`} />
          </div>
        </Section>

        {/* Layout System */}
        <Section title="Layout System">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16, marginBottom: 24 }}>
            {[
              { name: 'Home Screen', desc: 'Full-screen hero with account selector. No navigation bar visible. Logo top-left. Large focus cards grid.' },
              { name: 'Main Menu', desc: 'Category grid (Live TV, Movies, Series, Favorites, Search). 2-3 columns. Large D-pad targets (min 80dp height). ENTER selects category.' },
              { name: 'Browse/Grid', desc: 'Horizontal scrolling rows by category. Poster cards with title below. Cinematic backdrop on focus.' },
              { name: 'Player', desc: 'Full-screen video. Minimal overlay UI. Progress bar in cyan. Volume/controls fade in on D-pad press.' },
            ].map(item => (
              <div key={item.name} style={{
                background: 'rgba(255,255,255,0.03)',
                border: '1px solid rgba(255,255,255,0.08)',
                borderRadius: 12, padding: 20,
              }}>
                <p style={{ color: '#06B6D4', fontWeight: 700, fontSize: 14, marginBottom: 8 }}>{item.name}</p>
                <p style={{ color: '#94A3B8', fontSize: 13, lineHeight: 1.6 }}>{item.desc}</p>
              </div>
            ))}
          </div>
          <CodeBlock code={`// Spacing & Sizing — Android TV
Screen Margin:    96dp horizontal, 27dp vertical (TV safe zone = 10% of screen)
Card Min Height:  80dp (D-pad target — never smaller)
Grid Gap:         16–24dp
Corner Radius:    12–16dp on cards, 8dp on buttons
Focus Scale:      1.06–1.08x (transform: scale — hardware accelerated only)

// TV Safe Zone — IMPORTANT
Keep all content within 90% of screen (10% safe area margin on all sides)
Android TV resolution target: 1920x1080 (1080p) primary
4K (3840x2160) scaling support required`} />
        </Section>

        {/* Component Specs */}
        <Section title="Key Components">
          {/* Content Card */}
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 28, marginBottom: 16 }}>
            <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 20 }}>Content Cards (Poster Style)</p>
            <div style={{ display: 'flex', gap: 16, marginBottom: 24, flexWrap: 'wrap' }}>
              {['Movie', 'Series', 'Live'].map((type, i) => (
                <div key={type} style={{
                  width: 120, borderRadius: 12,
                  border: i === 0 ? '2px solid #06B6D4' : '2px solid rgba(255,255,255,0.1)',
                  overflow: 'hidden',
                  boxShadow: i === 0 ? '0 0 0 3px rgba(6,182,212,0.3), 0 16px 40px rgba(0,0,0,0.7)' : 'none',
                  transform: i === 0 ? 'scale(1.07)' : 'scale(1)',
                  transition: 'all 0.15s ease',
                  background: '#0F172A',
                }}>
                  <div style={{ height: 160, background: `linear-gradient(135deg, #1E293B, #0F172A)`, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <span style={{ color: '#334155', fontSize: 32 }}>{type === 'Movie' ? '🎬' : type === 'Series' ? '📺' : '📡'}</span>
                  </div>
                  <div style={{ padding: '10px 10px 12px' }}>
                    <p style={{ color: '#E2E8F0', fontWeight: 600, fontSize: 12, marginBottom: 4 }}>Content Title</p>
                    <p style={{ color: '#64748B', fontSize: 10 }}>{type}</p>
                  </div>
                </div>
              ))}
            </div>
            <CodeBlock code={`// Poster Card
Aspect Ratio:   2:3 (poster) for Movies/Series
                16:9 (thumbnail) for Live TV channels
Corner Radius:  12dp
Focused State:  scale(1.07), cyan border 2px, glow shadow
Unfocused:      scale(1.0), no border, dimmed 85%
Title:          Inter SemiBold 12–14sp, white
Metadata:       Inter Regular 10–12sp, #64748B`} />
          </div>

          {/* Navigation */}
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 28, marginBottom: 16 }}>
            <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 20 }}>Category Navigation Bar</p>
            <div style={{ display: 'flex', gap: 8, marginBottom: 24, flexWrap: 'wrap' }}>
              {['🔴 Live TV', '🎬 Movies', '📺 Series', '⭐ Favorites', '🔍 Search'].map((item, i) => (
                <div key={item} style={{
                  padding: '10px 20px', borderRadius: 8,
                  background: i === 0 ? 'rgba(6,182,212,0.15)' : 'rgba(255,255,255,0.04)',
                  border: i === 0 ? '1px solid rgba(6,182,212,0.4)' : '1px solid rgba(255,255,255,0.08)',
                  color: i === 0 ? '#06B6D4' : '#9CA3AF',
                  fontSize: 13, fontWeight: 600,
                }}>
                  {item}
                </div>
              ))}
            </div>
            <CodeBlock code={`// Top Nav / Category Bar
Height:         56–64dp
Active Tab:     background rgba(6,182,212,0.15), border cyan, text cyan
Inactive Tab:   background transparent, text #9CA3AF
Bottom Indicator: 2dp line, color #06B6D4, for active tab
Font:           Inter SemiBold 14sp`} />
          </div>

          {/* Player */}
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 28 }}>
            <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 20 }}>Video Player Overlay</p>
            <div style={{
              background: 'linear-gradient(to top, rgba(0,0,0,0.95) 0%, rgba(0,0,0,0.4) 50%, transparent 100%)',
              borderRadius: 12, padding: 20, height: 180, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end',
              border: '1px solid rgba(255,255,255,0.06)',
            }}>
              <p style={{ color: 'white', fontWeight: 700, fontSize: 18, marginBottom: 4 }}>Breaking Bad — S03E07</p>
              <p style={{ color: '#94A3B8', fontSize: 13, marginBottom: 16 }}>One Minute · Drama · 47 min</p>
              <div style={{ height: 4, background: 'rgba(255,255,255,0.15)', borderRadius: 2, marginBottom: 8 }}>
                <div style={{ height: '100%', width: '38%', background: '#06B6D4', borderRadius: 2 }} />
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <span style={{ color: '#94A3B8', fontSize: 12 }}>18:12</span>
                <span style={{ color: '#94A3B8', fontSize: 12 }}>47:00</span>
              </div>
            </div>
            <CodeBlock code={`// Player UI Overlay
Background:     gradient black-to-transparent (bottom overlay)
Progress Bar:   height 4dp, background rgba(255,255,255,0.2)
Progress Fill:  #06B6D4 (cyan), rounded ends
Controls:       auto-hide after 3 seconds of inactivity
Title Font:     Inter Bold 20–24sp
Metadata Font:  Inter Regular 14sp · #94A3B8`} />
          </div>
        </Section>

        {/* Gradient Backgrounds */}
        <Section title="Background Gradients">
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 16 }}>
            {[
              { name: 'App BG', css: 'linear-gradient(135deg, #000000 0%, #0F172A 50%, #000000 100%)', hex: '#000 → #0F172A → #000' },
              { name: 'Card Focus', css: 'linear-gradient(135deg, #1E293B, #0F172A)', hex: '#1E293B → #0F172A' },
              { name: 'Player Overlay', css: 'linear-gradient(to top, #000000 0%, rgba(0,0,0,0.4) 60%, transparent 100%)', hex: 'Black to transparent' },
              { name: 'Cyan Glow', css: 'radial-gradient(circle, rgba(6,182,212,0.15) 0%, transparent 70%)', hex: 'Cyan radial glow' },
            ].map(item => (
              <div key={item.name}>
                <div style={{ height: 80, background: item.css, borderRadius: 12, marginBottom: 12, border: '1px solid rgba(255,255,255,0.08)' }} />
                <p style={{ color: 'white', fontWeight: 600, fontSize: 13, marginBottom: 4 }}>{item.name}</p>
                <p style={{ color: '#475569', fontSize: 11, fontFamily: 'monospace' }}>{item.hex}</p>
              </div>
            ))}
          </div>
        </Section>

        {/* Animation */}
        <Section title="Motion & Animation">
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 28 }}>
            <CodeBlock code={`// Animation Timing — All transitions
Focus Scale:        transform 0.15s ease
Card Hover Scale:   transform 0.20s ease
Page Transitions:   fade 0.25s ease-in-out
Loading Skeleton:   pulse 1.5s ease-in-out infinite
Progress Update:    width transition 0.5s ease

// Keyframes
@keyframes shimmer {
  0%   { opacity: 0.4; }
  50%  { opacity: 0.8; }
  100% { opacity: 0.4; }
}

// Scroll Behavior — TV Row Navigation
Horizontal lists:   scroll-behavior: smooth
Snap:               scroll-snap-type: x mandatory
Card snap:          scroll-snap-align: start

// Performance
- Use hardware-accelerated properties only: transform, opacity
- Avoid animating: width, height, margin, padding, background-color
- Max animation duration: 300ms (TV remotes feel sluggish with longer)`} />
          </div>
        </Section>

        {/* Technical Spec */}
        <Section title="Technical Requirements">
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 28 }}>
            <div style={{ marginBottom: 0 }}>
              {[
                ['Target API Level', 'Android TV API 21+ (Lollipop) minimum, API 31+ recommended'],
                ['Resolution', '1920×1080 (1080p) primary · 3840×2160 (4K) scaled'],
                ['Safe Zone', '10% margin all sides — keep content within 90% of screen'],
                ['Remote Input', 'D-pad navigation, ENTER/OK, BACK, MENU, media keys'],
                ['Font', 'Inter — embed in app (do not rely on system font)'],
                ['Streaming Protocol', 'HLS (.m3u8) primary · MPEG-TS · MP4 direct play'],
                ['API Auth', 'Xtream Codes (username/password + host URL)'],
                ['Subtitle Format', 'SRT → converted to VTT for display'],
                ['Icon Sizes', '48×48, 96×96, 192×192, 512×512 px PNG'],
                ['Banner', '320×180px (TV launcher banner — REQUIRED for Android TV)'],
              ].map(([k, v]) => (
                <SpecRow key={k} label={k} value={v} />
              ))}
            </div>
          </div>
        </Section>

        {/* Loading Screen */}
        <Section title="Launch / Loading Screen">
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 32, marginBottom: 16 }}>
            <p style={{ color: '#94A3B8', marginBottom: 28, lineHeight: 1.7, fontSize: 15 }}>
              The launch screen is the user's <strong style={{ color: 'white' }}>first impression</strong>. It should feel cinematic and premium — pure black background, 
              centered logo that fades + scales in, a thin cyan progress bar that fills across the bottom, then a smooth fade-out into the home screen. Total duration: <strong style={{ color: '#06B6D4' }}>2.5–3 seconds</strong>.
            </p>

            {/* Live mockup */}
            <LoadingScreenMockup />

            <div style={{ marginTop: 28 }}>
              <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 16 }}>Sequence Breakdown</p>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: 12, marginBottom: 24 }}>
                {[
                  { step: '0–0.3s', label: 'Black screen', desc: 'Instant black. No flash of white.' },
                  { step: '0.3–1.0s', label: 'Logo Fade In', desc: '"hush" fades + scales from 0.8→1.0. "tv." in cyan appears 150ms later.' },
                  { step: '1.0–2.0s', label: 'Progress Bar', desc: 'Thin 2dp cyan bar sweeps left→right across the bottom edge.' },
                  { step: '2.0–2.5s', label: 'Tagline', desc: 'Optional: "Your Stream. Your Way." fades in below logo at #475569.' },
                  { step: '2.5–3.0s', label: 'Fade Out', desc: 'Entire screen fades to black, then home screen fades in.' },
                ].map(item => (
                  <div key={item.step} style={{ background: 'rgba(0,0,0,0.4)', borderRadius: 10, padding: '14px 16px', borderLeft: '2px solid rgba(6,182,212,0.4)' }}>
                    <p style={{ color: '#06B6D4', fontFamily: 'monospace', fontSize: 11, marginBottom: 4 }}>{item.step}</p>
                    <p style={{ color: 'white', fontWeight: 600, fontSize: 13, marginBottom: 4 }}>{item.label}</p>
                    <p style={{ color: '#64748B', fontSize: 12, lineHeight: 1.5 }}>{item.desc}</p>
                  </div>
                ))}
              </div>

              <CodeBlock code={`// Loading Screen — Android TV (Compose or XML)
Background:       #000000 solid (no gradients during load)
Logo:             "hush" white + "tv." cyan, Inter Black 900, centered
Logo Animation:   alpha 0→1 over 600ms, scaleX/Y 0.85→1.0, easing: FastOutSlowIn
Logo Delay:       300ms after Activity onCreate
"tv." Delay:      150ms after "hush" starts animating

// Progress Bar
Height:           2dp
Color:            #06B6D4
Position:         pinned to very bottom of screen
Width:            animates 0% → 100% over 1200ms, linear interpolator
Corner Radius:    1dp

// Tagline (optional)
Text:             "Your Stream. Your Way."
Font:             Inter Regular 400, 14sp
Color:            #475569
Delay:            appears at ~1.8s, fade in 400ms
Letter Spacing:   0.12em

// Exit Transition
Entire screen:    alpha 1→0 over 400ms, then launch MainActivity
Easing:           AccelerateInterpolator

// Android Implementation Note
Use SplashScreen API (API 31+) or a dedicated SplashActivity
Prevent white flash: set windowBackground="#000000" in splash theme
styles.xml: <item name="android:windowBackground">@color/black</item>
            <item name="android:windowIsTranslucent">false</item>`} />
            </div>
          </div>
        </Section>

        {/* DO / DON'T */}
        <Section title="Design Do's & Don'ts">
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
            <div style={{ background: 'rgba(34,197,94,0.05)', border: '1px solid rgba(34,197,94,0.2)', borderRadius: 12, padding: 24 }}>
              <p style={{ color: '#22C55E', fontWeight: 700, fontSize: 14, marginBottom: 16 }}>✓ DO</p>
              {[
                'Pure black (#000) background always',
                'Cyan (#06B6D4) for ALL focus/active states',
                'Inter font at 900 weight for logo',
                'Scale cards on focus (not just border)',
                'Large touch targets (80dp+ height)',
                'Show loading skeletons (dark shimmer)',
                'Fade out player controls after 3s',
                'TV safe zone margins (10% all sides)',
                'Hardware-accelerated animations only',
                'Test with actual TV remote',
              ].map(item => (
                <p key={item} style={{ color: '#94A3B8', fontSize: 13, marginBottom: 8, paddingLeft: 8, borderLeft: '2px solid rgba(34,197,94,0.3)' }}>
                  {item}
                </p>
              ))}
            </div>
            <div style={{ background: 'rgba(239,68,68,0.05)', border: '1px solid rgba(239,68,68,0.2)', borderRadius: 12, padding: 24 }}>
              <p style={{ color: '#EF4444', fontWeight: 700, fontSize: 14, marginBottom: 16 }}>✗ DON'T</p>
              {[
                'White or light backgrounds anywhere',
                'Any color other than cyan for focus rings',
                'Small text under 12sp on TV',
                'Hover-only states (TV has no hover)',
                'Gradients on the app icon background',
                'Animations longer than 300ms',
                'Rely on system fonts (embed Inter)',
                'Touch-style gestures (swipe etc)',
                'Modal popups without D-pad focus trap',
                'Content outside the safe zone',
              ].map(item => (
                <p key={item} style={{ color: '#94A3B8', fontSize: 13, marginBottom: 8, paddingLeft: 8, borderLeft: '2px solid rgba(239,68,68,0.3)' }}>
                  {item}
                </p>
              ))}
            </div>
          </div>
        </Section>

        {/* ═══════════════════════════════════════════════════════════ */}
        {/* HOME SCREEN */}
        {/* ═══════════════════════════════════════════════════════════ */}
        <Section title="Home Screen — Unified Content Hub">

          {/* Concept overview */}
          <div style={{ background: 'rgba(6,182,212,0.06)', border: '1px solid rgba(6,182,212,0.25)', borderRadius: 16, padding: 28, marginBottom: 24 }}>
            <p style={{ color: '#06B6D4', fontWeight: 800, fontSize: 18, marginBottom: 12, letterSpacing: '-0.01em' }}>
              The Concept — "One Screen. Everything."
            </p>
            <p style={{ color: '#94A3B8', fontSize: 15, lineHeight: 1.8, marginBottom: 0 }}>
              The home screen is a single, continuously D-pad-scrollable canvas that mixes <strong style={{ color: 'white' }}>Live TV, Movies, and Series</strong> together — 
              exactly like Netflix or Disney+ on a big screen TV, but with HushTV's pure-black premium aesthetic. Users land here after the loading screen 
              and can begin watching within <strong style={{ color: 'white' }}>2 D-pad presses</strong>. The remote's UP/DOWN navigates between rows; LEFT/RIGHT navigates cards within a row. 
              The top nav bar is always reachable by pressing UP from the first row. No touch. No swipe. No mouse. Pure remote control.
            </p>
          </div>

          {/* Interactive home screen mockup */}
          <HomeScreenMockup />

          {/* Row-by-row breakdown */}
          <div style={{ marginTop: 28 }}>
            <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 16 }}>Screen Anatomy — Row by Row</p>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(260px, 1fr))', gap: 12 }}>
              {[
                { zone: '① Top Nav Bar', color: '#06B6D4', desc: 'Fixed. Logo left · Tabs: Home / Live TV / Movies / Series / Search / My List. Active tab = cyan underline. Transparent over hero, opaque on scroll.' },
                { zone: '② Hero Billboard', color: '#3B82F6', desc: 'Full-width featured content (1920×500dp). Auto-rotates every 8s. Backdrop art + gradient overlay. Large title, genre tags, synopsis snippet, two CTA buttons: ▶ Play  +  ⊕ Add to List.' },
                { zone: '③ 🔴 Live Now', color: '#EF4444', desc: 'Horizontal scroll row. Wide 16:9 cards showing LIVE channels currently airing. Channel logo top-left, red LIVE badge, current show name, progress bar showing how far into the show it is.' },
                { zone: '④ Continue Watching', color: '#F59E0B', desc: 'Horizontal scroll. Poster cards with cyan progress bar at bottom. Shows series episode label (S2 E4) or movie % complete. Disappears if empty.' },
                { zone: '⑤ Trending This Week', color: '#8B5CF6', desc: 'Wide poster row. Top 10 numbered overlay on each card (bold white number, bottom-left). Movies + series mixed. Ranked by popularity score.' },
                { zone: '⑥ Featured Series', color: '#06B6D4', desc: 'Tall poster (2:3 ratio) cards. 6–8 visible. Genre sub-label below title. D-pad focus scale 1.08×. OK/ENTER → Series detail page.' },
                { zone: '⑦ Movies — New Arrivals', color: '#3B82F6', desc: 'Tall poster cards. "NEW" badge top-right on cards added in last 7 days. TMDB rating badge. OK/ENTER → Movie info page.' },
                { zone: '⑧ Genre Rows (Dynamic)', color: '#475569', desc: 'Multiple rows auto-generated per genre: Action, Drama, Comedy, Sci-Fi etc. Each row has a "See All →" item at end — ENTER on it navigates to full category.' },
                { zone: '⑨ Recommended For You', color: '#06B6D4', desc: 'AI-curated row based on watch history. "Because you watched Breaking Bad…" label above row. Mixed content types. Always last row before footer.' },
              ].map(item => (
                <div key={item.zone} style={{ background: 'rgba(0,0,0,0.5)', borderRadius: 10, padding: '14px 16px', borderLeft: `3px solid ${item.color}` }}>
                  <p style={{ color: item.color, fontWeight: 700, fontSize: 12, marginBottom: 6, fontFamily: 'monospace' }}>{item.zone}</p>
                  <p style={{ color: '#94A3B8', fontSize: 12, lineHeight: 1.6 }}>{item.desc}</p>
                </div>
              ))}
            </div>
          </div>

          {/* Top nav bar detail */}
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 28, marginTop: 24 }}>
            <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 20 }}>Top Navigation Bar — Interactive Preview</p>
            <TopNavMockup />
          </div>

          {/* Hero billboard detail */}
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 28, marginTop: 16 }}>
            <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 20 }}>Hero Billboard — Detail Spec</p>
            <HeroBillboardMockup />
          </div>

          {/* Content row detail */}
          <div style={{ background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: 16, padding: 28, marginTop: 16 }}>
            <p style={{ color: '#64748B', fontSize: 12, textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 20 }}>Content Rows — Live Now & Trending</p>
            <ContentRowsMockup />
          </div>

          {/* Code spec */}
          <CodeBlock code={`// HOME SCREEN — Android TV Layout Spec
// ⚠ ALL NAVIGATION IS D-PAD ONLY. No touch, no swipe, no mouse.
// Remote: UP/DOWN = row navigation · LEFT/RIGHT = card navigation · OK/ENTER = select · BACK = go back

Screen:           1920×1080px (1080p primary). Content within 90% safe zone (10% margin all sides).
Background:       #000000 solid black
Scroll:           D-pad DOWN moves focus row-by-row. Page scrolls to keep focused row visible.
                  RecyclerView (vertical) containing horizontal RecyclerViews per row.
Nav Bar Height:   72dp. Reaches focus when user presses UP from hero row.
Hero Height:      500dp (≈46% of screen height)
Row Card Height:  Poster (2:3): 220dp · Live/Wide (16:9): 160dp
Row Gap:          32dp between rows
Row Label:        Inter SemiBold 18sp, white, left-aligned at safe zone margin (96dp from edge)
"See All" Item:   Last item in each row. ENTER on it → full category browse screen.
                  Color: #06B6D4. Focus: same scale + border as cards.

// TOP NAV BAR — D-Pad Behavior
Reach:            User presses UP from first content row
Tab Focus:        LEFT/RIGHT moves between tabs. ENTER selects.
Active Tab:       White text, 3dp cyan underline (#06B6D4)
Focused Tab:      Scale 1.04×, rgba(6,182,212,0.12) bg highlight
Inactive Tab:     #64748B text, no border
Tab min width:    120dp (large enough for TV remote accuracy)
Logo:             "hushtv." Inter 900, 28sp, left-anchored. Not focusable.
Profile icon:     Right side. ENTER → account/settings.

// HERO BILLBOARD — D-Pad Behavior
Backdrop:         Full-bleed art image. Crossfade on slide change.
Overlay:          gradient: transparent 0% → rgba(0,0,0,0.85) 70% (bottom)
                  + left vignette: rgba(0,0,0,0.7) on left 40% (keeps text readable)
Title:            Inter Black 900, 52–60sp, white, max 2 lines, ellipsis after
Genre Tags:       Pill badges, Inter 600 12sp, rgba(255,255,255,0.15) bg, not focusable
Synopsis:         Inter 400 15sp, #94A3B8, max 2 lines, auto-hidden on focus
CTA Primary:      "▶ Play" — #FFFFFF bg, #000 text, 56dp height, 180dp wide min
CTA Secondary:    "+ My List" — rgba(255,255,255,0.12) bg, white text, same height
CTA Focus order:  LEFT/RIGHT between Play and My List. DOWN → first content row.
Auto-rotate:      Every 8000ms when no focus is in hero zone. Stops on focus.
Progress Dots:    Bottom-right, 8dp each. Cyan = current. ENTER on dots has no action.

// LIVE NOW ROW — Android TV Cards
Card size:        16:9 ratio, min 280×158dp
LIVE Badge:       Top-left corner. Red #EF4444 bg, "● LIVE" text, blinking dot animation.
Channel Logo:     Top-right, max 48×28dp, white-tinted (colorFilter: WHITE with SRC_IN)
Progress Bar:     Cyan 3dp bar at very bottom of card showing % through current broadcast
Focused state:    scale(1.07), 2px #06B6D4 border, glow shadow, show name animates in below
ENTER action:     Immediate playback — no detail page for live TV

// TRENDING ROW — Numbered (Netflix style)
Ghost Number:     Inter Black 900, 96sp, rgba(255,255,255,0.08), bottom-left of card
                  Offset left so number bleeds behind adjacent card
Card:             2:3 poster, min 140×210dp
ENTER action:     Navigate to content detail page (Movie Info or Series Detail)

// CONTINUE WATCHING ROW
Card:             16:9, min 240×135dp
Progress Bar:     Cyan, 3dp, overlaid flush at very bottom edge of card thumbnail
Episode Label:    "S2 E4 · 42 min left" — shown below card · Inter 500 11sp, #94A3B8
On Focus:         Show "▶ Resume" pill overlay (cyan bg, black text) centered on card
ENTER action:     Resume playback from saved progress position`} />
        </Section>

        {/* ═══════════════════════════════════════════════════════════ */}
        {/* LOGIN SCREEN */}
        {/* ═══════════════════════════════════════════════════════════ */}
        <Section title="Login / Account Setup Screen">

          <div style={{ background: 'rgba(6,182,212,0.06)', border: '1px solid rgba(6,182,212,0.25)', borderRadius: 16, padding: 28, marginBottom: 24 }}>
            <p style={{ color: '#06B6D4', fontWeight: 800, fontSize: 18, marginBottom: 12 }}>
              The Concept — "Cinema-Grade Onboarding"
            </p>
            <p style={{ color: '#94A3B8', fontSize: 15, lineHeight: 1.8 }}>
              The login screen must feel like opening night at a cinema — pure black, centred logo, minimal inputs, maximum confidence. 
              No clutter. No distractions. Just the brand and the bare minimum fields to get the user watching in under 30 seconds. 
              Think Apple TV+ first-launch meets a high-end streaming portal.
            </p>
          </div>

          <LoginScreenMockup />

          <div style={{ marginTop: 28, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))', gap: 12 }}>
            {[
              { label: 'Background', value: 'Pure #000000 — no gradients, no noise, no texture. Absolute black.' },
              { label: 'Logo Position', value: 'Top-center. 48dp from top edge. "hushtv." at 40sp, Inter 900.' },
              { label: 'Tagline', value: '"Your Stream. Your Way." — 13sp, Inter 400, #475569, below logo, 8dp gap.' },
              { label: 'Card Container', value: 'Centered card, max-width 400dp. Border: 1px rgba(255,255,255,0.08). Background: rgba(255,255,255,0.03). Radius: 16dp. Padding: 40dp.' },
              { label: 'Input Fields', value: 'Height 64dp (TV legibility). Background #0F172A. 2px cyan border + glow on D-pad focus. ENTER opens system keyboard.' },
              { label: 'Connect Button', value: 'Full width 64dp. Cyan #06B6D4 fill, black text. Inter 700 18sp. ENTER triggers. Scale 0.97× on press.' },
              { label: 'Error State', value: 'Field border → #EF4444. Shake animation ±8dp × 3 cycles. Error text 14sp red below field. Focus stays on problem field.' },
              { label: 'Success State', value: 'Button → green + "✓ Connected". Hold 800ms. Entire screen crossfades to Home Screen.' },
              { label: 'Loading State', value: 'Circular ProgressBar (cyan, 28dp) replaces button text. Button stays full-width — no layout shift.' },
              { label: 'Step Indicator', value: '3 dots above card. Active = cyan pill (24dp wide). Complete = white dot. Pending = #1E293B dot. D-pad navigates forward, BACK goes to previous step.' },
            ].map(({ label, value }) => (
              <div key={label} style={{ background: 'rgba(0,0,0,0.4)', borderRadius: 10, padding: '14px 16px', borderLeft: '2px solid rgba(6,182,212,0.3)' }}>
                <p style={{ color: '#475569', fontSize: 11, marginBottom: 5, textTransform: 'uppercase', letterSpacing: '0.08em' }}>{label}</p>
                <p style={{ color: '#E2E8F0', fontSize: 13, lineHeight: 1.55 }}>{value}</p>
              </div>
            ))}
          </div>

          <CodeBlock code={`// LOGIN / ACCOUNT SETUP SCREEN — Android TV Spec
// ⚠ INPUT IS VIA TV ON-SCREEN KEYBOARD OR PHYSICAL KEYBOARD. No touch.
// ENTER on a field → opens Android TV system keyboard. BACK → closes keyboard.

Background:       #000000 solid. windowBackground="#000000" set in theme to prevent white flash.
Layout:           Full 1920×1080. Single centered column. Vertically centered with ConstraintLayout.

// LOGO BLOCK
Logo:             "hushtv." Inter Black 900, 40sp. Centered above card.
Logo Color:       "hush" #FFFFFF · "tv." #06B6D4
Tagline:          "Your Stream. Your Way." · Inter 400 · 13sp · #475569 · letter-spacing 0.08em
Logo → Card Gap: 32dp

// INPUT CARD
Width:            640dp (wide enough to be readable from couch at 3m distance)
Background:       rgba(255,255,255,0.03)
Border:           1px solid rgba(255,255,255,0.08)
Border Radius:    16dp
Padding:          40dp all sides

// INPUT FIELDS (3 fields: Host URL · Username · Password)
// IMPORTANT: On Android TV, EditText fields use the system IME keyboard.
// Field must look clearly focused so user knows which field is active.
Height:           64dp (larger than mobile — TV viewing distance requires bigger targets)
Background:       #0F172A
Border default:   1px solid #1E293B
Border focused:   2px solid #06B6D4 + box-shadow 0 0 0 4px rgba(6,182,212,0.2)
Radius:           10dp
Font:             Inter 400 · 18sp white (larger sp for TV distance legibility)
Label above:      Inter 600 · 13sp · #94A3B8 · always visible (not floating — TV legibility)
Placeholder:      #334155

// FIELDS
Field 1 — Server / Host URL:
  Placeholder:    "https://your-iptv-host.com"
  InputType:      TYPE_TEXT_VARIATION_URI
  Validate:       must start with http:// or https://

Field 2 — Username:
  Placeholder:    "Username"
  InputType:      TYPE_CLASS_TEXT

Field 3 — Password:
  Placeholder:    "Password"
  InputType:      TYPE_TEXT_VARIATION_PASSWORD
  Toggle:         D-pad-focusable eye icon button RIGHT of field (not tap — ENTER to toggle)

// D-PAD FOCUS ORDER (CRITICAL for TV)
Focus chain:      Field 1 (Host) → Field 2 (Username) → Field 3 (Password) → Connect Button → Help Link
ENTER on field:   Opens Android TV on-screen keyboard. User types, presses Done/ENTER → closes keyboard, moves to next field.
BACK from field:  Closes keyboard without changing field focus.
Focus ring:       2px solid #06B6D4, outer glow 0 0 0 4px rgba(6,182,212,0.2)
Min focus target: 64dp height. Never smaller — TV remote is imprecise.

// CONNECT BUTTON
Text:             "Connect  →"
Height:           64dp
Width:            100% of card width
Background:       #06B6D4
Text Color:       #000000 (black on cyan — maximum contrast on TV from 3m)
Font:             Inter Bold 700 · 18sp
Radius:           10dp
Margin Top:       24dp from last field
ENTER press:      scale(0.97) 80ms → triggers form submission

// STATES
Loading:          ProgressBar (circular, cyan, 28dp) replaces text. Button stays same size.
Success:          Button BG → #22C55E, text → "✓ Connected", hold 800ms, then transition to Home
Error:            Field borders → #EF4444. Shake animation (TranslateAnimation ±8dp, 3 cycles, 250ms).
                  Error text 14sp #EF4444 appears below affected field.

// STEP INDICATOR
Dots:             3 dots, 10dp each, 8dp gap, centered above card
Active step:      #06B6D4 filled, width 24dp (pill shape)
Complete step:    #FFFFFF filled
Pending step:     #1E293B filled

// SECONDARY HELP LINK
Text:             "Need help? Contact support →"
Font:             Inter 400 · 14sp · #334155
Focused:          color #06B6D4, underline
ENTER:            Opens support URL in browser (if available) or shows QR code overlay

// ANDROID TV KEYBOARD NOTE
The system on-screen keyboard (Leanback keyboard) appears when EditText gains focus via ENTER.
Do NOT use a custom keyboard — use the system one. Ensure the layout scrolls up so
the focused field remains visible above the keyboard (WindowSoftInputMode: adjustPan).`} />

        </Section>

        {/* Footer */}
        <div style={{ textAlign: 'center', borderTop: '1px solid rgba(255,255,255,0.06)', paddingTop: 48, marginTop: 24 }}>
          <div style={{ fontWeight: 900, fontSize: 40, letterSpacing: '-0.03em', marginBottom: 12 }}>
            <span style={{ color: 'white' }}>hush</span>
            <span style={{ color: '#06B6D4' }}>tv.</span>
          </div>
          <p style={{ color: '#334155', fontSize: 13 }}>Design Spec · Confidential · For Emergent Development Team</p>
          <p style={{ color: '#1E293B', fontSize: 12, marginTop: 8 }}>Reference: hushtv.com web player</p>
        </div>

      </div>
    </div>
  );
}