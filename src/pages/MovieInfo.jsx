import React from "react";
import { base44 } from "@/api/base44Client";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { ArrowLeft, Play, Film, Star, Users, Loader2, ChevronLeft, ChevronRight } from "lucide-react";
import { motion } from "framer-motion";
import RatingBadge from "@/components/RatingBadge";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";
import FranchiseBoxSet from "@/components/FranchiseBoxSet";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

const fetchVodInfo = async (playlistId, vodId) => {
    const playlist = getPlaylistFromLocal(playlistId);
    if (!playlist) throw new Error("Playlist not found");

    // Handle Plex content
    if (vodId.startsWith('plex_')) {
        const plexRatingKey = vodId.replace('plex_', '');
        const { data } = await base44.functions.invoke('plexProxy', {
            action: 'get_metadata',
            ratingKey: plexRatingKey
        });
        
        // Convert Plex format to Xtream-like format for compatibility
        return {
            info: {
                name: data.title,
                movie_image: data.thumb,
                plot: data.summary,
                rating: data.rating,
                releaseDate: data.originallyAvailableAt,
                duration_secs: Math.round((data.duration || 0) / 1000),
                backdrop: data.art
            },
            movie_data: {
                stream_id: vodId,
                container_extension: 'mkv',
                stream_url: data.streamUrl
            },
            source: 'plex'
        };
    }

    const { data } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host,
        username: playlist.username,
        password: playlist.password,
        params: { action: 'get_vod_info', vod_id: vodId }
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
                    params: { action: 'get_vod_streams' }
                });

                if (!data || data.length === 0) {
                    setRecommendations([]);
                    setIsLoadingRecs(false);
                    return;
                }

                const watchedIds = new Set(watchHistory.map(item => item.content_id));
                const unwatched = data.filter(item => {
                    const itemId = String(item.stream_id);
                    return itemId !== currentContentId && !watchedIds.has(itemId);
                });

                const aiPrompt = `Based on the movie "${currentName}"${currentPlot ? ` with plot: "${currentPlot}"` : ''}, find the 10 most similar movies from this list.

Consider:
- Similar genres and themes
- Similar plot elements
- Similar tone and style
- Movies from the same franchise or series

Available movies (format: name):
${unwatched.slice(0, 200).map(m => m.name).join('\n')}

Return ONLY the exact movie names that best match, in order of relevance.`;

                const aiResult = await base44.integrations.Core.InvokeLLM({
                    prompt: aiPrompt,
                    response_json_schema: {
                        type: "object",
                        properties: {
                            recommendations: {
                                type: "array",
                                items: { type: "string" },
                                description: "List of exact movie names from the provided list"
                            }
                        },
                        required: ["recommendations"]
                    }
                });

                const recommendedNames = aiResult.recommendations || [];
                const matched = [];
                
                for (const name of recommendedNames) {
                    const movie = unwatched.find(m => 
                        m.name.toLowerCase().trim() === name.toLowerCase().trim()
                    );
                    if (movie && matched.length < 10) {
                        matched.push(movie);
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
                            <span className="text-white">Finding similar movies...</span>
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
                                    const targetUrl = createPageUrl(`MovieInfo?playlistId=${playlistId}&vodId=${item.stream_id}`);

                                    return (
                                        <div key={index} className="relative group flex-shrink-0" style={{ width: '140px' }}>
                                            <Link to={targetUrl}>
                                                <motion.div 
                                                    whileHover={{scale: 1.05}} 
                                                    className="h-full"
                                                >
                                                    <div className="bg-gray-900/50 rounded-lg overflow-hidden group-hover:bg-gray-900/70 transition-all">
                                                        <div className="aspect-[2/3] bg-gray-900 relative">
                                                            {item.stream_icon ? (
                                                                <img 
                                                                    src={item.stream_icon} 
                                                                    alt={item.name} 
                                                                    className="w-full h-full object-cover"
                                                                    onError={(e) => {
                                                                        e.target.style.display = 'none';
                                                                        e.target.nextSibling.style.display = 'flex';
                                                                    }}
                                                                />
                                                            ) : null}
                                                            <div className={`w-full h-full ${item.stream_icon ? 'hidden' : 'flex'} items-center justify-center absolute inset-0 bg-gray-800`}>
                                                                <Film className="w-10 h-10 text-cyan-400" />
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
                                    );
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

export default function MovieInfo() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');
  const vodId = urlParams.get('vodId');
  const categoryId = urlParams.get('categoryId');
  const categoryName = urlParams.get('categoryName');

  const { data: playlist } = useQuery({
      queryKey: ['playlist', playlistId],
      queryFn: () => getPlaylistFromLocal(playlistId),
      enabled: !!playlistId
  });

  const userIdentifier = playlist?.username;

  const { data: favoriteMovies, isLoading: isLoadingFavorites } = useQuery({
    queryKey: ['favoriteMovies', userIdentifier, playlistId],
    queryFn: () => base44.entities.FavoriteMovie.filter({ user_email: userIdentifier, playlist_id: playlistId }),
    enabled: !!userIdentifier && !!playlistId,
    initialData: [],
  });

  const { data: vodInfo, isLoading: isLoadingVod } = useQuery({
    queryKey: ['vod-info', playlistId, vodId],
    queryFn: () => fetchVodInfo(playlistId, vodId),
    enabled: !!playlistId && !!vodId,
  });

  const { data: tmdbData, isLoading: isLoadingTmdb } = useQuery({
    queryKey: ['tmdb-details', 'movie', vodInfo?.info?.name],
    queryFn: async () => {
        const response = await base44.functions.invoke('getTmdbDetails', {
            type: 'movie',
            name: vodInfo.info.name,
            year: vodInfo.info.releaseDate ? new Date(vodInfo.info.releaseDate).getFullYear() : null,
        });
        return response.data;
    },
    enabled: !!vodInfo,
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
        type: 'movie'
      }).then(res => {
        setRpdbData(res.data);
      }).catch(err => {
        console.warn('Failed to fetch RPDB data:', err);
      });
    }
  }, [tmdbData]);

  const addFavoriteMutation = useMutation({
    mutationFn: () => {
      if (!userIdentifier || !vodInfo) return Promise.reject('Missing required data');
      return base44.entities.FavoriteMovie.create({
        user_email: userIdentifier,
        movie_id: vodId,
        playlist_id: playlistId,
        movie_info: {
          name: vodInfo.info.name,
          cover: vodInfo.info.movie_image,
          category_id: categoryId || '',
          category_name: categoryName || '',
          container_extension: vodInfo.movie_data?.container_extension || vodInfo.info.container_extension
        }
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries(['favoriteMovies']);
    }
  });

  const removeFavoriteMutation = useMutation({
    mutationFn: () => {
      const fav = favoriteMovies?.find(f => f.movie_id === vodId);
      if (!fav) return Promise.reject('Favorite not found');
      return base44.entities.FavoriteMovie.delete(fav.id);
    },
    onSuccess: () => {
      queryClient.invalidateQueries(['favoriteMovies']);
    }
  });

  const isFavorite = favoriteMovies?.some(f => f.movie_id === vodId);
  const isMutating = addFavoriteMutation.isPending || removeFavoriteMutation.isPending;

  const toggleFavorite = () => {
    if (isFavorite) {
      removeFavoriteMutation.mutate();
    } else {
      addFavoriteMutation.mutate();
    }
  };

  const isLoading = isLoadingVod;

  if (isLoading || !playlist) return <div className="p-8 text-white text-center flex items-center justify-center min-h-screen gap-3 text-xl"><Loader2 className="animate-spin" />Loading Movie Details...</div>;
  if (!vodInfo) return <div className="p-8 text-white">Movie data could not be loaded.</div>;

  const constructStreamUrl = (host, username, password, streamId, containerExtension) => {
    let fullHost = host;
    if (!/^https?:\/\//i.test(host)) {
      fullHost = `http://${host}`;
    }
    const u = new URL(fullHost);
    return `${u.protocol}//${u.host}/movie/${username}/${password}/${streamId}.${containerExtension}`;
  };

  // Use Plex stream URL if available, otherwise construct Xtream URL
  const streamUrl = vodInfo.source === 'plex' && vodInfo.movie_data?.stream_url
    ? vodInfo.movie_data.stream_url
    : constructStreamUrl(
        playlist.host,
        playlist.username,
        playlist.password,
        vodId,
        vodInfo.movie_data?.container_extension || vodInfo.info.container_extension
      );

  const displayData = {
      name: tmdbData?.title || vodInfo.info.name,
      poster: rpdbData?.poster_url || (tmdbData?.poster_path ? `https://image.tmdb.org/t/p/w500${tmdbData.poster_path}` : vodInfo.info.movie_image),
      backdrop: tmdbData?.backdrop_path ? `https://image.tmdb.org/t/p/original${tmdbData.backdrop_path}` : null,
      plot: tmdbData?.overview || vodInfo.info.plot,
      rating: rpdbData?.rpdb_rating || rpdbData?.tmdb_rating || vodInfo.info.rating,
      cast: tmdbData?.credits?.cast || [],
      genres: tmdbData?.genres || [],
      runtime: tmdbData?.runtime || vodInfo.info.duration_secs ? Math.round(vodInfo.info.duration_secs / 60) : null,
      releaseDate: tmdbData?.release_date || vodInfo.info.releaseDate,
  };

  const playerUrl = createPageUrl(`Player?playlistId=${playlistId}&channelUrl=${encodeURIComponent(streamUrl)}&channelName=${encodeURIComponent(displayData.name)}&containerExtension=${vodInfo.movie_data?.container_extension || vodInfo.info.container_extension}&contentType=movie${displayData.poster ? `&coverImage=${encodeURIComponent(displayData.poster)}` : ''}`);

  return (
    <div className="min-h-screen">
      {displayData.backdrop && (
        <div className="absolute top-0 left-0 w-full h-[60vh] opacity-20">
          <img src={displayData.backdrop} alt="" className="w-full h-full object-cover" />
          <div className="absolute inset-0 bg-gradient-to-b from-transparent via-black/50 to-black" />
        </div>
      )}
      
      <div className="relative p-4 md:p-8">
        <div className="max-w-6xl mx-auto">
          <Button variant="ghost" onClick={() => navigate(-1)} className="mb-6 text-cyan-300 hover:text-white hover:bg-blue-500/20">
            <ArrowLeft className="w-4 h-4 mr-2" />
            Back
          </Button>

          <div className="flex flex-col md:flex-row gap-8 items-start">
            <motion.div initial={{ opacity: 0, x: -20 }} animate={{ opacity: 1, x: 0 }} className="w-full md:w-1/3 relative">
              <div className="aspect-[2/3] bg-gray-800 rounded-xl overflow-hidden shadow-2xl relative">
                {displayData.poster ? (
                  <img src={displayData.poster} alt={displayData.name} className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full flex items-center justify-center">
                    <Film className="w-24 h-24 text-cyan-500"/>
                  </div>
                )}
                {displayData.rating && (
                  <div className="absolute top-3 right-3">
                    <RatingBadge rating={displayData.rating} size="lg" />
                  </div>
                )}
              </div>
            </motion.div>

            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{delay: 0.1}} className="w-full md:w-2/3">
              <h1 className="text-4xl md:text-5xl font-bold text-white mb-4">{displayData.name}</h1>
              
              <div className="flex flex-wrap gap-3 mb-6">
                {displayData.releaseDate && (
                  <Badge variant="secondary" className="text-sm">
                    {new Date(displayData.releaseDate).getFullYear()}
                  </Badge>
                )}
                {displayData.runtime && (
                  <Badge variant="secondary" className="text-sm">
                    {displayData.runtime} min
                  </Badge>
                )}
                {displayData.genres.map(genre => (
                  <Badge key={genre.id} variant="outline" className="border-blue-500/30 text-cyan-300">
                    {genre.name}
                  </Badge>
                ))}
              </div>

              <div className="flex gap-3 mb-6 flex-wrap">
                <Link to={playerUrl}>
                  <Button size="lg" className="bg-gradient-to-r from-blue-600 to-blue-800 hover:from-blue-700 hover:to-blue-900 shadow-lg">
                    <Play className="w-5 h-5 mr-2" />
                    Play Movie
                  </Button>
                </Link>
                <Button
                  size="lg"
                  variant="outline"
                  onClick={toggleFavorite}
                  className={`border-blue-500/30 hover:bg-blue-500/20 transition-all ${isFavorite ? 'text-yellow-400 border-yellow-400/40' : 'text-cyan-300'}`}
                  disabled={isMutating}
                >
                  <Star className={`w-5 h-5 mr-2 transition-all ${isFavorite ? 'fill-yellow-400 text-yellow-400' : ''}`} />
                  {isMutating ? 'Updating...' : (isFavorite ? 'Remove from Favorites' : 'Add to Favorites')}
                </Button>
              </div>

              <p className="text-gray-300 text-lg leading-relaxed mb-6">{displayData.plot}</p>

              {(rpdbData || tmdbData) && (
                <Accordion type="single" collapsible className="mb-6">
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
                        {vodInfo.info.rating && (
                          <div className="flex items-center justify-between bg-gray-800/30 p-3 rounded-lg">
                            <span className="text-gray-300">Provider Rating</span>
                            <div className="flex items-center gap-2">
                              <RatingBadge rating={vodInfo.info.rating} size="md" />
                            </div>
                          </div>
                        )}
                      </div>
                    </AccordionContent>
                  </AccordionItem>
                </Accordion>
              )}

              {displayData.cast.length > 0 && (
                <div className="mt-6">
                  <h3 className="text-lg font-semibold text-white mb-3 flex items-center gap-2">
                    <Users className="w-5 h-5" />
                    Cast
                  </h3>
                  <div className="flex flex-wrap gap-2">
                    {displayData.cast.slice(0, 10).map(actor => (
                      <Link key={actor.id} to={createPageUrl(`CastSearch?playlistId=${playlistId}&actorName=${encodeURIComponent(actor.name.trim())}&actorId=${actor.id}`)}>
                        <Badge variant="secondary" className="cursor-pointer hover:bg-blue-500/30 transition-colors text-sm">
                          {actor.name.trim()}
                        </Badge>
                      </Link>
                    ))}
                  </div>
                </div>
              )}
            </motion.div>
          </div>

          <RecommendedForYou 
            playlistId={playlistId} 
            currentContentId={vodId} 
            currentName={displayData.name}
            currentPlot={displayData.plot}
          />

          <FranchiseBoxSet 
            playlistId={playlistId}
            currentMovieId={vodId}
            currentMovieName={displayData.name}
            playlist={playlist}
          />
        </div>
      </div>
    </div>
  );
}