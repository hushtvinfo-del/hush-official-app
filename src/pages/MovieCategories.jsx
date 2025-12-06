import React, { useState } from "react";
import { base44 } from "@/api/base44Client";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ArrowLeft, ChevronRight, Film, Search, WifiOff, Sparkles } from "lucide-react";
import { motion } from "framer-motion";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

const fetchCategories = async (playlistId) => {
    const playlist = getPlaylistFromLocal(playlistId);
    if (!playlist) throw new Error("Playlist not found");

    const { data } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host,
        username: playlist.username,
        password: playlist.password,
        params: { action: 'get_vod_categories' }
    });
    return Array.isArray(data) ? data : [];
}

export default function MovieCategories() {
  const navigate = useNavigate();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');
  const [searchQuery, setSearchQuery] = useState("");

  const { data: categories, isLoading, error } = useQuery({
    queryKey: ['movie_categories', playlistId],
    queryFn: () => fetchCategories(playlistId),
    enabled: !!playlistId,
  });

  const filteredCategories = categories?.filter(c => c.category_name.toLowerCase().includes(searchQuery.toLowerCase())) || [];

  const handleSearch = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(createPageUrl(`GlobalSearch?playlistId=${playlistId}&q=${encodeURIComponent(searchQuery.trim())}&mode=ai&contentType=movie`));
    }
  };

  if (isLoading && !categories) return <div className="p-8 text-white text-center">Loading Categories...</div>;
  if (error) return (
      <div className="min-h-screen p-4 md:p-8 flex items-center justify-center">
        <Card className="bg-gray-800/50 backdrop-blur-xl border-red-500/30">
          <CardContent className="p-12 text-center">
            <WifiOff className="w-16 h-16 text-red-400 mx-auto mb-4" />
            <h3 className="text-xl font-semibold text-white mb-2">Error Loading Categories</h3>
            <p className="text-gray-400">{error.message}</p>
          </CardContent>
        </Card>
      </div>
  );

  return (
    <div className="min-h-screen p-4 md:p-8">
      <div className="max-w-7xl mx-auto">
        <Button
          variant="ghost"
          onClick={() => navigate(createPageUrl(`MainMenu?playlistId=${playlistId}`))}
          className="mb-6 text-orange-300 hover:text-white hover:bg-orange-500/20"
        >
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Main Menu
        </Button>

        <h1 className="text-3xl md:text-4xl font-bold text-white mb-8">Movie Categories</h1>

        <form onSubmit={handleSearch} className="mb-6">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
            <Input
              type="text"
              placeholder="Search for movies..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10 bg-gray-800/50 border-orange-500/30 text-white placeholder:text-gray-500 focus:border-orange-500"
            />
          </div>
        </form>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          <Link to={createPageUrl(`Movies?playlistId=${playlistId}&categoryId=recently_added&categoryName=${encodeURIComponent('Recently Added')}`)}>
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0 }} whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
              <Card className="bg-gradient-to-br from-orange-600/30 to-amber-600/30 backdrop-blur-xl border-orange-500/50 hover:border-orange-400/70 transition-all cursor-pointer group overflow-hidden">
                <CardContent className="p-6 flex items-center justify-between relative">
                  <div className="flex items-center gap-4 overflow-hidden">
                    <div className="w-12 h-12 bg-gradient-to-br from-orange-500 to-amber-600 rounded-xl flex items-center justify-center flex-shrink-0 shadow-lg">
                      <Sparkles className="w-6 h-6 text-white" />
                    </div>
                    <div className="overflow-hidden">
                      <h3 className="text-lg font-semibold text-white mb-1 truncate">Recently Added</h3>
                      <p className="text-xs text-orange-200">Latest 30 movies</p>
                    </div>
                  </div>
                  <ChevronRight className="w-5 h-5 text-orange-300 group-hover:translate-x-1 transition-transform flex-shrink-0 ml-2" />
                </CardContent>
              </Card>
            </motion.div>
          </Link>

          {filteredCategories.map((category, index) => (
            <Link key={category.category_id} to={createPageUrl(`Movies?playlistId=${playlistId}&categoryId=${category.category_id}&categoryName=${encodeURIComponent(category.category_name)}`)}>
              <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: (index + 1) * 0.05 }} whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
                <Card className="bg-gray-800/50 backdrop-blur-xl border-orange-500/30 hover:border-orange-500/60 transition-all cursor-pointer group overflow-hidden">
                  <CardContent className="p-6 flex items-center justify-between relative">
                    <div className="flex items-center gap-4 overflow-hidden">
                      <div className="w-12 h-12 bg-gradient-to-br from-orange-500/20 to-orange-800/20 rounded-xl flex items-center justify-center flex-shrink-0">
                        <Film className="w-6 h-6 text-orange-400" />
                      </div>
                      <div className="overflow-hidden">
                        <h3 className="text-lg font-semibold text-white mb-1 truncate">{category.category_name}</h3>
                      </div>
                    </div>
                    <ChevronRight className="w-5 h-5 text-orange-400 group-hover:translate-x-1 transition-transform flex-shrink-0 ml-2" />
                  </CardContent>
                </Card>
              </motion.div>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
}