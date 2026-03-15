import React, { useState } from "react";
import { base44 } from "@/api/base44Client";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ArrowLeft, Play, Search, Film, WifiOff } from "lucide-react";
import { motion } from "framer-motion";
import MultiRatingBadge from "@/components/MultiRatingBadge";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

const fetchMovies = async (playlistId, categoryId) => {
    const playlist = getPlaylistFromLocal(playlistId);
    if (!playlist) throw new Error("Playlist not found");

    // Handle Plex categories - fetch all with increased limit
    if (categoryId.startsWith('plex_')) {
        const plexSectionId = categoryId.replace('plex_', '');
        const { data } = await base44.functions.invoke('plexProxy', {
            action: 'get_library_items',
            sectionId: plexSectionId,
            offset: 0,
            limit: 500
        });
        
        const items = Array.isArray(data) ? data : [];
        return items.map(item => ({
            stream_id: `plex_${item.ratingKey}`,
            name: item.title,
            stream_icon: item.thumb,
            rating: item.rating,
            year: item.year,
            source: 'plex',
            plexRatingKey: item.ratingKey,
            category_id: categoryId
        }));
    }

    if (categoryId === 'recently_added') {
        const { data } = await base44.functions.invoke('xtreamProxy', {
            host: playlist.host,
            username: playlist.username,
            password: playlist.password,
            params: { action: 'get_vod_streams' }
        });
        
        if (!Array.isArray(data)) return [];
        
        const sorted = data.sort((a, b) => {
            const dateA = a.added ? parseInt(a.added) : 0;
            const dateB = b.added ? parseInt(b.added) : 0;
            return dateB - dateA;
        });
        
        return sorted.slice(0, 30);
    }

    const { data } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host,
        username: playlist.username,
        password: playlist.password,
        params: { action: 'get_vod_streams', category_id: categoryId }
    });
    return Array.isArray(data) ? data : [];
}

export default function Movies() {
  const navigate = useNavigate();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');
  const categoryId = urlParams.get('categoryId');
  const categoryName = decodeURIComponent(urlParams.get('categoryName'));
  const [searchQuery, setSearchQuery] = useState("");
  const [sortBy, setSortBy] = useState("newest");

  const { data: playlist } = useQuery({
      queryKey: ['playlist', playlistId],
      queryFn: () => getPlaylistFromLocal(playlistId),
      enabled: !!playlistId
  });

  const { data: movies, isLoading, error } = useQuery({
    queryKey: ['vod_streams', playlistId, categoryId],
    queryFn: () => fetchMovies(playlistId, categoryId),
    enabled: !!playlistId && !!categoryId,
  });

  const filteredMovies = movies?.filter(movie =>
    movie.name.toLowerCase().includes(searchQuery.toLowerCase())
  ) || [];

  const sortedMovies = [...filteredMovies].sort((a, b) => {
    switch (sortBy) {
      case "az":
        return a.name.localeCompare(b.name);
      case "newest":
        return b.stream_id - a.stream_id;
      case "default":
      default:
        return 0;
    }
  });
  
  if (isLoading || !playlist) {
      return (
        <div className="min-h-screen p-4 md:p-8">
            <div className="max-w-7xl mx-auto">
                <h1 className="text-3xl md:text-4xl font-bold text-white mb-8">{categoryName}</h1>
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                    {[...Array(12)].map((_, i) => (
                    <Card key={i} className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 animate-pulse">
                        <CardContent className="p-3">
                        <div className="aspect-[2/3] bg-gray-700 rounded mb-3" />
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
        <Card className="bg-slate-800/50 backdrop-blur-xl border-red-500/30">
          <CardContent className="p-12 text-center">
            <WifiOff className="w-16 h-16 text-red-400 mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-white mb-2">Error Loading Movies</h3>
            <p className="text-gray-400 max-w-sm">{error.message}</p>
          </CardContent>
        </Card>
      </div>
  );

  return (
    <div className="min-h-screen p-3 sm:p-4 md:p-6 lg:p-8 xl:p-16">
      <div className="max-w-7xl mx-auto">
        <Button
          variant="ghost"
          onClick={() => navigate(createPageUrl(`MovieCategories?playlistId=${playlistId}`))}
          className="mb-4 sm:mb-6 text-cyan-300 hover:text-white hover:bg-blue-500/20 text-sm sm:text-base"
        >
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Movie Categories
        </Button>

        <div className="mb-6 sm:mb-8">
          <h1 className="text-2xl sm:text-3xl md:text-4xl xl:text-6xl 2xl:text-7xl font-bold text-white mb-2">{categoryName}</h1>
          <p className="text-sm sm:text-base xl:text-xl text-cyan-300">{movies?.length || 0} movies available</p>
        </div>

        <div className="mb-4 sm:mb-6 flex flex-col sm:flex-row gap-3 sm:gap-4 justify-between">
          <div className="relative w-full max-w-md xl:max-w-2xl flex-grow">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-4 h-4 sm:w-5 sm:h-5 xl:w-6 xl:h-6 text-gray-400" />
            <Input
              type="text"
              placeholder="Search movies..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-9 sm:pl-10 xl:pl-12 xl:h-14 xl:text-xl text-sm sm:text-base bg-slate-800/50 border-blue-500/30 text-white placeholder:text-gray-500 focus:border-blue-500 w-full"
            />
          </div>
          <Select value={sortBy} onValueChange={setSortBy}>
            <SelectTrigger className="w-full sm:w-48 xl:w-64 xl:h-14 xl:text-xl text-sm sm:text-base bg-slate-800/50 border-blue-500/30 text-white">
              <SelectValue placeholder="Sort by" />
            </SelectTrigger>
            <SelectContent className="bg-slate-900 border-blue-500/30">
              <SelectItem value="newest" className="text-white hover:bg-blue-500/20 text-sm sm:text-base xl:text-lg">Newest First</SelectItem>
              <SelectItem value="az" className="text-white hover:bg-blue-500/20 text-sm sm:text-base xl:text-lg">A-Z</SelectItem>
              <SelectItem value="default" className="text-white hover:bg-blue-500/20 text-sm sm:text-base xl:text-lg">Default</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 2xl:grid-cols-8 gap-3 sm:gap-4 xl:gap-6">
            {sortedMovies.map((movie, index) => {
                const ratings = movie.rating ? {
                  imdb: (parseFloat(movie.rating) || 0).toFixed(1),
                  audience: Math.round((parseFloat(movie.rating) || 0) * 10),
                  critic: null
                } : null;
                
                // Use the movie's actual category_id if it exists, otherwise use the current categoryId
                const movieCategoryId = movie.category_id || categoryId;
                const movieCategoryName = movie.category_name || categoryName;
                
                return (
                  <Link
                    key={movie.stream_id}
                    to={createPageUrl(`MovieInfo?playlistId=${playlistId}&vodId=${movie.stream_id}&categoryId=${movieCategoryId}&categoryName=${encodeURIComponent(movieCategoryName)}`)}
                  >
                    <motion.div
                      initial={{ opacity: 0, y: 20 }}
                      animate={{ opacity: 1, y: 0 }}
                      transition={{ delay: index * 0.02 }}
                      whileHover={{ scale: 1.05 }}
                      whileTap={{ scale: 0.95 }}
                    >
                      <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 transition-all cursor-pointer group overflow-hidden h-full flex flex-col">
                       <CardContent className="p-2 sm:p-3 xl:p-4 relative flex-grow flex flex-col">
                         <div className="aspect-[2/3] bg-gray-900/50 rounded-md mb-2 sm:mb-3 flex items-center justify-center overflow-hidden relative">
                             <img
                               src={movie.stream_icon}
                               alt={movie.name}
                               className="w-full h-full object-cover"
                               loading="lazy"
                               onError={(e) => { e.target.onerror = null; e.target.style.display='none'; e.target.nextSibling.style.display='flex' }}
                             />
                           <div style={{ display: 'none', position: 'absolute', inset: 0, alignItems: 'center', justifyContent: 'center' }}>
                             <Film className="w-12 h-12 xl:w-16 xl:h-16 text-cyan-400" />
                           </div>
                           <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                             <div className="w-10 h-10 sm:w-12 sm:h-12 xl:w-16 xl:h-16 bg-blue-600 rounded-full flex items-center justify-center">
                               <Play className="w-5 h-5 sm:w-6 sm:h-6 xl:w-8 xl:h-8 text-white ml-0.5 sm:ml-1" />
                             </div>
                           </div>
                         </div>
                         <h3 className="text-xs sm:text-sm xl:text-base 2xl:text-lg font-semibold text-white truncate leading-tight mb-1 sm:mb-2">{movie.name}</h3>
                         {ratings && <MultiRatingBadge ratings={ratings} size="sm" />}
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