import React from "react";
import { base44 } from "@/api/base44Client";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from "@/components/ui/badge";
import { ArrowLeft, Play, Tv, Users, Loader2, Star, ChevronLeft, ChevronRight, Clapperboard, Cast } from "lucide-react";
import { motion } from "framer-motion";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import RatingBadge from "@/components/RatingBadge";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

const fetchSeriesInfo = async (playlistId, seriesId) => {
    const playlist = getPlaylistFromLocal(playlistId);
    if (!playlist) throw new Error("Playlist not found");

    const { data } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host,
        username: playlist.username,
        password: playlist.password,
        params: { action: 'get_series_info', series_id: seriesId }
    });
    return data;
};

function RecommendedForYou({ playlistId, currentContentId, currentName, currentPlot }) {
    const [recommendations, setRecommendations] = React.useState([]);
    const [isLoadingRecs, setIsLoadingRecs] = React.useState(false);
    const scrollContainerRef = React.useRef(null);

    const { data: playlist } = useQuery({
        queryKey: ['playlist', playlistId],
        queryFn: () => getPlaylistFromLocal(playlistId),
        enabled: !!playlistId,
    });

    const userIdentifier = playlist?.username;

    const { data: watchHistory } = useQuery({
        queryKey: ['watchHistory', userIdentifier, playlistId],
        queryFn: () => base44.entities.WatchProgress.filter({ user_email: userIdentifier, playlist_id: playlistId }, '-updated_date', 20),
        enabled: !!userIdentifier && !!playlistId,
        initialData: [],
    });

    React.useEffect(() => {
        const fetchRecommendations = async () => {
            if (!playlist || !watchHistory || !currentName) {
                setRecommendations([]);
                return;
            }

            setIsLoadingRecs(true);

            try {
                const { data } = await base44.functions.invoke('xtreamProxy', {
                    host: playlist.host,
                    username: playlist.username,
                    password: playlist.password,
                    params: { action: 'get_series' }
                });

                if (!data || data.length === 0) {
                    setRecommendations([]);
                    setIsLoadingRecs(false);
                    return;
                }

                const watchedIds = new Set(watchHistory.map(item => item.content_info?.series_id).filter(Boolean));
                const unwatched = data.filter(item => {
                    const itemId = String(item.series_id);
                    return itemId !== currentContentId && !watchedIds.has(itemId);
                });

                const aiPrompt = `Based on the series "${currentName}"${currentPlot ? ` with plot: "${currentPlot}"` : ''}, find the 10 most similar TV series from this list.

Consider:
- Similar genres and themes
- Similar plot elements and storytelling style
- Similar tone and atmosphere
- Series from the same universe or similar franchises

Available series (format: name):
${unwatched.slice(0, 200).map(s => s.name).join('\n')}

Return ONLY the exact series names that best match, in order of relevance.`;

                const aiResult = await base44.integrations.Core.InvokeLLM({
                    prompt: aiPrompt,
                    response_json_schema: {
                        type: "object",
                        properties: {
                            recommendations: {
                                type: "array",
                                items: { type: "string" },
                                description: "List of exact series names from the provided list"
                            }
                        },
                        required: ["recommendations"]
                    }
                });

                const recommendedNames = aiResult.recommendations || [];
                const matched = [];
                
                for (const name of recommendedNames) {
                    const series = unwatched.find(s => 
                        s.name.toLowerCase().trim() === name.toLowerCase().trim()
                    );
                    if (series && matched.length < 10) {
                        matched.push(series);
                    }
                }

                setRecommendations(matched);
            } catch (error) {
                console.error('Failed to fetch AI recommendations:', error);
                setRecommendations([]);
            } finally {
                setIsLoadingRecs(false);
            }
        };

        fetchRecommendations();
    }, [playlist, watchHistory, currentContentId, currentName, currentPlot]);

    if (isLoadingRecs) {
        return (
            <div className="mt-6">
                <Card className="bg-slate-800/30 border-blue-500/30 overflow-hidden">
                    <CardContent className="p-4">
                        <div className="flex items-center justify-center py-8">
                            <Loader2 className="w-6 h-6 animate-spin text-cyan-400 mr-2" />
                            <span className="text-white">Finding similar series...</span>
                        </div>
                    </CardContent>
                </Card>
            </div>
        );
    }

    if (recommendations.length === 0 || !playlist) {
        return null;
    }

    const scroll = (direction) => {
        if (scrollContainerRef.current) {
            const scrollAmount = 300;
            scrollContainerRef.current.scrollBy({
                left: direction === 'left' ? -scrollAmount : scrollAmount,
                behavior: 'smooth'
            });
        }
    };

    return (
        <div className="mt-6">
            <Card className="bg-slate-800/30 border-blue-500/30 overflow-hidden">
                <CardContent className="p-4">
                    <div className="flex items-center justify-between mb-3">
                        <h3 className="text-lg font-bold text-white flex items-center gap-2">
                            <Star className="w-5 h-5 text-cyan-400" />
                            More like this...
                        </h3>
                        <div className="flex gap-1">
                            <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => scroll('left')}
                                className="h-8 w-8 text-cyan-400 hover:bg-blue-500/20"
                            >
                                <ChevronLeft className="w-4 h-4" />
                            </Button>
                            <Button
                                variant="ghost"
                                size="icon"
                                onClick={() => scroll('right')}
                                className="h-8 w-8 text-cyan-400 hover:bg-blue-500/20"
                            >
                                <ChevronRight className="w-4 h-4" />
                            </Button>
                        </div>
                    </div>
                    
                    <div className="relative">
                        <ScrollArea className="w-full whitespace-nowrap" ref={scrollContainerRef}>
                            <div className="flex gap-3 pb-2">
                                {recommendations.map((item, index) => {
                                    const targetUrl = createPageUrl(`SeriesDetails?playlistId=${playlistId}&seriesId=${item.series_id}`);

                                    return (
                                        <div key={index} className="relative group flex-shrink-0" style={{ width: '140px' }}>
                                            <Link to={targetUrl}>
                                                <motion.div 
                                                    whileHover={{scale: 1.05}} 
                                                    className="h-full"
                                                >
                                                    <div className="bg-gray-900/50 rounded-lg overflow-hidden group-hover:bg-gray-900/70 transition-all">
                                                        <div className="aspect-[2/3] bg-gray-900 relative">
                                                            {item.cover ? (
                                                                <img 
                                                                    src={item.cover} 
                                                                    alt={item.name} 
                                                                    className="w-full h-full object-cover"
                                                                    onError={(e) => {
                                                                        e.target.style.display = 'none';
                                                                        e.target.nextElementSibling.style.display = 'flex';
                                                                    }}
                                                                />
                                                            ) : null}
                                                            <div className={`w-full h-full ${item.cover ? 'hidden' : 'flex'} items-center justify-center absolute inset-0 bg-gray-800`}>
                                                                <Clapperboard className="w-10 h-10 text-cyan-400" />
                                                            </div>
                                                            <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                                                <div className="w-10 h-10 bg-blue-600 rounded-full flex items-center justify-center">
                                                                    <Play className="w-5 h-5 text-white ml-0.5" />
                                                                </div>
                                                            </div>
                                                        </div>
                                                        <div className="p-2">
                                                            <p className="text-xs font-semibold text-white truncate">{item.name}</p>
                                                        </div>
                                                    </div>
                                                </motion.div>
                                            </Link>
                                        </div>
                                    )
                                })}
                            </div>
                            <ScrollBar orientation="horizontal" className="h-2" />
                        </ScrollArea>
                    </div>
                </CardContent>
            </Card>
        </div>
    );
}

export default function SeriesDetails() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');
  const seriesId = urlParams.get('seriesId');

  const [castAvailable, setCastAvailable] = React.useState(false);
  const [casting, setCasting] = React.useState(false);

  const { data: user, isLoadingUser } = useQuery({
    queryKey: ['user'],
    queryFn: async () => {
      try {
        return await base44.auth.me();
      } catch (error) {
        console.log('User not authenticated:', error);
        return null;
      }
    },
    retry: false
  });

  const { data: playlist } = useQuery({
      queryKey: ['playlist', playlistId],
      queryFn: () => getPlaylistFromLocal(playlistId),
      enabled: !!playlistId
  });

  const userIdentifier = playlist?.username;

  const { data: favoriteSeries, isLoading: isLoadingFavorites } = useQuery({
    queryKey: ['favoriteSeries', userIdentifier, playlistId],
    queryFn: () => base44.entities.FavoriteSeries.filter({ user_email: userIdentifier, playlist_id: playlistId }),
    enabled: !!userIdentifier && !!playlistId,
    initialData: [],
  });

  const { data: seriesInfo, isLoading: isLoadingSeries } = useQuery({
    queryKey: ['series-info', playlistId, seriesId],
    queryFn: () => fetchSeriesInfo(playlistId, seriesId),
    enabled: !!playlistId && !!seriesId,
  });

  const { data: tmdbData, isLoading: isLoadingTmdb, error: tmdbError } = useQuery({
    queryKey: ['tmdb-details', 'tv', seriesInfo?.info?.name],
    queryFn: async () => {
        const response = await base44.functions.invoke('getTmdbDetails', {
            type: 'tv',
            name: seriesInfo.info.name,
            year: seriesInfo.info.releaseDate ? new Date(seriesInfo.info.releaseDate).getFullYear() : null,
        });
        return response.data;
    },
    enabled: !!seriesInfo,
    retry: 0,
    retryOnMount: false,
    refetchOnWindowFocus: false,
    staleTime: 1000 * 60 * 60,
  });

  const [rpdbData, setRpdbData] = React.useState(null);

  React.useEffect(() => {
    if (tmdbData && tmdbData.id) {
      base44.functions.invoke('getRpdbData', {
        tmdb_id: tmdbData.id,
        type: 'tv'
      }).then(res => {
        setRpdbData(res.data);
      }).catch(err => {
        console.warn('Failed to fetch RPDB data:', err);
      });
    }
  }, [tmdbData]);

  const addFavoriteMutation = useMutation({
    mutationFn: () => {
      if (!userIdentifier || !seriesInfo) return Promise.reject('Missing required data');
      const categoryId = urlParams.get('categoryId') || '';
      const categoryName = urlParams.get('categoryName') || '';

      return base44.entities.FavoriteSeries.create({
        user_email: userIdentifier,
        series_id: seriesId,
        playlist_id: playlistId,
        series_info: {
          name: seriesInfo.info.name,
          cover: seriesInfo.info.cover,
          category_id: categoryId,
          category_name: categoryName
        }
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries(['favoriteSeries']);
    }
  });

  const removeFavoriteMutation = useMutation({
    mutationFn: () => {
      const fav = favoriteSeries?.find(f => f.series_id === seriesId);
      if (!fav) return Promise.reject('Favorite not found');
      return base44.entities.FavoriteSeries.delete(fav.id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries(['favoriteSeries']);
    }
  });

  const isFavorite = favoriteSeries?.some(f => f.series_id === seriesId);
  const isMutating = addFavoriteMutation.isPending || removeFavoriteMutation.isPending;

  const toggleFavorite = () => {
    if (isFavorite) {
      removeFavoriteMutation.mutate();
    } else {
      addFavoriteMutation.mutate();
    }
  };

  const constructEpisodeUrl = (host, username, password, streamId, extension) => {
    let fullHost = host;
    if (!/^https?:\/\//i.test(host)) {
      fullHost = `http://${host}`;
    }
    const u = new URL(fullHost);
    return `${u.protocol}//${u.host}/series/${username}/${password}/${streamId}.${extension}`;
  }

  React.useEffect(() => {
    const initializeCast = () => {
      if (window.chrome && window.chrome.cast && window.chrome.cast.isAvailable) {
        const cast = window.chrome.cast;
        const sessionRequest = new cast.SessionRequest('CC1AD845');
        
        const apiConfig = new cast.ApiConfig(
          sessionRequest,
          (session) => {
            console.log('✅ Cast session active');
            setCasting(true);
            
            session.addUpdateListener((isAlive) => {
              if (!isAlive) {
                console.log('🛑 Cast session ended');
                setCasting(false);
              }
            });
          },
          (status) => {
            if (status === cast.ReceiverAvailability.AVAILABLE) {
              setCastAvailable(true);
              console.log('✅ Chromecast available');
            } else if (status === cast.ReceiverAvailability.UNAVAILABLE) {
              setCastAvailable(false);
              console.log('❌ No Chromecast found');
            }
          },
          cast.AutoJoinPolicy.ORIGIN_SCOPED
        );
        
        cast.initialize(apiConfig, 
          () => console.log('✅ Cast API initialized'),
          (error) => console.error('❌ Cast init error:', error)
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
      if (window.chrome && window.chrome.cast && window.chrome.cast.isAvailable) {
        try {
          const castContext = window.chrome.cast.framework?.CastContext?.getInstance();
          if (castContext) {
            const session = castContext.getCurrentSession();
            if (session) {
              console.log('Ending cast session on unmount');
              session.endSession(true);
            }
          }
        } catch (e) {
          console.log('No active cast session to end');
        }
      }
    };
  }, []);

  const isLoading = isLoadingSeries;

  if (isLoading || !playlist) return <div className="p-8 text-white text-center flex items-center justify-center min-h-screen gap-3 text-xl"><Loader2 className="animate-spin" />Loading Series Details...</div>;
  if (!seriesInfo) return <div className="p-8 text-white">Series data could not be loaded.</div>

  const seasons = seriesInfo.episodes || {};

  const displayData = {
      name: tmdbData?.name || seriesInfo.info.name,
      poster: rpdbData?.poster_url || (tmdbData?.poster_path ? `https://image.tmdb.org/t/p/w500${tmdbData.poster_path}` : seriesInfo.info.cover),
      plot: tmdbData?.overview || seriesInfo.info.plot,
      rating: rpdbData?.rpdb_rating || rpdbData?.tmdb_rating,
      cast: tmdbData?.credits?.cast || [],
      genres: tmdbData?.genres || [],
  };

  const handleCastEpisode = (episode) => {
    if (!window.chrome || !window.chrome.cast || !window.chrome.cast.isAvailable || !playlist) {
      alert('Cast API not ready. Please wait and try again.');
      return;
    }

    const cast = window.chrome.cast;
    const episodeUrl = constructEpisodeUrl(playlist.host, playlist.username, playlist.password, episode.id, episode.container_extension);
    
    console.log('🎬 Starting cast for episode:', { title: episode.title, url: episodeUrl });
    
    cast.requestSession(
      (session) => {
        console.log('✅ Cast session created:', session.sessionId);
        
        let contentType = 'video/mp4';
        if (episode.container_extension === 'mkv') {
          contentType = 'video/x-matroska';
        } else if (episode.container_extension === 'avi') {
          contentType = 'video/x-msvideo';
        } else if (episode.container_extension === 'webm') {
          contentType = 'video/webm';
        }
        
        console.log('📺 Media info:', { contentType, extension: episode.container_extension });
        
        const mediaInfo = new cast.media.MediaInfo(episodeUrl, contentType);
        mediaInfo.metadata = new cast.media.GenericMediaMetadata();
        mediaInfo.metadata.title = episode.title;
        mediaInfo.metadata.subtitle = displayData.name;
        
        if (displayData.poster) {
          try {
            mediaInfo.metadata.images = [new cast.media.Image(displayData.poster)];
          } catch (e) {
            console.warn('Could not add cover image:', e);
          }
        }
        
        mediaInfo.streamType = cast.media.StreamType.BUFFERED;
        
        const request = new cast.media.LoadRequest(mediaInfo);
        request.autoplay = true;
        
        session.loadMedia(
          request,
          (media) => {
            console.log('✅ Episode loaded on Chromecast');
            setCasting(true);
            
            media.addUpdateListener((isAlive) => {
              if (!isAlive) {
                console.log('🛑 Media session ended');
                setCasting(false);
              }
            });
          },
          (error) => {
            console.error('❌ Cast error:', error);
            setCasting(false);
            
            let errorMessage = 'Failed to cast episode. ';
            if (error.code === 'LOAD_FAILED') {
              errorMessage += `The format (${episode.container_extension}) may not be supported by Chromecast.`;
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

  return (
    <div className="min-h-screen p-4 md:p-8">
      <div className="max-w-4xl mx-auto">
        <div className="flex justify-between items-center mb-6">
          <Button variant="ghost" onClick={() => navigate(-1)} className="text-cyan-300 hover:text-white hover:bg-blue-500/20">
            <ArrowLeft className="w-4 h-4 mr-2" />
            Back
          </Button>
          
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
              <Cast className="w-4 h-4 mr-2" />
              Casting
            </Button>
          )}
        </div>

        <div className="flex flex-col md:flex-row gap-8 items-start mb-8">
            <motion.div initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} className="w-full md:w-1/3 relative">
                <div className="aspect-[2/3] bg-gray-800 rounded-xl overflow-hidden shadow-lg relative">
                    {displayData.poster ? <img src={displayData.poster} alt={displayData.name} className="w-full h-full object-cover" /> : <div className="w-full h-full flex items-center justify-center"><Tv className="w-24 h-24 text-cyan-500"/></div>}
                    {displayData.rating && (
                      <div className="absolute top-3 right-3">
                        <RatingBadge rating={displayData.rating} size="lg" />
                      </div>
                    )}
                </div>
            </motion.div>
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{delay: 0.1}} className="w-full md:w-2/3">
                <h1 className="text-4xl md:text-5xl font-bold text-white mb-4">{displayData.name}</h1>
                <p className="text-cyan-300 text-lg mb-4">{Object.keys(seasons).length} seasons</p>
                <Button
                  size="lg"
                  variant="outline"
                  onClick={toggleFavorite}
                  className={`border-blue-500/30 hover:bg-blue-500/20 mb-4 w-full sm:w-auto transition-all ${isFavorite ? 'text-yellow-400 border-yellow-400/40' : 'text-cyan-300'}`}
                  disabled={isMutating}
                >
                  <Star className={`w-5 h-5 mr-2 transition-all ${isFavorite ? 'fill-yellow-400 text-yellow-400' : ''}`} />
                  {isMutating ? 'Updating...' : (isFavorite ? 'Remove from Favorites' : 'Add to Favorites')}
                </Button>
                <p className="text-gray-400 mt-4 line-clamp-6">{displayData.plot}</p>

                {(rpdbData || tmdbData) && (
                  <Accordion type="single" collapsible className="my-6">
                    <AccordionItem value="ratings" className="border-blue-500/20">
                      <AccordionTrigger className="text-white hover:text-cyan-300 py-3">
                        <div className="flex items-center gap-2">
                          <Star className="w-4 h-4" />
                          <span>Rating Details</span>
                        </div>
                      </AccordionTrigger>
                      <AccordionContent className="pt-2 pb-4">
                        <div className="space-y-3">
                          {rpdbData?.rpdb_rating && (
                            <div className="flex items-center justify-between bg-gray-800/30 p-3 rounded-lg">
                              <span className="text-gray-300">RPDB Rating</span>
                              <div className="flex items-center gap-2">
                                <RatingBadge rating={rpdbData.rpdb_rating} size="md" />
                              </div>
                            </div>
                          )}
                          {rpdbData?.tmdb_rating && (
                            <div className="flex items-center justify-between bg-gray-800/30 p-3 rounded-lg">
                              <span className="text-gray-300">TMDB Rating</span>
                              <div className="flex items-center gap-2">
                                <RatingBadge rating={rpdbData.tmdb_rating} size="md" />
                              </div>
                            </div>
                          )}
                          {seriesInfo.info.rating && (
                            <div className="flex items-center justify-between bg-gray-800/30 p-3 rounded-lg">
                              <span className="text-gray-300">Provider Rating</span>
                              <div className="flex items-center gap-2">
                                <RatingBadge rating={seriesInfo.info.rating} size="md" />
                              </div>
                            </div>
                          )}
                        </div>
                      </AccordionContent>
                    </AccordionItem>
                  </Accordion>
                )}

                <div className="mt-6">
                    <h3 className="text-lg font-semibold text-white mb-2 flex items-center gap-2"><Users className="w-5 h-5" />Cast</h3>
                    {tmdbError && (
                        <p className="text-sm text-yellow-500 mb-2">Could not fetch cast info from TMDB: {tmdbError.message}</p>
                    )}
                    {displayData.cast.length > 0 ? (
                        <div className="flex flex-wrap gap-2">
                            {displayData.cast.slice(0,10).map(actor => (
                                <Link key={actor.id} to={createPageUrl(`CastSearch?playlistId=${playlistId}&actorName=${encodeURIComponent(actor.name.trim())}&actorId=${actor.id}`)}>
                                    <Badge variant="secondary" className="cursor-pointer hover:bg-blue-500/30 transition-colors">{actor.name.trim()}</Badge>
                                </Link>
                            ))}
                        </div>
                    ) : (
                         <p className="text-sm text-gray-500">Cast information not available for this title.</p>
                    )}
                </div>
            </motion.div>
        </div>

        <Accordion type="single" collapsible className="w-full" defaultValue="season-1">
          {Object.entries(seasons).sort(([a], [b]) => parseInt(a) - parseInt(b)).map(([seasonNum, episodes]) => (
            <AccordionItem key={seasonNum} value={`season-${seasonNum}`} className="border-blue-500/20">
              <AccordionTrigger className="hover:no-underline text-2xl font-semibold text-white py-6">Season {seasonNum}</AccordionTrigger>
              <AccordionContent>
                <div className="flex flex-col gap-2">
                  {episodes.map((ep, index) => {
                    const episodeUrl = constructEpisodeUrl(playlist.host, playlist.username, playlist.password, ep.id, ep.container_extension);
                    const episodePlayerUrl = createPageUrl(`Player?playlistId=${playlistId}&channelUrl=${encodeURIComponent(episodeUrl)}&channelName=${encodeURIComponent(ep.title)}&containerExtension=${ep.container_extension}&contentType=episode&seriesId=${seriesId}${displayData.poster ? `&coverImage=${encodeURIComponent(displayData.poster)}` : ''}`);
                    
                    return (
                        <motion.div 
                          key={index}
                          whileHover={{ backgroundColor: 'rgba(59, 130, 246, 0.2)' }} 
                          className="flex items-center justify-between p-4 rounded-lg transition-colors group"
                        >
                          <Link to={episodePlayerUrl} className="flex-1">
                            <p className="text-gray-300">{ep.title}</p>
                          </Link>
                          <div className="flex items-center gap-2">
                            <Link to={episodePlayerUrl}>
                              <Button variant="ghost" size="icon" className="text-cyan-400 hover:bg-blue-500/20">
                                <Play className="w-5 h-5"/>
                              </Button>
                            </Link>
                            {castAvailable && (
                              <Button 
                                variant="ghost" 
                                size="icon" 
                                onClick={() => handleCastEpisode(ep)}
                                className="text-cyan-400 hover:bg-blue-500/20 opacity-0 group-hover:opacity-100 transition-opacity"
                                title="Cast to TV"
                              >
                                <Cast className="w-5 h-5"/>
                              </Button>
                            )}
                          </div>
                        </motion.div>
                    )
                  })}
                </div>
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>

        <RecommendedForYou 
            playlistId={playlistId} 
            currentContentId={seriesId}
            currentName={displayData.name}
            currentPlot={displayData.plot}
        />
      </div>
    </div>
  );
}