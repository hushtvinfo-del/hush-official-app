import React, { useRef, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Play, Pause, Volume2, VolumeX, Maximize } from 'lucide-react';

export default function TVPlayer() {
  const navigate = useNavigate();
  const videoRef = useRef(null);
  const playerRef = useRef(null);
  const controlsTimeout = useRef(null);
  const urlParams = new URLSearchParams(window.location.search);

  const channelUrl = decodeURIComponent(urlParams.get('channelUrl') || '');
  const channelName = decodeURIComponent(urlParams.get('channelName') || '');
  const containerExtension = urlParams.get('containerExtension');
  const coverImage = urlParams.get('coverImage') ? decodeURIComponent(urlParams.get('coverImage')) : '';

  const [showControls, setShowControls] = useState(true);
  const [isPlaying, setIsPlaying] = useState(true);
  const [isMuted, setIsMuted] = useState(false);
  const [progress, setProgress] = useState(0);
  const [duration, setDuration] = useState(0);
  const isHLS = containerExtension === 'm3u8' || containerExtension === 'ts' || !containerExtension;

  const showControlsTemporarily = () => {
    setShowControls(true);
    clearTimeout(controlsTimeout.current);
    controlsTimeout.current = setTimeout(() => setShowControls(false), 4000);
  };

  useEffect(() => {
    showControlsTemporarily();
    return () => clearTimeout(controlsTimeout.current);
  }, []);

  useEffect(() => {
    if (!channelUrl) return;

    const cssId = 'videojs-css';
    if (!document.getElementById(cssId)) {
      const link = document.createElement('link');
      link.id = cssId; link.rel = 'stylesheet';
      link.href = 'https://vjs.zencdn.net/8.10.0/video-js.css';
      document.head.appendChild(link);
    }

    const initPlayer = () => {
      if (!videoRef.current || playerRef.current) return;
      const player = window.videojs(videoRef.current, {
        autoplay: true, controls: false, responsive: true, fluid: true,
        preload: 'auto', playsinline: true,
        html5: { vhs: { overrideNative: true, withCredentials: false } }
      });
      playerRef.current = player;

      if (isHLS) {
        player.src({ src: channelUrl, type: 'application/x-mpegURL' });
      } else {
        player.src(channelUrl);
      }

      player.on('timeupdate', () => {
        setProgress(player.currentTime());
        setDuration(player.duration() || 0);
      });
      player.on('play', () => setIsPlaying(true));
      player.on('pause', () => setIsPlaying(false));
    };

    if (window.videojs) {
      initPlayer();
    } else {
      const scriptId = 'videojs-script';
      if (!document.getElementById(scriptId)) {
        const script = document.createElement('script');
        script.id = scriptId;
        script.src = 'https://vjs.zencdn.net/8.10.0/video.min.js';
        script.async = true;
        document.body.appendChild(script);
        script.onload = initPlayer;
      }
    }

    return () => {
      if (playerRef.current && !playerRef.current.isDisposed()) {
        playerRef.current.dispose();
        playerRef.current = null;
      }
    };
  }, [channelUrl, isHLS]);

  const handleKeyDown = (e) => {
    showControlsTemporarily();
    const player = playerRef.current;
    if (!player) return;

    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      if (player.paused()) { player.play(); } else { player.pause(); }
    } else if (e.key === 'ArrowRight') {
      e.preventDefault();
      player.currentTime(Math.min(player.currentTime() + 10, player.duration()));
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      player.currentTime(Math.max(player.currentTime() - 10, 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      player.volume(Math.min(player.volume() + 0.1, 1));
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      player.volume(Math.max(player.volume() - 0.1, 0));
    } else if (e.key === 'Backspace' || e.key === 'Escape') {
      navigate(-1);
    }
  };

  const togglePlay = () => {
    const player = playerRef.current;
    if (!player) return;
    if (player.paused()) { player.play(); } else { player.pause(); }
    showControlsTemporarily();
  };

  const toggleMute = () => {
    const player = playerRef.current;
    if (!player) return;
    player.muted(!player.muted());
    setIsMuted(!isMuted);
    showControlsTemporarily();
  };

  const formatTime = (secs) => {
    if (!secs || isNaN(secs)) return '0:00';
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
  };

  const progressPct = duration > 0 ? (progress / duration) * 100 : 0;

  return (
    <div
      className="fixed inset-0 bg-black flex items-center justify-center"
      onKeyDown={handleKeyDown}
      onMouseMove={showControlsTemporarily}
      onClick={showControlsTemporarily}
      tabIndex={0}
      style={{ outline: 'none' }}
      autoFocus
    >
      {/* Video */}
      <div data-vjs-player className="w-full h-full">
        <video
          ref={videoRef}
          className="video-js w-full h-full"
          playsInline
          autoPlay
          style={{ width: '100%', height: '100%', objectFit: 'contain' }}
        />
      </div>

      {/* Controls overlay */}
      <div
        className="absolute inset-0 flex flex-col justify-between"
        style={{
          opacity: showControls ? 1 : 0,
          transition: 'opacity 0.4s ease',
          pointerEvents: showControls ? 'auto' : 'none',
          background: 'linear-gradient(to bottom, rgba(0,0,0,0.7) 0%, transparent 30%, transparent 70%, rgba(0,0,0,0.85) 100%)'
        }}
      >
        {/* Top */}
        <div className="flex items-center gap-4 px-12 pt-8">
          <button
            onClick={() => navigate(-1)}
            className="tv-focus p-3 rounded-full"
            style={{ background: 'rgba(0,0,0,0.5)' }}
          >
            <ArrowLeft className="w-7 h-7 text-white" />
          </button>
          <div>
            <h1 className="text-white text-3xl font-bold">{channelName}</h1>
            {isHLS && <span className="text-red-400 text-sm font-semibold uppercase tracking-wider">● LIVE</span>}
          </div>
        </div>

        {/* Bottom controls */}
        <div className="px-12 pb-10">
          {/* Progress bar (VOD only) */}
          {duration > 0 && (
            <div className="mb-4">
              <div className="w-full h-1.5 rounded-full" style={{ background: 'rgba(255,255,255,0.25)' }}>
                <div className="h-full rounded-full" style={{ width: `${progressPct}%`, background: '#06b6d4' }} />
              </div>
              <div className="flex justify-between text-gray-400 text-sm mt-1">
                <span>{formatTime(progress)}</span>
                <span>{formatTime(duration)}</span>
              </div>
            </div>
          )}

          <div className="flex items-center gap-6">
            <button onClick={togglePlay} className="tv-focus p-4 rounded-full" style={{ background: 'rgba(255,255,255,0.15)' }}>
              {isPlaying ? <Pause className="w-8 h-8 text-white" /> : <Play className="w-8 h-8 text-white ml-1" />}
            </button>
            <button onClick={toggleMute} className="tv-focus p-4 rounded-full" style={{ background: 'rgba(255,255,255,0.15)' }}>
              {isMuted ? <VolumeX className="w-7 h-7 text-white" /> : <Volume2 className="w-7 h-7 text-white" />}
            </button>
            <div className="ml-auto text-gray-300 text-lg">
              ← → Skip 10s &nbsp;|&nbsp; ↑ ↓ Volume &nbsp;|&nbsp; Back = Exit
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}