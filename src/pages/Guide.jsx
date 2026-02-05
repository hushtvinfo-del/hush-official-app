import React, { useState, useEffect, useRef } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { base44 } from "@/api/base44Client";
import { useQuery, useQueryClient, useMutation } from "@tanstack/react-query";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { ArrowLeft, Tv, Star, ChevronLeft, ChevronRight, Menu, Cast } from "lucide-react";
import { useEpg } from "../components/useEpg";
import { ScrollArea } from "@/components/ui/scroll-area";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import { detectDevice } from "@/components/deviceDetection";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

// --- Player Component ---
const MiniPlayer = ({ channelUrl }) => {
  const videoRef = useRef(null);
  const playerRef = useRef(null);

  useEffect(() => {
    console.log('🎬 MiniPlayer: New channel URL:', channelUrl);
    
    const cssId = 'videojs-css';
    if (!document.getElementById(cssId)) {
        const link = document.createElement('link');
        link.id = cssId;
        link.rel = 'stylesheet';
        link.href = 'https://vjs.zencdn.net/8.10.0/video-js.css';
        document.head.appendChild(link);
    }
    
    const initializePlayer = () => {
        if (!videoRef.current) {
            console.warn('⚠️ Video ref not available');
            return;
        }

        if (playerRef.current && !playerRef.current.isDisposed()) {
            console.log('🔄 Updating existing player source');
            try {
                playerRef.current.src({ src: channelUrl, type: 'application/x-mpegURL' });
                playerRef.current.load();
                playerRef.current.play().catch(e => console.log('Autoplay prevented:', e));
            } catch (e) {
                console.error('❌ Error updating player:', e);
                // Dispose and recreate on error
                playerRef.current.dispose();
                playerRef.current = null;
                initializePlayer();
            }
        } else {
            console.log('✨ Creating new player instance');
            const options = {
                autoplay: true,
                controls: true,
                responsive: true,
                fluid: true,
                preload: 'auto',
                sources: [{ src: channelUrl, type: 'application/x-mpegURL' }],
                html5: {
                    vhs: {
                        overrideNative: true
                    }
                }
            };
            playerRef.current = window.videojs(videoRef.current, options);
            
            playerRef.current.on('error', () => {
                const error = playerRef.current.error();
                console.error('❌ Player error:', error);
            });
            
            playerRef.current.on('loadstart', () => {
                console.log('📡 Loading stream...');
            });
            
            playerRef.current.on('loadedmetadata', () => {
                console.log('✅ Stream loaded successfully');
            });
        }
    };
    
    if (window.videojs) {
        initializePlayer();
    } else {
        const scriptId = 'videojs-script';
        if (!document.getElementById(scriptId)) {
            const script = document.createElement('script');
            script.id = scriptId;
            script.src = 'https://vjs.zencdn.net/8.10.0/video.js';
            script.async = true;
            document.body.appendChild(script);
            script.onload = initializePlayer;
        } else {
             const existingScript = document.getElementById(scriptId);
             if (existingScript.readyState === 'complete') {
                 initializePlayer();
             } else {
                 existingScript.addEventListener('load', initializePlayer);
             }
        }
    }

    return () => {
        // Don't dispose on channel change, only on unmount
    };
  }, [channelUrl]);

  return (
    <div data-vjs-player className="w-full h-full bg-black">
      <video ref={videoRef} className="video-js vjs-big-play-centered w-full h-full" />
    </div>
  );
};

// Channel List Component (reusable for mobile and desktop)
const ChannelList = ({ channelsData, isLoading, currentStreamId, handleChannelSelect, epgData, isLoadingEpg, user, toggleFavorite, favoriteChannelIds, isMutating, isTV }) => (
  <ScrollArea className="flex-1">
    {isLoading && <div className="p-4 text-gray-400">Loading channels...</div>}
    {channelsData && channelsData.map(channel => {
      const isFavorite = favoriteChannelIds.has(channel.stream_id.toString());
      const isActive = channel.stream_id.toString() === currentStreamId;
      
      return (
        <div 
          key={channel.stream_id} 
          onClick={() => handleChannelSelect(channel)}
          tabIndex={0}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              handleChannelSelect(channel);
            }
          }}
          className={`flex items-center gap-3 ${isTV ? 'p-4' : 'p-3'} border-b border-gray-800 cursor-pointer transition-all ${isTV ? 'tv-focusable' : ''} ${
            isActive ? 'bg-blue-500/30 border-l-4 border-l-cyan-400' : 'hover:bg-blue-500/20 focus:bg-blue-500/30 focus:outline-none'
          }`}
        >
          <div className="flex-shrink-0 w-12 h-12 bg-black rounded overflow-hidden flex items-center justify-center">
            {channel.stream_icon ? (
              <img src={channel.stream_icon} alt={channel.name} className="w-full h-full object-contain"/>
            ) : (
              <Tv className="w-6 h-6 text-cyan-400"/>
            )}
          </div>
          <div className="flex-1 min-w-0">
            <p className={`font-semibold truncate ${isActive ? 'text-white' : 'text-gray-300'}`}>
              {channel.name}
            </p>
            <p className="text-xs text-gray-500 truncate">
              {isLoadingEpg ? 'Loading...' : (epgData && epgData[channel.epg_channel_id]?.current?.title) || 'No EPG'}
            </p>
          </div>
          {user && (
            <Button 
              variant="ghost" 
              size="icon"
              className="flex-shrink-0 h-8 w-8"
              onClick={(e) => {
                e.stopPropagation();
                toggleFavorite(channel);
              }}
              disabled={isMutating}
            >
              <Star className={`w-4 h-4 ${isFavorite ? 'fill-yellow-400 text-yellow-400' : 'text-gray-500'}`}/>
            </Button>
          )}
        </div>
      );
    })}
    {!isLoading && channelsData?.length === 0 && (
      <p className="p-4 text-gray-400 text-center">No channels in this category.</p>
    )}
  </ScrollArea>
);

export default function Guide() {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [mobileChannelsOpen, setMobileChannelsOpen] = useState(false);
  const [castAvailable, setCastAvailable] = useState(false);
  const [casting, setCasting] = useState(false);
  const { isTV } = detectDevice();

  const getUrlParams = () => new URLSearchParams(location.search);
  const [params, setParams] = useState(getUrlParams());

  useEffect(() => {
    setParams(getUrlParams());
  }, [location.search]);

  const playlistId = params.get('playlistId');
  const categoryId = params.get('categoryId');
  const currentChannelUrl = decodeURIComponent(params.get('channelUrl') || '');
  const currentStreamId = params.get('streamId');
  const channelName = decodeURIComponent(params.get('channelName') || '');

  const { data: user } = useQuery({
    queryKey: ['user'],
    queryFn: () => base44.auth.me(),
  });

  const { data: playlist, isLoading: isLoadingPlaylist } = useQuery({
    queryKey: ['playlist', playlistId],
    queryFn: () => getPlaylistFromLocal(playlistId),
    enabled: !!playlistId,
  });

  const userIdentifier = playlist?.username;
  
  const { data: channelsData, isLoading: isLoadingChannels } = useQuery({
    queryKey: ['category_channels', playlistId, categoryId],
    queryFn: async () => {
      if (!playlist) return [];
      const { data } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host,
        username: playlist.username,
        password: playlist.password,
        params: { action: 'get_live_streams', category_id: categoryId }
      });
      return Array.isArray(data) ? data : [];
    },
    enabled: !!playlist,
  });
  
  const { epgData, isLoadingEpg, epgError } = useEpg(playlist, channelsData);

  const { data: favoriteChannels, isLoading: isLoadingFavorites } = useQuery({
    queryKey: ['favoriteChannels', userIdentifier, playlistId],
    queryFn: () => base44.entities.FavoriteChannel.filter({ user_email: userIdentifier, playlist_id: playlistId }),
    enabled: !!userIdentifier && !!playlistId,
    initialData: []
  });

  const favoriteChannelIds = React.useMemo(() => new Set(favoriteChannels.map(fav => fav.channel_id)), [favoriteChannels]);

  const addFavoriteMutation = useMutation({
    mutationFn: (channel) => {
        if (!userIdentifier) return Promise.reject('No user identifier');
        const categoryName = decodeURIComponent(params.get('categoryName'));
        return base44.entities.FavoriteChannel.create({
            user_email: userIdentifier,
            channel_id: channel.stream_id.toString(),
            playlist_id: playlistId,
            channel_info: { 
                name: channel.name, 
                icon: channel.stream_icon, 
                category_id: categoryId, 
                category_name: categoryName,
                epg_channel_id: channel.epg_channel_id
            }
        })
    },
    onSuccess: () => {
        queryClient.invalidateQueries(['favoriteChannels']);
    }
  });

  const removeFavoriteMutation = useMutation({
    mutationFn: (channelId) => {
        const fav = favoriteChannels.find(f => f.channel_id === channelId);
        if (!fav) return Promise.reject('Favorite not found');
        return base44.entities.FavoriteChannel.delete(fav.id);
    },
    onSuccess: () => {
        queryClient.invalidateQueries(['favoriteChannels']);
    }
  });

  const toggleFavorite = (channel) => {
      const isFavorite = favoriteChannelIds.has(channel.stream_id.toString());
      if (isFavorite) {
          removeFavoriteMutation.mutate(channel.stream_id.toString());
      } else {
          addFavoriteMutation.mutate(channel);
      }
  };
  
  const constructStreamUrl = (host, username, password, streamId) => {
    let fullHost = host;
    if (!/^https?:\/\//i.test(host)) fullHost = `http://${fullHost}`;
    const u = new URL(fullHost);
    return `${u.protocol}//${u.host}/live/${username}/${password}/${streamId}.m3u8`;
  }
  
  const handleChannelSelect = (channel) => {
      const newUrl = constructStreamUrl(playlist.host, playlist.username, playlist.password, channel.stream_id);
      const newParams = new URLSearchParams(location.search);
      newParams.set('channelUrl', encodeURIComponent(newUrl));
      newParams.set('channelName', encodeURIComponent(channel.name));
      newParams.set('streamId', channel.stream_id);
      navigate(`${location.pathname}?${newParams.toString()}`, { replace: true });
      setMobileChannelsOpen(false);
  }

  const handleCast = () => {
    if (!window.chrome || !window.chrome.cast || !window.chrome.cast.isAvailable) {
      alert('Cast API not loaded yet. Please wait and try again.');
      return;
    }

    const cast = window.chrome.cast;
    
    cast.requestSession(
      (session) => {
        console.log('🎬 Cast session created for Live TV:', channelName);
        
        // Use HLS-specific receiver for better stream support
        const mediaInfo = new cast.media.MediaInfo(currentChannelUrl, 'application/x-mpegURL');
        mediaInfo.metadata = new cast.media.GenericMediaMetadata();
        mediaInfo.metadata.title = channelName;
        mediaInfo.metadata.subtitle = 'Live TV - HushTV';
        mediaInfo.streamType = cast.media.StreamType.LIVE;
        
        const request = new cast.media.LoadRequest(mediaInfo);
        request.autoplay = true;
        
        console.log('📤 Loading media on Chromecast:', { url: currentChannelUrl, title: channelName });
        
        session.loadMedia(
          request,
          (media) => {
            console.log('✅ Live TV loaded on Chromecast');
            setCasting(true);
            
            // Listen for media status updates
            media.addUpdateListener((isAlive) => {
              console.log('📊 Media update - isAlive:', isAlive);
              if (!isAlive) {
                console.log('🛑 Media session ended');
                setCasting(false);
              }
            });
          },
          (error) => {
            console.error('❌ Chromecast load error:', error);
            setCasting(false);
            
            let errorMessage = 'Failed to cast Live TV. ';
            if (error.code === 'LOAD_FAILED') {
              errorMessage += 'The stream could not be loaded. This might be due to network issues or unsupported format.';
            } else {
              errorMessage += error.description || error.code || 'Unknown error';
            }
            
            alert(errorMessage);
          }
        );
      },
      (error) => {
        console.error('❌ Error requesting cast session:', error);
        alert('Failed to connect to Chromecast. Make sure your device is on the same network.');
      }
    );
  };

  useEffect(() => {
    const initializeCast = () => {
      if (window.chrome && window.chrome.cast && window.chrome.cast.isAvailable) {
        const cast = window.chrome.cast;
        
        // Use styled receiver for better HLS support
        const sessionRequest = new cast.SessionRequest('CC1AD845');
        
        const apiConfig = new cast.ApiConfig(
          sessionRequest,
          (session) => {
            console.log('✅ Cast session active:', session.sessionId);
            setCasting(true);
            
            // Listen for session ending
            session.addUpdateListener((isAlive) => {
              console.log('Session update - isAlive:', isAlive);
              if (!isAlive) {
                console.log('🛑 Cast session ended');
                setCasting(false);
              }
            });
          },
          (status) => {
            console.log('📡 Cast receiver status:', status);
            if (status === cast.ReceiverAvailability.AVAILABLE) {
              setCastAvailable(true);
              console.log('✅ Chromecast devices available');
            } else if (status === cast.ReceiverAvailability.UNAVAILABLE) {
              setCastAvailable(false);
              console.log('❌ No Chromecast devices found');
            }
          },
          cast.AutoJoinPolicy.ORIGIN_SCOPED
        );
        
        cast.initialize(apiConfig, 
          () => {
            console.log('✅ Cast framework initialized successfully');
          },
          (error) => {
            console.error('❌ Cast initialization error:', error);
          }
        );
      }
    };

    if (!window.chrome || !window.chrome.cast || !window.chrome.cast.isAvailable) {
      console.log('⏳ Loading Cast SDK...');
      const script = document.createElement('script');
      script.src = 'https://www.gstatic.com/cv/js/sender/v1/cast_sender.js?loadCastFramework=1';
      script.async = true;
      document.body.appendChild(script);
      
      window['__onGCastApiAvailable'] = (isAvailable) => {
        console.log('Cast API available:', isAvailable);
        if (isAvailable) {
          setTimeout(initializeCast, 1000);
        }
      };
    } else {
      initializeCast();
    }
    
    return () => {
      // Cleanup
      if (window.chrome && window.chrome.cast && window.chrome.cast.isAvailable) {
        try {
          const castContext = window.chrome.cast.framework?.CastContext?.getInstance();
          if (castContext) {
            const session = castContext.getCurrentSession();
            if (session) {
              console.log('Stopping cast session on unmount');
              session.endSession(true);
            }
          }
        } catch (e) {
          console.log('No active cast session to clean up');
        }
      }
    };
  }, []);

  const isLoading = isLoadingPlaylist || isLoadingChannels;
  const isMutating = addFavoriteMutation.isPending || removeFavoriteMutation.isPending;

  const formatTime = (dateString) => {
    if (!dateString) return '';
    const date = new Date(dateString);
    return date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit', hour12: true });
  };

  const currentChannelInfo = channelsData?.find(c => c.stream_id.toString() === currentStreamId);
  const currentEpgData = currentChannelInfo && epgData ? epgData[currentChannelInfo.epg_channel_id] : null;
  const currentEpg = currentEpgData?.current;
  const nextEpg = currentEpgData?.next;

  return (
    <div className="h-screen flex flex-col bg-black">
      {/* Top Bar - TiVimate Style */}
      <div className="bg-slate-900/95 border-b border-blue-500/20 px-4 md:px-6 py-3 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-2 md:gap-4">
          <Button variant="ghost" size="icon" onClick={() => navigate(-1)} className="text-cyan-300 hover:text-white hover:bg-blue-500/20">
            <ArrowLeft className="w-5 h-5" />
          </Button>
          
          {/* Mobile: Channel Menu Button */}
          <Sheet open={mobileChannelsOpen} onOpenChange={setMobileChannelsOpen}>
            <SheetTrigger asChild>
              <Button variant="ghost" size="icon" className="md:hidden text-cyan-300 hover:text-white hover:bg-blue-500/20">
                <Menu className="w-5 h-5" />
              </Button>
            </SheetTrigger>
            <SheetContent side="left" className="w-80 bg-slate-900 p-0 border-r border-blue-500/20">
              <div className="flex flex-col h-full">
                <div className="p-4 border-b border-blue-500/20 bg-slate-950">
                  <h3 className="text-white font-bold">{decodeURIComponent(params.get('categoryName') || 'Channels')}</h3>
                </div>
                <ChannelList 
                  channelsData={channelsData}
                  isLoading={isLoading}
                  currentStreamId={currentStreamId}
                  handleChannelSelect={handleChannelSelect}
                  epgData={epgData}
                  isLoadingEpg={isLoadingEpg}
                  user={user}
                  toggleFavorite={toggleFavorite}
                  favoriteChannelIds={favoriteChannelIds}
                  isMutating={isMutating}
                  isTV={isTV}
                />
              </div>
            </SheetContent>
          </Sheet>

          <div className="flex items-center gap-2 md:gap-3">
            <h2 className="text-base md:text-lg font-bold text-white truncate">{decodeURIComponent(params.get('categoryName') || 'LIVE TV')}</h2>
          </div>
        </div>
        
        {/* Cast Button */}
        <div className="flex gap-2">
          {castAvailable && !casting && (
            <Button
              variant="outline"
              size="sm"
              onClick={handleCast}
              className="border-blue-500/30 hover:bg-blue-500/20 text-cyan-300"
              title="Cast to TV"
            >
              <Cast className="w-4 h-4 md:mr-2" />
              <span className="hidden md:inline">Cast</span>
            </Button>
          )}
          {casting && (
            <Button
              variant="outline"
              size="sm"
              onClick={() => {
                if (window.chrome && window.chrome.cast && window.chrome.cast.isAvailable) {
                  try {
                    const castContext = window.chrome.cast.framework?.CastContext?.getInstance();
                    if (castContext) {
                      const session = castContext.getCurrentSession();
                      if (session) {
                        session.endSession(true);
                        setCasting(false);
                      }
                    }
                  } catch (e) {
                    console.log('Error ending session:', e);
                    setCasting(false);
                  }
                }
              }}
              className="border-green-500/30 bg-green-500/20 text-green-300"
              title="Stop casting"
            >
              <Cast className="w-4 h-4 md:mr-2" />
              <span className="hidden md:inline">Casting</span>
            </Button>
          )}
        </div>
      </div>

      {/* Main Content Area - TiVimate Style Layout */}
      <div className="flex flex-1 overflow-hidden">
        {/* Desktop: Left Sidebar - Channel List (TiVimate Style) */}
        <div className="hidden md:flex w-80 bg-slate-950 border-r border-blue-500/20 flex-col flex-shrink-0">
          <ChannelList 
            channelsData={channelsData}
            isLoading={isLoading}
            currentStreamId={currentStreamId}
            handleChannelSelect={handleChannelSelect}
            epgData={epgData}
            isLoadingEpg={isLoadingEpg}
            user={user}
            toggleFavorite={toggleFavorite}
            favoriteChannelIds={favoriteChannelIds}
            isMutating={isMutating}
            isTV={isTV}
          />
        </div>

        {/* Right Content Area - Player and Guide */}
        <div className="flex-1 overflow-y-auto">
          <div className="min-h-full flex flex-col">
            {/* Video Player */}
            <div className="w-full bg-black aspect-video">
              <MiniPlayer channelUrl={currentChannelUrl} />
            </div>

            {/* EPG Guide Section - TiVimate Style */}
            <div className="bg-slate-950 border-t border-blue-500/20">
              {currentEpg ? (
                <div className="px-3 md:px-6 py-4">
                  {/* Current Program Info */}
                  <div className="mb-4">
                    <div className="flex items-center gap-2 mb-2">
                      <span className="bg-red-600 text-white text-xs font-bold px-2.5 py-1 rounded">LIVE</span>
                      <p className="text-sm text-cyan-400 font-semibold truncate">{currentChannelInfo?.name}</p>
                    </div>
                    <h4 className="text-white text-lg md:text-2xl font-bold mb-1 leading-tight">
                      {currentEpg.title}
                    </h4>
                    <p className="text-gray-400 text-xs md:text-sm mb-2">
                      {formatTime(currentEpg.start)} - {formatTime(currentEpg.stop)}
                      {currentEpg.episode && <span className="ml-2 text-cyan-400">Episode {currentEpg.episode}</span>}
                    </p>
                    {currentEpg.desc && (
                      <p className="text-gray-300 text-sm md:text-base leading-relaxed">
                        {currentEpg.desc}
                      </p>
                    )}
                  </div>

                  {/* Up Next */}
                  <div className="pt-3 border-t border-blue-500/20">
                    <div className="flex items-center gap-2 text-gray-400 mb-1.5">
                      <span className="text-xs font-semibold uppercase tracking-wider">Up Next</span>
                      <span className="text-xs">{nextEpg ? formatTime(nextEpg.start) : formatTime(currentEpg.stop)}</span>
                    </div>
                    {nextEpg ? (
                      <>
                        <h5 className="text-white font-semibold text-sm md:text-base">{nextEpg.title}</h5>
                        <p className="text-gray-400 text-xs md:text-sm mt-0.5">
                          {formatTime(nextEpg.start)} - {formatTime(nextEpg.stop)}
                          {nextEpg.episode && <span className="ml-2 text-cyan-400">Episode {nextEpg.episode}</span>}
                        </p>
                      </>
                    ) : (
                      <p className="text-gray-400 text-sm">Check back for upcoming schedule...</p>
                    )}
                  </div>
                </div>
              ) : (
                <div className="flex items-center justify-center py-8">
                  <div className="text-center px-4">
                    <Tv className="w-12 h-12 text-gray-700 mx-auto mb-3" />
                    <p className="text-gray-500 text-sm">
                      {isLoadingEpg ? 'Loading guide information...' : 'No guide information available'}
                    </p>
                    {epgError && (
                      <p className="text-red-500 text-xs mt-2">Error: {epgError.message}</p>
                    )}
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}