import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { base44 } from '@/api/base44Client';
import { Link, useNavigate } from 'react-router-dom';
import { createPageUrl } from '@/utils';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { ArrowLeft, Globe, Medal, Ticket, WifiOff } from 'lucide-react';
import { motion } from 'framer-motion';

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { 
        console.error("Error parsing playlists from local storage:", e);
        return null; 
    }
};

// Main categories definition
const mainCategoryConfig = {
  SPORTS: { name: 'Sports', icon: Medal },
  PPV: { name: 'PPV & Events', icon: Ticket },
  US: { name: 'USA', icon: () => <span className="text-5xl">🇺🇸</span> },
  UK: { name: 'United Kingdom', icon: () => <span className="text-5xl">🇬🇧</span> },
  CA: { name: 'Canada', icon: () => <span className="text-5xl">🇨🇦</span> },
  INT: { name: 'International', icon: Globe },
};

// Define the display order
const orderedCategoryKeys = ['PPV', 'SPORTS', 'CA', 'US', 'UK', 'INT'];

export default function LiveTVMain() {
  const navigate = useNavigate();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');

  const { data: categories, isLoading, error } = useQuery({
    queryKey: ['live_categories_all', playlistId],
    queryFn: async () => {
        if (!playlistId) {
            throw new Error("Playlist ID is missing.");
        }
        const playlist = getPlaylistFromLocal(playlistId);
        if (!playlist) {
            throw new Error("Playlist not found in local storage.");
        }

        const { data } = await base44.functions.invoke('xtreamProxy', {
            host: playlist.host, username: playlist.username, password: playlist.password,
            params: { action: 'get_live_categories' }
        });
        return Array.isArray(data) ? data : [];
    },
    enabled: !!playlistId,
  });

  if (isLoading) return <div className="p-8 text-white text-center">Loading Main Categories...</div>;
  if (error) return (
    <div className="min-h-screen p-4 md:p-8 flex items-center justify-center">
      <Card className="bg-slate-800/50 backdrop-blur-xl border-red-500/30">
        <CardContent className="p-12 text-center">
          <WifiOff className="w-16 h-16 text-red-400 mx-auto mb-4" />
          <h3 className="text-xl font-semibold text-white mb-2">Error Loading Data</h3>
          <p className="text-gray-400">{error.message}</p>
        </CardContent>
      </Card>
    </div>
  );

  return (
    <div className="min-h-screen p-4 md:p-8">
      <div className="max-w-7xl mx-auto">
        <Button variant="ghost" onClick={() => navigate(createPageUrl(`MainMenu?playlistId=${playlistId}`))} className="mb-6 text-cyan-300 hover:text-white hover:bg-blue-500/20">
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Main Menu
        </Button>
        <div className="mb-8">
            <h1 className="text-3xl md:text-4xl font-bold text-white mb-2">Live TV</h1>
            <p className="text-cyan-300">Select a main category to browse</p>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
            {orderedCategoryKeys.map((key, index) => {
                const config = mainCategoryConfig[key];
                if (!config) return null; 

                return (
                    <Link to={createPageUrl(`LiveCategories?playlistId=${playlistId}&group=${key}`)} key={key}>
                        <motion.div initial={{ opacity: 0, y: 50 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: index * 0.1 }}>
                            <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 transition-all group overflow-hidden text-center h-full flex flex-col">
                                <CardContent className="p-8 flex flex-col items-center justify-center flex-grow">
                                    <motion.div whileHover={{ scale: 1.05 }} className="flex flex-col items-center">
                                        {typeof config.icon === 'function' ? config.icon() : <config.icon className="w-20 h-20 text-cyan-400 group-hover:text-cyan-300 transition-colors" />}
                                        <h2 className="text-3xl font-bold text-white mt-6 mb-2">{config.name}</h2>
                                    </motion.div>
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