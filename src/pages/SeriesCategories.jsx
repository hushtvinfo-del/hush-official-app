import React, { useState } from "react";
import { base44 } from "@/api/base44Client";
import { useQuery } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ArrowLeft, ChevronRight, Clapperboard, Search, WifiOff, Sparkles } from "lucide-react";
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

    // Fetch Xtream categories
    const { data: xtreamData } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host,
        username: playlist.username,
        password: playlist.password,
        params: { action: 'get_series_categories' }
    });
    const xtreamCategories = Array.isArray(xtreamData) ? xtreamData : [];

    // Add single VIP category for all Plex content
    try {
        const { data: plexData } = await base44.functions.invoke('plexProxy', {
            action: 'get_libraries'
        });
        
        if (plexData?.shows && plexData.shows.length > 0) {
            const vipCategory = {
                category_id: 'plex_all',
                category_name: 'VIP',
                source: 'plex',
                plexSectionIds: plexData.shows.map(lib => lib.key)
            };
            return [vipCategory, ...xtreamCategories];
        }
    } catch (plexError) {
        console.log('Plex not available:', plexError);
    }
    
    return xtreamCategories;
}

export default function SeriesCategories() {
  const navigate = useNavigate();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');
  const [searchQuery, setSearchQuery] = useState("");

  const { data: categories, isLoading, error } = useQuery({
    queryKey: ['series_categories', playlistId],
    queryFn: () => fetchCategories(playlistId),
    enabled: !!playlistId,
  });

  const filteredCategories = categories?.filter(c => c.category_name.toLowerCase().includes(searchQuery.toLowerCase())) || [];

  const handleSearch = (e) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      navigate(createPageUrl(`GlobalSearch?playlistId=${playlistId}&q=${encodeURIComponent(searchQuery.trim())}&mode=ai&contentType=series`));
    }
  };

  if (isLoading && !categories) return <div className="p-8 text-white text-center">Loading Categories...</div>;
  if (error) return (
      <div className="min-h-screen p-4 md:p-8 flex items-center justify-center">
        <Card className="bg-slate-800/50 backdrop-blur-xl border-red-500/30">
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
          className="mb-6 text-cyan-300 hover:text-white hover:bg-blue-500/20"
        >
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Main Menu
        </Button>

        <h1 className="text-3xl md:text-4xl font-bold text-white mb-8">Series Categories</h1>

        <form onSubmit={handleSearch} className="mb-6">
          <div className="relative max-w-md">
            <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 text-gray-400" />
            <Input
              type="text"
              placeholder="Search for series..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="pl-10 bg-slate-800/50 border-blue-500/30 text-white placeholder:text-gray-500 focus:border-blue-500"
            />
          </div>
        </form>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-4">
          <Link to={createPageUrl(`SeriesGrid?playlistId=${playlistId}&categoryId=recently_added&categoryName=${encodeURIComponent('Recently Added')}`)}>
            <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0 }} whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
              <Card className="bg-gradient-to-br from-blue-600/30 to-cyan-600/30 backdrop-blur-xl border-blue-500/50 hover:border-cyan-400/70 transition-all cursor-pointer group overflow-hidden">
                <CardContent className="p-6 flex items-center justify-between relative">
                  <div className="flex items-center gap-4 overflow-hidden">
                    <div className="w-12 h-12 bg-gradient-to-br from-blue-500 to-cyan-600 rounded-xl flex items-center justify-center flex-shrink-0 shadow-lg">
                      <Sparkles className="w-6 h-6 text-white" />
                    </div>
                    <div className="overflow-hidden">
                      <h3 className="text-lg font-semibold text-white mb-1 truncate">Recently Added</h3>
                      <p className="text-xs text-cyan-200">Latest 30 series</p>
                    </div>
                  </div>
                  <ChevronRight className="w-5 h-5 text-cyan-300 group-hover:translate-x-1 transition-transform flex-shrink-0 ml-2" />
                </CardContent>
              </Card>
            </motion.div>
          </Link>

          {filteredCategories.map((category, index) => (
            <Link key={category.category_id} to={createPageUrl(`SeriesGrid?playlistId=${playlistId}&categoryId=${category.category_id}&categoryName=${encodeURIComponent(category.category_name)}`)}>
              <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: (index + 1) * 0.05 }} whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
                <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 transition-all cursor-pointer group overflow-hidden">
                  <CardContent className="p-6 flex items-center justify-between relative">
                    <div className="flex items-center gap-4 overflow-hidden">
                      <div className={`w-12 h-12 rounded-xl flex items-center justify-center flex-shrink-0 ${category.source === 'plex' ? 'bg-gradient-to-br from-amber-500/30 to-yellow-600/30' : 'bg-gradient-to-br from-blue-500/20 to-blue-800/20'}`}>
                        {category.source === 'plex' ? (
                          <span className="text-sm font-bold text-amber-400">VIP</span>
                        ) : (
                          <Clapperboard className="w-6 h-6 text-cyan-400" />
                        )}
                      </div>
                      <div className="overflow-hidden">
                        <h3 className="text-lg font-semibold text-white mb-1 truncate">{category.category_name}</h3>
                      </div>
                    </div>
                    <ChevronRight className="w-5 h-5 text-cyan-400 group-hover:translate-x-1 transition-transform flex-shrink-0 ml-2" />
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