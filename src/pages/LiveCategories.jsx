import React, { useState } from "react";
import { base44 } from "@/api/base44Client";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ArrowLeft, ChevronRight, Tv, Search, WifiOff } from "lucide-react";
import { motion } from "framer-motion";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

const getCategoryGroup = (name) => {
    const upperName = name.toUpperCase();
    if (upperName.includes('PPV')) return 'PPV';
    if (upperName.startsWith('SPORTS')) return 'SPORTS';
    if (upperName.includes(' US') || upperName.startsWith('US ') || upperName.includes('(US)')) return 'US';
    if (upperName.includes(' UK') || upperName.startsWith('UK ') || upperName.includes('(UK)')) return 'UK';
    if (upperName.includes(' CA') || upperName.startsWith('CA ') || upperName.includes('(CA)')) return 'CA';
    return 'INT';
};

const groupNames = {
  SPORTS: 'Sports', PPV: 'PPV & Events', US: 'USA', UK: 'United Kingdom', CA: 'Canada', INT: 'International'
};

const fetchCategories = async (playlistId) => {
    const playlist = getPlaylistFromLocal(playlistId);
    if (!playlist) throw new Error("Playlist not found");

    const { data } = await base44.functions.invoke('xtreamProxy', {
        host: playlist.host,
        username: playlist.username,
        password: playlist.password,
        params: { action: 'get_live_categories' }
    });
    // Ensure the function always returns an array, even if the API returns null or not an array
    return Array.isArray(data) ? data : [];
}

export default function LiveCategories() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');
  const group = urlParams.get('group');
  const [searchQuery, setSearchQuery] = useState("");

  const { data: categories, isLoading, error } = useQuery({
    queryKey: ['live_categories', playlistId],
    queryFn: () => fetchCategories(playlistId),
    enabled: !!playlistId,
  });

  const filteredCategories = categories?.filter(c => {
    const groupMatch = group ? getCategoryGroup(c.category_name) === group : true;
    const searchMatch = c.category_name.toLowerCase().includes(searchQuery.toLowerCase());
    return groupMatch && searchMatch;
  }) || [];

  // New: Dynamic page title based on group
  const pageTitle = group ? `${groupNames[group]} Categories` : 'Live TV Categories';

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
        <Button variant="ghost" onClick={() => navigate(createPageUrl(`LiveTVMain?playlistId=${playlistId}`))} className="mb-6 text-cyan-300 hover:text-white hover:bg-blue-500/20">
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Main Categories
        </Button>
        <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-8 gap-4">
            <div>
                <h1 className="text-3xl md:text-4xl xl:text-6xl 2xl:text-7xl font-bold text-white mb-2">{pageTitle}</h1>
                <p className="text-cyan-300 xl:text-xl">Browse channels by category</p>
            </div>
            <div className="relative flex-grow md:max-w-xs xl:max-w-md w-full">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 w-5 h-5 xl:w-6 xl:h-6 text-gray-400" />
                <Input type="text" placeholder="Search categories..." value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} className="pl-10 xl:pl-12 xl:h-14 xl:text-xl bg-slate-800/50 border-blue-500/30 text-white" />
            </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-4 xl:gap-6">
          {filteredCategories.map((category, index) => (
            <Link key={category.category_id} to={createPageUrl(`Channels?playlistId=${playlistId}&categoryId=${category.category_id}&categoryName=${encodeURIComponent(category.category_name)}&group=${group || ''}`)}>
              <motion.div initial={{ opacity: 0, y: 20 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.05 }} whileHover={{ scale: 1.05 }} whileTap={{ scale: 0.95 }}>
                <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 transition-all cursor-pointer group overflow-hidden">
                  <CardContent className="p-6 xl:p-8 flex items-center justify-between relative">
                    <div className="flex items-center gap-4 xl:gap-6 overflow-hidden">
                      <div className="w-12 h-12 xl:w-16 xl:h-16 bg-gradient-to-br from-blue-500/20 to-blue-800/20 rounded-xl flex items-center justify-center flex-shrink-0">
                        <Tv className="w-6 h-6 xl:w-8 xl:h-8 text-cyan-400" />
                      </div>
                      <div className="overflow-hidden">
                        <h3 className="text-lg xl:text-2xl font-semibold text-white mb-1 truncate">{category.category_name}</h3>
                      </div>
                    </div>
                    <ChevronRight className="w-5 h-5 xl:w-7 xl:h-7 text-cyan-400 group-hover:translate-x-1 transition-transform flex-shrink-0 ml-2" />
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