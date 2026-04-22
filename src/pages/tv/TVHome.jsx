import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { HushTVLogo } from './TVApp';
import { Play, Plus } from 'lucide-react';

export default function TVHome() {
  const navigate = useNavigate();
  const [playlists, setPlaylists] = useState([]);
  const [focused, setFocused] = useState(0);
  const btnRefs = useRef([]);

  useEffect(() => {
    try {
      const stored = JSON.parse(localStorage.getItem('playlists') || '[]');
      setPlaylists(stored);
    } catch {}
  }, []);

  useEffect(() => {
    if (btnRefs.current[focused]) {
      btnRefs.current[focused].focus();
    }
  }, [focused]);

  const totalItems = playlists.length + 1; // accounts + add button

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowDown' || e.key === 'ArrowRight') {
      e.preventDefault();
      setFocused(f => Math.min(f + 1, totalItems - 1));
    } else if (e.key === 'ArrowUp' || e.key === 'ArrowLeft') {
      e.preventDefault();
      setFocused(f => Math.max(f - 1, 0));
    } else if (e.key === 'Enter') {
      if (btnRefs.current[focused]) btnRefs.current[focused].click();
    }
  };

  return (
    <div
      className="min-h-screen flex flex-col items-center justify-center relative overflow-hidden"
      onKeyDown={handleKeyDown}
      tabIndex={-1}
      style={{ background: 'radial-gradient(ellipse at 50% 30%, #0f2657 0%, #000 70%)' }}
    >
      {/* Background glow */}
      <div className="absolute inset-0 overflow-hidden pointer-events-none">
        <div className="absolute top-1/4 left-1/2 -translate-x-1/2 w-[800px] h-[400px] rounded-full opacity-20"
          style={{ background: 'radial-gradient(ellipse, #06b6d4 0%, transparent 70%)' }} />
      </div>

      <div className="relative z-10 flex flex-col items-center w-full max-w-4xl px-16">
        <HushTVLogo size="xl" />
        <p className="text-gray-400 text-2xl mt-4 mb-16 tracking-wide">Official Android TV App</p>

        <div className="w-full space-y-4">
          {playlists.map((playlist, i) => (
            <button
              key={playlist.id}
              ref={el => btnRefs.current[i] = el}
              className="tv-card tv-focus w-full flex items-center gap-6 px-8 py-6 rounded-2xl text-left"
              style={{ background: 'rgba(255,255,255,0.07)', border: '1px solid rgba(255,255,255,0.12)' }}
              onClick={() => navigate(`/tv/menu?playlistId=${playlist.id}`)}
              onFocus={() => setFocused(i)}
            >
              <div className="w-16 h-16 rounded-full flex items-center justify-center flex-shrink-0"
                style={{ background: 'linear-gradient(135deg, #3b82f6, #06b6d4)' }}>
                <Play className="w-7 h-7 text-white ml-1" />
              </div>
              <div>
                <div className="text-white text-2xl font-bold">{playlist.name}</div>
                <div className="text-gray-400 text-lg mt-1">@{playlist.username}</div>
              </div>
              <div className="ml-auto text-cyan-400 text-lg font-semibold">Watch Now →</div>
            </button>
          ))}

          <button
            ref={el => btnRefs.current[playlists.length] = el}
            className="tv-card tv-focus w-full flex items-center gap-6 px-8 py-6 rounded-2xl text-left"
            style={{ border: '2px dashed rgba(6,182,212,0.4)', background: 'rgba(6,182,212,0.05)' }}
            onClick={() => navigate('/tv/add-account')}
            onFocus={() => setFocused(playlists.length)}
          >
            <div className="w-16 h-16 rounded-full flex items-center justify-center flex-shrink-0"
              style={{ background: 'rgba(6,182,212,0.2)', border: '2px solid rgba(6,182,212,0.4)' }}>
              <Plus className="w-7 h-7 text-cyan-400" />
            </div>
            <div className="text-cyan-400 text-2xl font-bold">Add Account</div>
          </button>
        </div>
      </div>
    </div>
  );
}