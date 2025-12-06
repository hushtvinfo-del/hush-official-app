import React, { useState, useEffect } from "react";
import { base44 } from "@/api/base44Client";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ArrowLeft, Play, Search, Tv, WifiOff, Star } from "lucide-react";
import { motion } from "framer-motion";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

const fetchChannels = async (playlistId, categoryId) => {
    const playlist = getPlaylistFromLocal(playlistId);
    if (!playlist) throw new Error("Playlist not found");

    const { data } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host,
        username: playlist.username,
        password: playlist.password,
        params: { action: 'get_live_streams', category_id: categoryId }
    });
    // Ensure the function always returns an array
    return Array.isArray(data) ? data : [];
}

export default function Channels() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');
  const categoryId = urlParams.get('categoryId');
  const categoryName = decodeURIComponent(urlParams.get('categoryName'));
  const group = urlParams.get('group'); // Added this line
  const [searchQuery, setSearchQuery] = useState("");

  const { data: playlist } = useQuery({
      queryKey: ['playlist', playlistId],
      queryFn: () => getPlaylistFromLocal(playlistId),
      enabled: !!playlistId
  });

  const userIdentifier = playlist?.username;

  const { data: channels, isLoading, error } = useQuery({
    queryKey: ['live_streams', playlistId, categoryId],
    queryFn: () => fetchChannels(playlistId, categoryId),
    enabled: !!playlistId && !!categoryId,
  });

  const { data: favoriteChannels } = useQuery({
    queryKey: ['favoriteChannels', userIdentifier, playlistId],
    queryFn: () => base44.entities.FavoriteChannel.filter({ user_email: userIdentifier, playlist_id: playlistId }),
    enabled: !!userIdentifier && !!playlistId,
    initialData: []
  });

  const favoriteChannelIds = React.useMemo(() => new Set(favoriteChannels.map(fav => fav.channel_id)), [favoriteChannels]);

  const toggleFavoriteMutation = useMutation({
    mutationFn: async ({ channel, isFavorite }) => {
      if (isFavorite) {
        const existing = favoriteChannels.find(fav => fav.channel_id === channel.stream_id.toString());
        if (existing) {
          await base44.entities.FavoriteChannel.delete(existing.id);
        }
      } else {
        await base44.entities.FavoriteChannel.create({
          user_email: userIdentifier,
          channel_id: channel.stream_id.toString(),
          playlist_id: playlistId,
          channel_info: {
            name: channel.name,
            icon: channel.stream_icon || '',
            category_id: categoryId,
            category_name: categoryName,
            epg_channel_id: channel.epg_channel_id || ''
          }
        });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['favoriteChannels', userIdentifier, playlistId] });
    }
  });

  const handleToggleFavorite = (e, channel) => {
    e.preventDefault();
    e.stopPropagation();
    const isFavorite = favoriteChannelIds.has(channel.stream_id.toString());
    toggleFavoriteMutation.mutate({ channel, isFavorite });
  };

  // Fetch enhanced logos for sports channels
  const [channelsWithLogos, setChannelsWithLogos] = useState([]);
  const [logosFetching, setLogosFetching] = useState(false);

  // Cache for fetched logos (stored in localStorage)
  const getLogoCache = () => {
    try {
      const cache = localStorage.getItem('channel_logo_cache');
      return cache ? JSON.parse(cache) : {};
    } catch {
      return {};
    }
  };

  const setLogoCache = (channelName, logoUrl) => {
    try {
      const cache = getLogoCache();
      cache[channelName.toLowerCase()] = {
        url: logoUrl,
        timestamp: Date.now()
      };
      localStorage.setItem('channel_logo_cache', JSON.stringify(cache));
    } catch (e) {
      console.error('Failed to cache logo:', e);
    }
  };

  // Process items in batches to avoid rate limiting
  const processBatch = async (items, batchSize, processor) => {
    const results = [];
    for (let i = 0; i < items.length; i += batchSize) {
      const batch = items.slice(i, i + batchSize);
      const batchResults = await Promise.all(batch.map(processor));
      results.push(...batchResults);
      
      // Small delay between batches to avoid rate limits
      if (i + batchSize < items.length) {
        await new Promise(resolve => setTimeout(resolve, 500));
      }
    }
    return results;
  };

  useEffect(() => {
    if (!channels || channels.length === 0) return;

    const enhanceChannelLogos = async () => {
      setLogosFetching(true);
      
      const logoCache = getLogoCache();
      const logoMap = new Map();
      
      // Filter channels that need logo fetching
      const channelsNeedingLogos = channels.filter(channel => 
        !channel.stream_icon || channel.stream_icon === ''
      );

      // Process in batches of 3 to avoid rate limits
      await processBatch(channelsNeedingLogos, 3, async (channel) => {
        const cacheKey = channel.name.toLowerCase();
        
        // Check cache first (valid for 7 days)
        if (logoCache[cacheKey]) {
          const cached = logoCache[cacheKey];
          const age = Date.now() - cached.timestamp;
          const sevenDays = 7 * 24 * 60 * 60 * 1000;
          
          if (age < sevenDays && cached.url) {
            logoMap.set(channel.stream_id, cached.url);
            return;
          }
        }

        // Fetch from API
        try {
          const result = await base44.functions.invoke('getSportChannelLogo', {
            channel_name: channel.name
          });
          
          if (result.logo_url) {
            logoMap.set(channel.stream_id, result.logo_url);
            setLogoCache(channel.name, result.logo_url);
          }
        } catch (error) {
          // Silently fail - will use fallback icon
          console.log(`Logo fetch skipped for ${channel.name}`);
        }
      });

      // Enhance channels with fetched logos
      const enhanced = channels.map(channel => ({
        ...channel,
        stream_icon: logoMap.get(channel.stream_id) || channel.stream_icon
      }));

      setChannelsWithLogos(enhanced);
      setLogosFetching(false);
    };

    enhanceChannelLogos();
  }, [channels]);

  // Use enhanced channels if available, otherwise use original
  const displayChannels = channelsWithLogos.length > 0 ? channelsWithLogos : channels || [];
  
  const filteredChannels = displayChannels.filter(channel =>
    channel.name.toLowerCase().includes(searchQuery.toLowerCase())
  );
  
  if (isLoading || !playlist) {
      return (
        <div className="min-h-screen p-4 md:p-8">
            <div className="max-w-7xl mx-auto">
                <h1 className="text-3xl md:text-4xl font-bold text-white mb-8">{categoryName}</h1>
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                    {[...Array(12)].map((_, i) => (
                    <Card key={i} className="bg-gray-800/50 backdrop-blur-xl border-orange-500/30 animate-pulse">
                        <CardContent className="p-3">
                        <div className="aspect-video bg-gray-700 rounded mb-3" />
                        <div className="h-4 bg-gray-700 rounded w-3/4" />
                        </CardContent>
                    </Card>
                    ))}
                </div>
            </div>
        </div>
      );
  }

  if (error) return (
      <div className="min-h-screen p-4 md:p-8 flex items-center justify-center">
        <Card className="bg-gray-800/50 backdrop-blur-xl border-red-500/30">
          <CardContent className="p-12 text-center">
            <WifiOff className="w-16 h-16 text-red-400 mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-white mb-2">Error Loading Channels</h3>
            <p className="text-gray-400 max-w-sm">{error.message}</p>
          </CardContent>
        </Card>
      </div>
  );
  
  const constructStreamUrl = (host, username, password, streamId) => {
    let fullHost = host;
    if (!/^https?:\/\//i.test(host)) {
      fullHost = `http://${host}`;
    }
    const u = new URL(fullHost);
    return `${u.protocol}//${u.host}/live/${username}/${password}/${streamId}.m3u8`;
  }

  return (
    <div className="min-h-screen p-4 md:p-8">
      <div className="max-w-7xl mx-auto">
        <Button
          variant="ghost"
          onClick={() => navigate(createPageUrl(`LiveCategories?playlistId=${playlistId}&group=${group}`))}
          className="mb-6 text-orange-300 hover:text-white hover:bg-orange-500/20"
        >
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Categories
        </Button>

        <div className="mb-8">
          <h1 className="text-3xl md:text-4xl font-bold text-white mb-2">{categoryName}</h1>
          <p className="text-orange-300">{channels?.length || 0} channels available</p>
        </div>

        <div className="mb-6">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
            <Input
              type="text"
              placeholder="Search channels..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10 bg-gray-800/50 border-orange-500/30 text-white placeholder:text-gray-500 focus:border-orange-500"
            />
          </div>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            {filteredChannels.map((channel, index) => {
                const streamUrl = constructStreamUrl(playlist.host, playlist.username, playlist.password, channel.stream_id);
                const isFavorite = favoriteChannelIds.has(channel.stream_id.toString());
                return (
                  <Link
                    key={channel.stream_id}
                    to={createPageUrl(`Guide?playlistId=${playlistId}&channelUrl=${encodeURIComponent(streamUrl)}&channelName=${encodeURIComponent(channel.name)}&categoryId=${categoryId}&categoryName=${encodeURIComponent(categoryName)}&streamId=${channel.stream_id}`)}
                  >
                    <motion.div
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: index * 0.02 }}
                      whileHover={{ scale: 1.05 }}
                      whileTap={{ scale: 0.95 }}
                    >
                      <Card className="bg-gray-800/50 backdrop-blur-xl border-orange-500/30 hover:border-orange-500/60 transition-all cursor-pointer group overflow-hidden h-full flex flex-col">
                        <CardContent className="p-3 relative flex-grow flex flex-col">
                          <button
                            onClick={(e) => handleToggleFavorite(e, channel)}
                            className="absolute top-2 right-2 z-10 w-8 h-8 bg-gray-900/80 hover:bg-gray-800 rounded-full flex items-center justify-center transition-all"
                          >
                            <Star className={`w-4 h-4 ${isFavorite ? 'fill-yellow-400 text-yellow-400' : 'text-gray-400'}`} />
                          </button>
                          <div className="aspect-video bg-gray-900/50 rounded-md mb-3 flex items-center justify-center overflow-hidden relative">
                              <img
                                src={channel.stream_icon}
                                alt={channel.name}
                                className="w-full h-full object-contain"
                                loading="lazy"
                                onError={(e) => { e.target.onerror = null; e.target.style.display='none'; e.target.nextSibling.style.display='flex' }}
                              />
                            <div style={{ display: 'none', position: 'absolute', inset: 0, alignItems: 'center', justifyContent: 'center' }}>
                              <Tv className="w-8 h-8 text-orange-400" />
                            </div>
                            <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                              <div className="w-12 h-12 bg-orange-600 rounded-full flex items-center justify-center">
                                <Play className="w-6 h-6 text-white ml-1" />
                              </div>
                            </div>
                          </div>
                          <div className="flex items-center gap-1">
                            {isFavorite && (
                              <Star className="w-3 h-3 fill-yellow-400 text-yellow-400 flex-shrink-0" />
                            )}
                            <h3 className="text-sm font-semibold text-white truncate leading-tight">{channel.name}</h3>
                          </div>
                        </CardContent>
                      </Card>
                    </motion.div>
                  </Link>
                );
            })}
          </div>
      </div>
    </div>
  );
}