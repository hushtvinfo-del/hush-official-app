
import React, { useState } from "react";
import { base44 } from "@/api/base44Client";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ArrowLeft, Play, Search, Clapperboard, WifiOff } from "lucide-react";
import { motion } from "framer-motion";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

const fetchSeries = async (playlistId, categoryId) => {
    const playlist = getPlaylistFromLocal(playlistId);
    if (!playlist) throw new Error("Playlist not found");

    if (categoryId === 'recently_added') {
        const { data } = await base44.functions.invoke('xtreamProxy', {
            host: playlist.host,
            username: playlist.username,
            password: playlist.password,
            params: { action: 'get_series' }
        });
        
        if (!Array.isArray(data)) return [];
        
        const sorted = data.sort((a, b) => {
            const dateA = a.last_modified ? parseInt(a.last_modified) : 0;
            const dateB = b.last_modified ? parseInt(b.last_modified) : 0;
            return dateB - dateA;
        });
        
        return sorted.slice(0, 30);
    }

    const { data } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host,
        username: playlist.username,
        password: playlist.password,
        params: { action: 'get_series', category_id: categoryId }
    });
    return Array.isArray(data) ? data : [];
}

export default function SeriesGrid() {
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

  const { data: series, isLoading, error } = useQuery({
    queryKey: ['series_streams', playlistId, categoryId],
    queryFn: () => fetchSeries(playlistId, categoryId),
    enabled: !!playlistId && !!categoryId,
  });

  const filteredSeries = series?.filter(s =>
    s.name.toLowerCase().includes(searchQuery.toLowerCase())
  ) || [];

  const sortedSeries = [...filteredSeries].sort((a, b) => {
    switch (sortBy) {
      case "az":
        return a.name.localeCompare(b.name);
      case "newest":
        return b.series_id - a.series_id;
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
                    <Card key={i} className="bg-gray-800/50 backdrop-blur-xl border-orange-500/30 animate-pulse">
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
        <Card className="bg-gray-800/50 backdrop-blur-xl border-red-500/30">
          <CardContent className="p-12 text-center">
            <WifiOff className="w-16 h-16 text-red-400 mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-white mb-2">Error Loading Series</h3>
            <p className="text-gray-400 max-w-sm">{error.message}</p>
          </CardContent>
        </Card>
      </div>
  );

  return (
    <div className="min-h-screen p-4 md:p-8">
      <div className="max-w-7xl mx-auto">
        <Button
          variant="ghost"
          onClick={() => navigate(createPageUrl(`SeriesCategories?playlistId=${playlistId}`))}
          className="mb-6 text-orange-300 hover:text-white hover:bg-orange-500/20"
        >
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Series Categories
        </Button>

        <div className="mb-8">
          <h1 className="text-3xl md:text-4xl font-bold text-white mb-2">{categoryName}</h1>
          <p className="text-orange-300">{series?.length || 0} series available</p>
        </div>

        <div className="mb-6 flex flex-col sm:flex-row gap-4 justify-between">
          <div className="relative max-w-md flex-grow">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
            <Input
              type="text"
              placeholder="Search series..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10 bg-gray-800/50 border-orange-500/30 text-white placeholder:text-gray-500 focus:border-orange-500"
            />
          </div>
          <Select value={sortBy} onValueChange={setSortBy}>
            <SelectTrigger className="w-full sm:w-48 bg-gray-800/50 border-orange-500/30 text-white">
              <SelectValue placeholder="Sort by" />
            </SelectTrigger>
            <SelectContent className="bg-gray-900 border-orange-500/30">
              <SelectItem value="newest" className="text-white hover:bg-orange-500/20">Newest First</SelectItem>
              <SelectItem value="az" className="text-white hover:bg-orange-500/20">A-Z</SelectItem>
              <SelectItem value="default" className="text-white hover:bg-orange-500/20">Default</SelectItem>
            </SelectContent>
          </Select>
        </div>

        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
            {sortedSeries.map((show, index) => {
              // Use the series's actual category_id if it exists, otherwise use the current categoryId
              const seriesCategoryId = show.category_id || categoryId;
              const seriesCategoryName = show.category_name || categoryName;
              
              return (
                <Link
                  key={show.series_id}
                  to={createPageUrl(`SeriesDetails?playlistId=${playlistId}&seriesId=${show.series_id}&categoryId=${seriesCategoryId}&categoryName=${encodeURIComponent(seriesCategoryName)}`)}
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
                        <div className="aspect-[2/3] bg-gray-900/50 rounded-md mb-3 flex items-center justify-center overflow-hidden relative">
                            <img
                              src={show.cover}
                              alt={show.name}
                              className="w-full h-full object-cover"
                              loading="lazy"
                              onError={(e) => { e.target.onerror = null; e.target.style.display='none'; e.target.nextSibling.style.display='flex' }}
                            />
                          <div style={{ display: 'none', position: 'absolute', inset: 0, alignItems: 'center', justifyContent: 'center' }}>
                            <Clapperboard className="w-12 h-12 text-orange-400" />
                          </div>
                          <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                            <div className="w-12 h-12 bg-orange-600 rounded-full flex items-center justify-center">
                              <Play className="w-6 h-6 text-white ml-1" />
                            </div>
                          </div>
                        </div>
                        <h3 className="text-sm font-semibold text-white truncate leading-tight">{show.name}</h3>
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
