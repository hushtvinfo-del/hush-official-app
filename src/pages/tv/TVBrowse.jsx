import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { base44 } from '@/api/base44Client';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Search, X, Loader2, Tv, Film, Star } from 'lucide-react';

const getPlaylistFromLocal = (playlistId) => {
  try {
    return JSON.parse(localStorage.getItem('playlists') || '[]').find(p => p.id === playlistId);
  } catch { return null; }
};

// A single focusable content card
function TVCard({ item, onSelect, isFocused, cardRef }) {
  return (
    <button
      ref={cardRef}
      className="tv-card tv-focus flex-shrink-0 relative rounded-xl overflow-hidden text-left"
      style={{ width: '200px', height: '300px', border: isFocused ? '3px solid #06b6d4' : '2px solid rgba(255,255,255,0.1)' }}
      onClick={() => onSelect(item)}
    >
      {item.cover || item.stream_icon ? (
        <img
          src={item.cover || item.stream_icon}
          alt={item.name}
          className="w-full h-full object-cover"
          onError={e => { e.target.style.display = 'none'; e.target.nextSibling.style.display = 'flex'; }}
        />
      ) : null}
      <div className="w-full h-full absolute inset-0 items-center justify-center bg-gray-900"
        style={{ display: (item.cover || item.stream_icon) ? 'none' : 'flex' }}>
        <Film className="w-12 h-12 text-gray-600" />
      </div>
      <div className="absolute inset-0 bg-gradient-to-t from-black via-transparent to-transparent" />
      <div className="absolute bottom-0 left-0 right-0 p-3">
        <p className="text-white text-sm font-semibold leading-tight line-clamp-2">{item.name}</p>
        {item.rating && (
          <div className="flex items-center gap-1 mt-1">
            <Star className="w-3 h-3 text-yellow-400" />
            <span className="text-yellow-400 text-xs">{parseFloat(item.rating).toFixed(1)}</span>
          </div>
        )}
      </div>
    </button>
  );
}

// Horizontal row with keyboard nav
function TVRow({ title, items, onSelect, rowIndex, isActiveRow, onRowFocus, initialFocusCol }) {
  const [colFocus, setColFocus] = useState(initialFocusCol || 0);
  const cardRefs = useRef([]);
  const rowRef = useRef(null);

  useEffect(() => {
    if (isActiveRow && cardRefs.current[colFocus]) {
      cardRefs.current[colFocus].focus();
    }
  }, [isActiveRow, colFocus]);

  useEffect(() => {
    if (typeof initialFocusCol === 'number') setColFocus(initialFocusCol);
  }, [initialFocusCol]);

  const handleKeyDown = (e) => {
    if (e.key === 'ArrowRight') {
      e.preventDefault();
      setColFocus(c => Math.min(c + 1, items.length - 1));
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      setColFocus(c => Math.max(c - 1, 0));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      onRowFocus(rowIndex - 1, colFocus);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      onRowFocus(rowIndex + 1, colFocus);
    } else if (e.key === 'Enter') {
      onSelect(items[colFocus]);
    }
  };

  return (
    <div ref={rowRef} className="mb-10">
      <h2 className="text-white text-2xl font-bold mb-5 px-16">{title}</h2>
      <div className="flex gap-4 overflow-x-auto scrollbar-hide px-16 pb-4" onKeyDown={handleKeyDown}>
        {items.map((item, i) => (
          <TVCard
            key={item.stream_id || item.series_id || item.num || i}
            item={item}
            onSelect={onSelect}
            isFocused={isActiveRow && colFocus === i}
            cardRef={el => cardRefs.current[i] = el}
          />
        ))}
      </div>
    </div>
  );
}

export default function TVBrowse() {
  const navigate = useNavigate();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');
  const type = urlParams.get('type');
  const playlist = getPlaylistFromLocal(playlistId);

  const [activeRow, setActiveRow] = useState(0);
  const [colPerRow, setColPerRow] = useState({});
  const [searchMode, setSearchMode] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const searchRef = useRef(null);

  // Fetch categories
  const { data: categories, isLoading: loadingCats } = useQuery({
    queryKey: ['tv-categories', playlistId, type],
    queryFn: async () => {
      if (!playlist) return [];
      if (type === 'live') {
        const { data } = await base44.functions.invoke('xtreamProxy', {
          host: playlist.host, username: playlist.username, password: playlist.password,
          params: { action: 'get_live_categories' }
        });
        return data || [];
      } else if (type === 'movie') {
        const { data } = await base44.functions.invoke('xtreamProxy', {
          host: playlist.host, username: playlist.username, password: playlist.password,
          params: { action: 'get_vod_categories' }
        });
        return data || [];
      } else if (type === 'series') {
        const { data } = await base44.functions.invoke('xtreamProxy', {
          host: playlist.host, username: playlist.username, password: playlist.password,
          params: { action: 'get_series_categories' }
        });
        return data || [];
      }
      return [];
    },
    enabled: !!playlist && type !== 'search' && type !== 'favorites',
    staleTime: 5 * 60 * 1000,
  });

  // Fetch content for first few categories
  const firstCats = (categories || []).slice(0, 6);
  const { data: rowsData, isLoading: loadingRows } = useQuery({
    queryKey: ['tv-rows', playlistId, type, firstCats.map(c => c.category_id).join(',')],
    queryFn: async () => {
      const results = await Promise.all(firstCats.map(async (cat) => {
        let action = 'get_vod_streams';
        if (type === 'live') action = 'get_live_streams';
        if (type === 'series') action = 'get_series';
        const { data } = await base44.functions.invoke('xtreamProxy', {
          host: playlist.host, username: playlist.username, password: playlist.password,
          params: { action, category_id: cat.category_id }
        });
        return { category: cat.category_name, items: (data || []).slice(0, 30) };
      }));
      return results.filter(r => r.items.length > 0);
    },
    enabled: !!playlist && firstCats.length > 0,
    staleTime: 5 * 60 * 1000,
  });

  // Search
  const { data: searchResults, isLoading: loadingSearch } = useQuery({
    queryKey: ['tv-search', playlistId, type, searchQuery],
    queryFn: async () => {
      if (!searchQuery.trim() || searchQuery.length < 2) return [];
      let action = 'get_vod_streams';
      if (type === 'live') action = 'get_live_streams';
      if (type === 'series') action = 'get_series';
      const { data } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host, username: playlist.username, password: playlist.password,
        params: { action }
      });
      const q = searchQuery.toLowerCase();
      return (data || []).filter(i => i.name?.toLowerCase().includes(q)).slice(0, 50);
    },
    enabled: searchMode && searchQuery.length >= 2,
  });

  const rows = searchMode
    ? (searchResults?.length ? [{ category: `Results for "${searchQuery}"`, items: searchResults }] : [])
    : (rowsData || []);

  const handleRowFocus = (rowIdx, col) => {
    const clamped = Math.max(0, Math.min(rowIdx, rows.length - 1));
    setActiveRow(clamped);
    setColPerRow(prev => ({ ...prev, [clamped]: col }));
  };

  const handleSelect = (item) => {
    const buildStreamUrl = () => {
      let host = playlist.host;
      if (!/^https?:\/\//i.test(host)) host = `http://${host}`;
      const u = new URL(host);
      if (type === 'live') {
        return `${u.protocol}//${u.host}/live/${playlist.username}/${playlist.password}/${item.stream_id}.m3u8`;
      } else if (type === 'movie') {
        return `${u.protocol}//${u.host}/movie/${playlist.username}/${playlist.password}/${item.stream_id}.${item.container_extension || 'mp4'}`;
      }
      return null;
    };

    if (type === 'series') {
      navigate(`/tv/browse?playlistId=${playlistId}&type=series-detail&seriesId=${item.series_id}&seriesName=${encodeURIComponent(item.name)}`);
      return;
    }

    const streamUrl = buildStreamUrl();
    if (streamUrl) {
      navigate(`/tv/player?channelUrl=${encodeURIComponent(streamUrl)}&channelName=${encodeURIComponent(item.name)}&containerExtension=${item.container_extension || 'm3u8'}&coverImage=${encodeURIComponent(item.cover || item.stream_icon || '')}`);
    }
  };

  const typeTitle = { live: 'Live TV', movie: 'Movies', series: 'Series', favorites: 'Favorites', search: 'Search' }[type] || type;
  const isLoading = loadingCats || loadingRows;

  return (
    <div className="min-h-screen flex flex-col" style={{ background: '#0a0a0a' }}>
      {/* Header */}
      <div className="flex items-center gap-6 px-16 pt-10 pb-6 flex-shrink-0">
        <button
          onClick={() => navigate(`/tv/menu?playlistId=${playlistId}`)}
          className="tv-focus p-3 rounded-full"
          style={{ background: 'rgba(255,255,255,0.1)' }}
        >
          <ArrowLeft className="w-6 h-6 text-white" />
        </button>
        <h1 className="text-4xl font-black text-white">{typeTitle}</h1>
        <div className="ml-auto">
          {!searchMode ? (
            <button
              onClick={() => { setSearchMode(true); setTimeout(() => searchRef.current?.focus(), 100); }}
              className="tv-focus flex items-center gap-2 px-6 py-3 rounded-full text-gray-300 text-lg"
              style={{ background: 'rgba(255,255,255,0.08)', border: '1px solid rgba(255,255,255,0.15)' }}
            >
              <Search className="w-5 h-5" />
              Search
            </button>
          ) : (
            <div className="flex items-center gap-3">
              <input
                ref={searchRef}
                value={searchQuery}
                onChange={e => setSearchQuery(e.target.value)}
                placeholder={`Search ${typeTitle}...`}
                className="px-5 py-3 rounded-full text-white text-lg outline-none"
                style={{ background: 'rgba(255,255,255,0.12)', border: '2px solid #06b6d4', width: '350px' }}
              />
              <button
                onClick={() => { setSearchMode(false); setSearchQuery(''); }}
                className="tv-focus p-3 rounded-full"
                style={{ background: 'rgba(255,255,255,0.1)' }}
              >
                <X className="w-5 h-5 text-white" />
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-y-auto scrollbar-hide pb-16">
        {isLoading && (
          <div className="flex flex-col items-center justify-center h-64 gap-4">
            <Loader2 className="w-12 h-12 text-cyan-400 animate-spin" />
            <p className="text-gray-400 text-xl">Loading content...</p>
          </div>
        )}

        {!isLoading && rows.length === 0 && !searchMode && (
          <div className="flex flex-col items-center justify-center h-64 gap-4">
            <Tv className="w-16 h-16 text-gray-700" />
            <p className="text-gray-500 text-xl">No content available</p>
          </div>
        )}

        {!isLoading && searchMode && searchQuery.length >= 2 && rows.length === 0 && (
          <div className="flex flex-col items-center justify-center h-64 gap-4">
            <Search className="w-16 h-16 text-gray-700" />
            <p className="text-gray-500 text-xl">No results for "{searchQuery}"</p>
          </div>
        )}

        {rows.map((row, i) => (
          <TVRow
            key={row.category + i}
            title={row.category}
            items={row.items}
            onSelect={handleSelect}
            rowIndex={i}
            isActiveRow={activeRow === i}
            onRowFocus={handleRowFocus}
            initialFocusCol={colPerRow[i] || 0}
          />
        ))}
      </div>
    </div>
  );
}