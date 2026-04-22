import React, { useState } from 'react';

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
        <span style={{ color: '#06B6D4' }}>+</span>
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
          <span style={{ color: '#06B6D4' }}>+</span>
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
              { label: 'App Name', value: 'Hush+' },
              { label: 'Platform', value: 'Android TV / Fire TV' },
              { label: 'Content', value: 'IPTV via Xtream Codes + Plex' },
              { label: 'Style', value: 'Premium dark streaming UI' },
              { label: 'Reference App', value: 'hushtv.com web player' },
              { label: 'Branding', value: 'hush + cyan accent dot' },
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
              The icon must use a <strong style={{ color: '#FFFFFF' }}>pure black (#000000) background</strong> with the wordmark "hush" in white and "+" in cyan (#06B6D4). 
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
                  ['Accent', '"+" — #06B6D4 cyan, weight 900'],
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
                  hush<span style={{ color: '#06B6D4' }}>+</span>
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
              { name: 'Main Menu', desc: 'Category grid (Live TV, Movies, Series, Favorites, Search). 2-3 columns. Large touch targets (min 80dp height).' },
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
Screen Margin:    48dp horizontal, 27dp vertical (TV safe zone = 10% of screen)
Card Min Height:  80dp (touch target)
Grid Gap:         16–24dp
Corner Radius:    12–16dp on cards, 8dp on buttons
Focus Scale:      1.06–1.08x (CSS: transform: scale(1.07))

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
              { name: 'Card Hover', css: 'linear-gradient(135deg, #1E293B, #0F172A)', hex: '#1E293B → #0F172A' },
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

        {/* Footer */}
        <div style={{ textAlign: 'center', borderTop: '1px solid rgba(255,255,255,0.06)', paddingTop: 48, marginTop: 24 }}>
          <div style={{ fontWeight: 900, fontSize: 40, letterSpacing: '-0.03em', marginBottom: 12 }}>
            <span style={{ color: 'white' }}>hush</span>
            <span style={{ color: '#06B6D4' }}>+</span>
          </div>
          <p style={{ color: '#334155', fontSize: 13 }}>Design Spec · Confidential · For Emergent Development Team</p>
          <p style={{ color: '#1E293B', fontSize: 12, marginTop: 8 }}>Reference: hushtv.com web player</p>
        </div>

      </div>
    </div>
  );
}