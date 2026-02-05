import React from "react";
import { base44 } from "@/api/base44Client";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ArrowLeft, Play, Tv, Film, Clapperboard, Star } from "lucide-react";
import { motion } from "framer-motion";
import { useEpg } from "@/components/useEpg";
import {
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
} from "@/components/ui/tabs";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

export default function Favorites() {
  const navigate = useNavigate();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');

  // Removed: const { data: user } = useQuery({ queryKey: ['user'], queryFn: () => base44.auth.me() });
  
  const { data: playlist } = useQuery({
      queryKey: ['playlist', playlistId],
      queryFn: () => getPlaylistFromLocal(playlistId),
      enabled: !!playlistId
  });

  // Use playlist username instead of Base44 user
  const userIdentifier = playlist?.username;

  const { data: favoriteChannels, isLoading: isLoadingChannels } = useQuery({
      queryKey: ['favoriteChannels', userIdentifier, playlistId],
      queryFn: () => base44.entities.FavoriteChannel.filter({ user_email: userIdentifier, playlist_id: playlistId }, '-created_date'),
      enabled: !!userIdentifier && !!playlistId,
      initialData: [],
  });

  const { data: favoriteMovies, isLoading: isLoadingMovies } = useQuery({
      queryKey: ['favoriteMovies', userIdentifier, playlistId],
      queryFn: () => base44.entities.FavoriteMovie.filter({ user_email: userIdentifier, playlist_id: playlistId }, '-created_date'),
      enabled: !!userIdentifier && !!playlistId,
      initialData: [],
  });

  const { data: favoriteSeries, isLoading: isLoadingSeries } = useQuery({
      queryKey: ['favoriteSeries', userIdentifier, playlistId],
      queryFn: () => base44.entities.FavoriteSeries.filter({ user_email: userIdentifier, playlist_id: playlistId }, '-created_date'),
      enabled: !!userIdentifier && !!playlistId,
      initialData: [],
  });

  const { epgData, isLoadingEpg } = useEpg(playlist, favoriteChannels);

  const constructStreamUrl = (host, username, password, streamId) => {
    let fullHost = host;
    if (!/^https?:\/\//i.test(host)) fullHost = `http://${host}`;
    const u = new URL(fullHost);
    return `${u.protocol}//${u.host}/live/${username}/${password}/${streamId}.m3u8`;
  }

  const totalFavorites = (favoriteChannels?.length || 0) + (favoriteMovies?.length || 0) + (favoriteSeries?.length || 0);
  const isLoading = isLoadingChannels || isLoadingMovies || isLoadingSeries || !playlist;

  return (
    <div className="min-h-screen p-4 md:p-8">
      <div className="max-w-7xl mx-auto">
        <Button
          variant="ghost"
          onClick={() => navigate(createPageUrl(`MainMenu?playlistId=${playlistId}`))}
          className="mb-6 text-cyan-300 hover:text-white hover:bg-blue-500/20"
        >
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Main Menu
        </Button>

        <div className="mb-8">
          <h1 className="text-3xl md:text-4xl font-bold text-white mb-2">My Favorites</h1>
          <p className="text-cyan-300">{totalFavorites} total favorites</p>
        </div>

        {totalFavorites === 0 && !isLoading ? (
          <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30">
            <CardContent className="flex flex-col items-center justify-center py-16">
              <Star className="w-16 h-16 text-cyan-400 mb-4" />
              <h3 className="text-xl font-semibold text-white mb-2">No Favorites Yet</h3>
              <p className="text-gray-400 mb-6 text-center">Add channels, movies, and series to your favorites to see them here.</p>
            </CardContent>
          </Card>
        ) : (
          <Tabs defaultValue="channels" className="w-full">
            <TabsList className="grid w-full grid-cols-3 bg-slate-800/50 border-blue-500/30">
              <TabsTrigger value="channels" className="data-[state=active]:bg-blue-600 data-[state=active]:text-white">
                <Tv className="w-4 h-4 mr-2" />
                Channels ({favoriteChannels?.length || 0})
              </TabsTrigger>
              <TabsTrigger value="movies" className="data-[state=active]:bg-blue-600 data-[state=active]:text-white">
                <Film className="w-4 h-4 mr-2" />
                Movies ({favoriteMovies?.length || 0})
              </TabsTrigger>
              <TabsTrigger value="series" className="data-[state=active]:bg-blue-600 data-[state=active]:text-white">
                <Clapperboard className="w-4 h-4 mr-2" />
                Series ({favoriteSeries?.length || 0})
              </TabsTrigger>
            </TabsList>

            <TabsContent value="channels" className="mt-6">
              {favoriteChannels?.length === 0 ? (
                <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30">
                  <CardContent className="flex flex-col items-center justify-center py-12">
                    <Tv className="w-12 h-12 text-cyan-400 mb-3" />
                    <p className="text-gray-400">No favorite channels yet</p>
                  </CardContent>
                </Card>
              ) : (
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                  {favoriteChannels?.map((fav, index) => {
                    const streamUrl = playlist ? constructStreamUrl(playlist.host, playlist.username, playlist.password, fav.channel_id) : '';
                    const currentEpg = epgData?.[fav.channel_info.epg_channel_id];
                    return (
                      <Link
                        key={fav.id}
                        to={createPageUrl(`Guide?playlistId=${playlistId}&channelUrl=${encodeURIComponent(streamUrl)}&channelName=${encodeURIComponent(fav.channel_info.name)}&categoryId=${fav.channel_info.category_id}&categoryName=${encodeURIComponent(fav.channel_info.category_name)}&streamId=${fav.channel_id}`)}
                      >
                        <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.02 }} whileHover={{ scale: 1.05 }}>
                          <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 transition-all cursor-pointer group overflow-hidden h-full flex flex-col">
                            <CardContent className="p-3 relative flex-grow flex flex-col">
                              <div className="aspect-video bg-gray-900/50 rounded-md mb-3 flex items-center justify-center overflow-hidden relative">
                                <img src={fav.channel_info.icon} alt={fav.channel_info.name} className="w-full h-full object-contain" loading="lazy" onError={(e) => { e.target.onerror = null; e.target.style.display='none'; e.target.nextSibling.style.display='flex' }} />
                                <div style={{ display: 'none', position: 'absolute', inset: 0, alignItems: 'center', justifyContent: 'center' }}><Tv className="w-8 h-8 text-cyan-400" /></div>
                                <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center"><div className="w-12 h-12 bg-blue-600 rounded-full flex items-center justify-center"><Play className="w-6 h-6 text-white ml-1" /></div></div>
                              </div>
                              <h3 className="text-sm font-semibold text-white truncate leading-tight">{fav.channel_info.name}</h3>
                              <p className="text-xs text-gray-400 truncate">{isLoadingEpg ? '...' : (currentEpg?.title || 'EPG not available')}</p>
                            </CardContent>
                          </Card>
                        </motion.div>
                      </Link>
                    );
                  })}
                </div>
              )}
            </TabsContent>

            <TabsContent value="movies" className="mt-6">
              {favoriteMovies?.length === 0 ? (
                <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30">
                  <CardContent className="flex flex-col items-center justify-center py-12">
                    <Film className="w-12 h-12 text-cyan-400 mb-3" />
                    <p className="text-gray-400">No favorite movies yet</p>
                  </CardContent>
                </Card>
              ) : (
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                  {favoriteMovies?.map((fav, index) => (
                    <Link key={fav.id} to={createPageUrl(`MovieInfo?playlistId=${playlistId}&vodId=${fav.movie_id}`)}>
                      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.02 }} whileHover={{ scale: 1.05 }}>
                        <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 transition-all cursor-pointer group overflow-hidden h-full flex flex-col">
                          <CardContent className="p-3 relative flex-grow flex flex-col">
                            <div className="aspect-[2/3] bg-gray-900/50 rounded-md mb-3 flex items-center justify-center overflow-hidden relative">
                              <img src={fav.movie_info.cover} alt={fav.movie_info.name} className="w-full h-full object-cover" loading="lazy" onError={(e) => { e.target.onerror = null; e.target.style.display='none'; e.target.nextSibling.style.display='flex' }} />
                              <div style={{ display: 'none', position: 'absolute', inset: 0, alignItems: 'center', justifyContent: 'center' }}><Film className="w-12 h-12 text-cyan-400" /></div>
                              <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center"><div className="w-12 h-12 bg-blue-600 rounded-full flex items-center justify-center"><Play className="w-6 h-6 text-white ml-1" /></div></div>
                            </div>
                            <h3 className="text-sm font-semibold text-white truncate leading-tight">{fav.movie_info.name}</h3>
                          </CardContent>
                        </Card>
                      </motion.div>
                    </Link>
                  ))}
                </div>
              )}
            </TabsContent>

            <TabsContent value="series" className="mt-6">
              {favoriteSeries?.length === 0 ? (
                <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30">
                  <CardContent className="flex flex-col items-center justify-center py-12">
                    <Clapperboard className="w-12 h-12 text-cyan-400 mb-3" />
                    <p className="text-gray-400">No favorite series yet</p>
                  </CardContent>
                </Card>
              ) : (
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                  {favoriteSeries?.map((fav, index) => (
                    <Link key={fav.id} to={createPageUrl(`SeriesDetails?playlistId=${playlistId}&seriesId=${fav.series_id}`)}>
                      <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.02 }} whileHover={{ scale: 1.05 }}>
                        <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 transition-all cursor-pointer group overflow-hidden h-full flex flex-col">
                          <CardContent className="p-3 relative flex-grow flex flex-col">
                            <div className="aspect-[2/3] bg-gray-900/50 rounded-md mb-3 flex items-center justify-center overflow-hidden relative">
                              <img src={fav.series_info.cover} alt={fav.series_info.name} className="w-full h-full object-cover" loading="lazy" onError={(e) => { e.target.onerror = null; e.target.style.display='none'; e.target.nextSibling.style.display='flex' }} />
                              <div style={{ display: 'none', position: 'absolute', inset: 0, alignItems: 'center', justifyContent: 'center' }}><Clapperboard className="w-12 h-12 text-cyan-400" /></div>
                            </div>
                            <h3 className="text-sm font-semibold text-white truncate leading-tight">{fav.series_info.name}</h3>
                          </CardContent>
                        </Card>
                      </motion.div>
                    </Link>
                  ))}
                </div>
              )}
            </TabsContent>
          </Tabs>
        )}
      </div>
    </div>
  );
}