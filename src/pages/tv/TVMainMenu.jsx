import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { base44 } from '@/api/base44Client';
import { useQuery } from '@tanstack/react-query';
import { HushTVLogo } from './TVApp';
import { Tv, Film, Clapperboard, Star, Search, Calendar, ArrowLeft } from 'lucide-react';
import { createPageUrl } from '@/utils';

const getPlaylistFromLocal = (playlistId) => {
  try {
    const stored = JSON.parse(localStorage.getItem('playlists') || '[]');
    return stored.find(p => p.id === playlistId);
  } catch { return null; }
};

const MENU_ITEMS = [
  { name: 'Live TV', icon: Tv, type: 'live', color: '#3b82f6', glow: 'rgba(59,130,246,0.4)' },
  { name: 'Movies', icon: Film, type: 'movie', color: '#8b5cf6', glow: 'rgba(139,92,246,0.4)' },
  { name: 'Series', icon: Clapperboard, type: 'series', color: '#ec4899', glow: 'rgba(236,72,153,0.4)' },
  { name: 'Favorites', icon: Star, type: 'favorites', color: '#f59e0b', glow: 'rgba(245,158,11,0.4)' },
  { name: 'Search', icon: Search, type: 'search', color: '#10b981', glow: 'rgba(16,185,129,0.4)' },
];

export default function TVMainMenu() {
  const navigate = useNavigate();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');
  const [focused, setFocused] = useState(0);
  const btnRefs = useRef([]);

  const playlist = getPlaylistFromLocal(playlistId);

  const { data: accountInfo } = useQuery({
    queryKey: ['accountInfo', playlistId],
    queryFn: async () => {
      const { data } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host,
        username: playlist.username,
        password: playlist.password,
        params: {}
      });
      return data.user_info;
    },
    enabled: !!playlist,
  });

  const expiryTimestamp = accountInfo?.exp_date ? parseInt(accountInfo.exp_date) : null;
  const expiryDate = expiryTimestamp ? new Date(expiryTimestamp * 1000) : null;
  const daysLeft = expiryDate ? Math.ceil((expiryDate - new Date()) / (1000 * 60 * 60 * 24)) : null;
  const expiryDateStr = expiryDate ? expiryDate.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' }) : null;

  useEffect(() => {
    if (btnRefs.current[focused]) btnRefs.current[focused].focus();
  }, [focused]);

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
      e.preventDefault();
      setFocused(f => Math.min(f + 1, MENU_ITEMS.length - 1 + 1)); // +1 for back button
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
      e.preventDefault();
      setFocused(f => Math.max(f - 1, 0));
    } else if (e.key === 'Enter') {
      if (btnRefs.current[focused]) btnRefs.current[focused].click();
    }
  };

  const handleMenuClick = (item) => {
    const base = `/tv/browse?playlistId=${playlistId}&type=${item.type}`;
    navigate(base);
  };

  return (
    <div
      className="min-h-screen flex flex-col"
      onKeyDown={handleKeyDown}
      tabIndex={-1}
      style={{ background: 'linear-gradient(135deg, #050d1a 0%, #000 60%)' }}
    >
      {/* Top Bar */}
      <div className="flex items-center justify-between px-16 pt-10 pb-6">
        <HushTVLogo size="lg" />
        <div className="text-right">
          <div className="text-white text-2xl font-bold">{accountInfo?.username || playlist?.name || 'User'}</div>
          {expiryDateStr && (
            <div className="flex items-center gap-2 justify-end mt-1">
              <Calendar className="w-4 h-4 text-gray-400" />
              <span className="text-gray-400 text-base">Expires {expiryDateStr}</span>
              {daysLeft !== null && daysLeft <= 7 && daysLeft > 0 && (
                <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-yellow-500 text-black">{daysLeft}d left</span>
              )}
              {daysLeft !== null && daysLeft <= 0 && (
                <span className="px-2 py-0.5 rounded-full text-xs font-bold bg-red-600 text-white">Expired</span>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Hero section */}
      <div className="px-16 mb-12">
        <div className="h-px w-full" style={{ background: 'linear-gradient(90deg, rgba(6,182,212,0.5) 0%, transparent 100%)' }} />
      </div>

      {/* Menu Grid */}
      <div className="flex-1 flex items-center px-16">
        <div className="w-full">
          <h2 className="text-gray-400 text-xl font-semibold uppercase tracking-widest mb-8">What would you like to watch?</h2>
          <div className="grid grid-cols-5 gap-6">
            {MENU_ITEMS.map((item, i) => {
              const Icon = item.icon;
              return (
                <button
                  key={item.name}
                  ref={el => btnRefs.current[i] = el}
                  onClick={() => handleMenuClick(item)}
                  onFocus={() => setFocused(i)}
                  className="tv-card tv-focus group flex flex-col items-center justify-center py-12 rounded-2xl relative overflow-hidden"
                  style={{
                    background: `rgba(255,255,255,0.05)`,
                    border: `1px solid rgba(255,255,255,0.1)`,
                  }}
                >
                  <div className="absolute inset-0 opacity-0 group-focus:opacity-100 transition-opacity"
                    style={{ background: `radial-gradient(ellipse at center, ${item.glow} 0%, transparent 70%)` }} />
                  <Icon
                    className="w-14 h-14 mb-5 relative z-10"
                    style={{ color: item.color }}
                  />
                  <span className="text-white text-xl font-bold relative z-10">{item.name}</span>
                </button>
              );
            })}
          </div>

          {/* Back button */}
          <button
            ref={el => btnRefs.current[MENU_ITEMS.length] = el}
            onClick={() => navigate('/tv')}
            onFocus={() => setFocused(MENU_ITEMS.length)}
            className="tv-focus mt-10 flex items-center gap-2 text-gray-500 hover:text-white text-lg px-4 py-2 rounded-lg"
          >
            <ArrowLeft className="w-5 h-5" />
            Switch Account
          </button>
        </div>
      </div>
    </div>
  );
}