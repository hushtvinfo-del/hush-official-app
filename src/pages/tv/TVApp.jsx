import React, { useState, useEffect } from 'react';
import { Routes, Route, useNavigate, useLocation } from 'react-router-dom';
import TVHome from './TVHome';
import TVAddAccount from './TVAddAccount';
import TVMainMenu from './TVMainMenu';
import TVBrowse from './TVBrowse';
import TVPlayer from './TVPlayer';

// HushTV Logo
export const HushTVLogo = ({ size = 'md' }) => {
  const sizes = { sm: 'text-2xl', md: 'text-4xl', lg: 'text-6xl', xl: 'text-8xl' };
  return (
    <div className={`font-black ${sizes[size]} tracking-tight`}>
      <span className="text-white">hush</span>
      <span className="text-cyan-400">tv.</span>
    </div>
  );
};

export default function TVApp() {
  return (
    <div className="min-h-screen bg-black text-white overflow-hidden" style={{ fontFamily: "'Inter', sans-serif" }}>
      <style>{`
        * { box-sizing: border-box; }
        .tv-focus:focus {
          outline: 3px solid #06b6d4;
          outline-offset: 2px;
          transform: scale(1.05);
        }
        .tv-focus {
          transition: transform 0.15s ease, outline 0.1s ease, box-shadow 0.15s ease;
        }
        .tv-focus:focus {
          box-shadow: 0 0 0 3px rgba(6,182,212,0.4), 0 8px 32px rgba(6,182,212,0.3);
        }
        .tv-card:focus {
          outline: 3px solid #06b6d4;
          transform: scale(1.08);
          z-index: 10;
          box-shadow: 0 0 0 3px rgba(6,182,212,0.5), 0 16px 48px rgba(0,0,0,0.8);
        }
        .tv-card {
          transition: transform 0.2s ease, box-shadow 0.2s ease;
        }
        .scrollbar-hide::-webkit-scrollbar { display: none; }
        .scrollbar-hide { -ms-overflow-style: none; scrollbar-width: none; }
      `}</style>
      <Routes>
        <Route path="/tv" element={<TVHome />} />
        <Route path="/tv/add-account" element={<TVAddAccount />} />
        <Route path="/tv/menu" element={<TVMainMenu />} />
        <Route path="/tv/browse" element={<TVBrowse />} />
        <Route path="/tv/player" element={<TVPlayer />} />
        <Route path="*" element={<TVHome />} />
      </Routes>
    </div>
  );
}