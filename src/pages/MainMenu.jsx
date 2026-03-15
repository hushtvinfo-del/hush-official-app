import React from "react";
import { base44 } from "@/api/base44Client";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { ArrowLeft, Tv, Film, Clapperboard, Play, Star, Search, RefreshCw, Loader2, X, ChevronLeft, ChevronRight } from "lucide-react";
import { motion, AnimatePresence } from "framer-motion";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { ScrollArea, ScrollBar } from "@/components/ui/scroll-area";

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

function ContinueWatchingCard({ playlistId }) {
    const queryClient = useQueryClient();
    const [itemToDelete, setItemToDelete] = React.useState(null);
    const scrollContainerRef = React.useRef(null);

    const { data: playlist } = useQuery({
        queryKey: ['playlist', playlistId],
        queryFn: () => getPlaylistFromLocal(playlistId),
        enabled: !!playlistId,
    });

    const userIdentifier = playlist?.username;

    const { data: progressItems, isLoading } = useQuery({
        queryKey: ['watchProgress', userIdentifier, playlistId],
        queryFn: async () => {
            const items = await base44.entities.WatchProgress.filter({ user_email: userIdentifier, playlist_id: playlistId }, '-updated_date', 10);
            return items;
        },
        enabled: !!userIdentifier && !!playlistId,
        initialData: [],
    });

    const deleteProgressMutation = useMutation({
        mutationFn: (itemId) => base44.entities.WatchProgress.delete(itemId),
        onSuccess: () => {
            queryClient.invalidateQueries(['watchProgress']);
            setItemToDelete(null);
        }
    });
    
    if (isLoading) {
        return null;
    }
    
    if (!progressItems || progressItems.length === 0) {
        return null;
    }
    
    if (!playlist) {
        return null;
    }

    const constructStreamUrl = (host, username, password, streamId, containerExtension, type) => {
        let fullHost = host;
        if (!/^https?:\/\//i.test(host)) fullHost = `http://${fullHost}`;
        const u = new URL(fullHost);
        const endpoint = type === 'movie' ? 'movie' : 'series';
        return `${u.protocol}//${u.host}/${endpoint}/${username}/${password}/${streamId}.${containerExtension}`;
    }

    const handleLongPress = (item, e) => {
        e.preventDefault();
        setItemToDelete(item);
    };

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
        <>
            <motion.div 
                initial={{ opacity: 0, y: 20 }} 
                animate={{ opacity: 1, y: 0 }} 
                transition={{ delay: 0 }}
                className="mb-6"
            >
                <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 overflow-hidden">
                    <CardContent className="p-4">
                        <div className="flex items-center justify-between mb-3">
                            <h2 className="text-lg font-bold text-white flex items-center gap-2">
                                <Play className="w-5 h-5 text-cyan-400" />
                                Continue Watching
                            </h2>
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
                                    {progressItems.map(item => {
                                        const progressPercent = (item.progress / item.duration) * 100;
                                        const { name, cover_image, container_extension } = item.content_info;
                                        const streamUrl = constructStreamUrl(playlist.host, playlist.username, playlist.password, item.content_id, container_extension, item.content_type);
                                        
                                        const targetUrl = createPageUrl(`Player?playlistId=${playlistId}&channelUrl=${encodeURIComponent(streamUrl)}&channelName=${encodeURIComponent(name)}&containerExtension=${container_extension}&contentType=${item.content_type}&t=${item.progress}${cover_image ? `&coverImage=${encodeURIComponent(cover_image)}` : ''}`);

                                        return (
                                            <div key={item.id} className="relative group flex-shrink-0" style={{ width: '140px' }}>
                                                <Link to={targetUrl}>
                                                    <motion.div 
                                                        whileHover={{scale: 1.05}} 
                                                        className="h-full"
                                                        onContextMenu={(e) => handleLongPress(item, e)}
                                                        onTouchStart={(e) => {
                                                            const timer = setTimeout(() => handleLongPress(item, e), 500);
                                                            e.target.ontouchend = () => clearTimeout(timer);
                                                            e.target.ontouchmove = () => clearTimeout(timer);
                                                        }}
                                                    >
                                                        <div className="bg-gray-900/50 rounded-lg overflow-hidden group-hover:bg-gray-900/70 transition-all">
                                                            <div className="aspect-[2/3] bg-gray-900 relative">
                                                                {cover_image ? (
                                                                    <img 
                                                                        src={cover_image} 
                                                                        alt={name} 
                                                                        className="w-full h-full object-cover"
                                                                        onError={(e) => {
                                                                            e.target.style.display = 'none';
                                                                            e.target.nextElementSibling.style.display = 'flex';
                                                                        }}
                                                                    />
                                                                ) : null}
                                                                <div className={`w-full h-full ${cover_image ? 'hidden' : 'flex'} items-center justify-center absolute inset-0 bg-gray-800`}>
                                                                   <Film className="w-10 h-10 text-cyan-400" />
                                                                </div>
                                                                <div className="absolute inset-0 bg-black/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                                                                   <div className="w-10 h-10 bg-blue-600 rounded-full flex items-center justify-center">
                                                                       <Play className="w-5 h-5 text-white ml-0.5" />
                                                                   </div>
                                                                </div>
                                                                </div>
                                                                <div className="p-2">
                                                                <p className="text-xs font-semibold text-white truncate">{name}</p>
                                                                <div className="w-full bg-gray-700 rounded-full h-1 mt-1">
                                                                   <div className="bg-blue-600 h-1 rounded-full" style={{ width: `${progressPercent}%` }}></div>
                                                                </div>
                                                                </div>
                                                        </div>
                                                    </motion.div>
                                                </Link>
                                                <Button
                                                    size="icon"
                                                    variant="ghost"
                                                    className="absolute -top-1 -right-1 h-6 w-6 bg-red-600 hover:bg-red-700 rounded-full opacity-0 group-hover:opacity-100 transition-opacity z-10 shadow-lg"
                                                    onClick={(e) => {
                                                        e.preventDefault();
                                                        e.stopPropagation();
                                                        setItemToDelete(item);
                                                    }}
                                                >
                                                    <X className="w-3 h-3 text-white" />
                                                </Button>
                                            </div>
                                        )
                                    })}
                                </div>
                                <ScrollBar orientation="horizontal" className="h-2" />
                            </ScrollArea>
                        </div>
                    </CardContent>
                </Card>
            </motion.div>

            <AlertDialog open={!!itemToDelete} onOpenChange={(open) => !open && setItemToDelete(null)}>
                <AlertDialogContent className="bg-slate-900 border-blue-500/30">
                    <AlertDialogHeader>
                        <AlertDialogTitle className="text-white">Clear Watch Progress?</AlertDialogTitle>
                        <AlertDialogDescription className="text-gray-400">
                            Remove "{itemToDelete?.content_info?.name}" from Continue Watching? This action cannot be undone.
                        </AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogCancel className="bg-gray-800 text-white hover:bg-gray-700">Cancel</AlertDialogCancel>
                        <AlertDialogAction 
                            className="bg-red-600 hover:bg-red-700 text-white"
                            onClick={() => deleteProgressMutation.mutate(itemToDelete.id)}
                            disabled={deleteProgressMutation.isPending}
                        >
                            {deleteProgressMutation.isPending ? 'Removing...' : 'Remove'}
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </>
    );
}

function RefreshToast({ message, show }) {
    return (
        <AnimatePresence>
            {show && (
                <motion.div
                    initial={{ opacity: 0, y: -50 }}
                    animate={{ opacity: 1, y: 0 }}
                    exit={{ opacity: 0, y: -50 }}
                    className="fixed top-8 left-1/2 transform -translate-x-1/2 z-50"
                >
                    <div className="bg-blue-600 text-white px-6 py-3 rounded-lg shadow-lg flex items-center gap-3">
                        <Loader2 className="w-5 h-5 animate-spin" />
                        <span className="font-medium">{message}</span>
                    </div>
                </motion.div>
            )}
        </AnimatePresence>
    );
}

export default function MainMenu() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const urlParams = new URLSearchParams(window.location.search);
  const playlistId = urlParams.get('playlistId');
  const { data: user } = useQuery({ queryKey: ['user'], queryFn: () => base44.auth.me() });

  const [refreshingKey, setRefreshingKey] = React.useState(null);

  const { data: playlist, isLoading } = useQuery({
    queryKey: ['playlist', playlistId],
    queryFn: () => getPlaylistFromLocal(playlistId),
    enabled: !!playlistId,
  });
  
  const { data: accountInfo } = useQuery({
    queryKey: ['accountInfo', playlistId],
    queryFn: async () => {
        if (!playlist) return null;
        const { data } = await base44.functions.invoke('xtreamProxy', {
            host: playlist.host,
            username: playlist.username,
            password: playlist.password,
            params: {}
        });
        return data.user_info;
    },
    enabled: !!playlist,
  });

  const menuItems = [
    { name: 'Live TV', icon: Tv, link: createPageUrl(`LiveTVMain?playlistId=${playlistId}`), refreshKey: 'live_categories' },
    { name: 'Movies', icon: Film, link: createPageUrl(`MovieCategories?playlistId=${playlistId}`), refreshKey: 'movie_categories' },
    { name: 'Series', icon: Clapperboard, link: createPageUrl(`SeriesCategories?playlistId=${playlistId}`), refreshKey: 'series_categories' },
    { name: 'Favorites', icon: Star, link: createPageUrl(`Favorites?playlistId=${playlistId}`) },
  ];
  
  const searchItem = { name: 'Smart Search', icon: Search, link: createPageUrl(`GlobalSearch?playlistId=${playlistId}`) };

  const handleRefresh = async (refreshKey) => {
    setRefreshingKey(refreshKey);
    await queryClient.invalidateQueries({ queryKey: [refreshKey, playlistId] });
    setTimeout(() => {
      setRefreshingKey(null);
    }, 1500);
  };

  if (isLoading) {
      return <div className="text-white p-8 text-center">Loading Menu...</div>
  }
  
  const expiryDate = accountInfo?.exp_date ? new Date(accountInfo.exp_date * 1000).toLocaleDateString() : 'Not available';

  return (
    <div className="min-h-screen p-4 md:p-8">
      <RefreshToast 
        message="Refreshing categories..." 
        show={refreshingKey !== null} 
      />
      
      <div className="max-w-7xl mx-auto">
        <Button variant="ghost" onClick={() => navigate(createPageUrl("Dashboard"))} className="mb-6 text-cyan-300 hover:text-white hover:bg-blue-500/20">
          <ArrowLeft className="w-4 h-4 mr-2" />
          Back to Accounts
        </Button>
        <div className="mb-8 text-left">
          <h1 className="text-3xl md:text-4xl xl:text-6xl 2xl:text-7xl font-bold text-white mb-2">Welcome, {accountInfo?.username || 'User'}!</h1>
          <p className="text-cyan-300 xl:text-xl 2xl:text-2xl">Expiry: <span className="font-semibold text-cyan-200">{expiryDate}</span></p>
        </div>
        
        <ContinueWatchingCard playlistId={playlistId} />
        
        <div className="grid grid-cols-1 md:grid-cols-2 gap-8 xl:gap-10">
          {menuItems.map((item, index) => (
            <motion.div 
              key={item.name}
              initial={{ opacity: 0, y: 50 }} 
              animate={{ opacity: 1, y: 0 }} 
              transition={{ delay: (index + 1) * 0.1 }}
              className="relative"
            >
              <Link to={item.link}>
                  <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 transition-all group overflow-hidden text-center h-full flex flex-col">
                      <CardContent className="p-8 xl:p-14 2xl:p-20 flex flex-col items-center justify-center flex-grow">
                          <motion.div whileHover={{ scale: 1.05 }} className="flex flex-col items-center">
                              <item.icon className="w-24 h-24 xl:w-36 xl:h-36 2xl:w-44 2xl:h-44 text-cyan-400 group-hover:text-cyan-300 transition-colors duration-300" />
                              <h2 className="text-3xl xl:text-5xl 2xl:text-6xl font-bold text-white mt-6 mb-2">{item.name}</h2>
                          </motion.div>
                      </CardContent>
                  </Card>
              </Link>
              {item.refreshKey && (
                <Button 
                  size="icon" 
                  variant="ghost" 
                  className="absolute top-4 right-4 xl:top-6 xl:right-6 xl:w-12 xl:h-12 text-cyan-400 hover:text-white hover:bg-blue-500/20 z-10"
                  onClick={(e) => {
                    e.stopPropagation();
                    handleRefresh(item.refreshKey);
                  }}
                  disabled={refreshingKey === item.refreshKey}
                >
                  <RefreshCw className={`w-5 h-5 xl:w-7 xl:h-7 ${refreshingKey === item.refreshKey ? 'animate-spin' : ''}`} />
                </Button>
              )}
            </motion.div>
          ))}
          
          <motion.div 
            key={searchItem.name}
            initial={{ opacity: 0, y: 50 }} 
            animate={{ opacity: 1, y: 0 }} 
            transition={{ delay: (menuItems.length + 1) * 0.1 }}
            className="md:col-span-2"
          >
            <Link to={searchItem.link}>
                <Card className="bg-slate-800/50 backdrop-blur-xl border-blue-500/30 hover:border-blue-500/60 transition-all group overflow-hidden text-center h-full flex flex-col">
                    <CardContent className="p-8 xl:p-14 2xl:p-16 flex flex-col items-center justify-center flex-grow">
                        <motion.div whileHover={{ scale: 1.05 }} className="flex flex-col items-center">
                            <searchItem.icon className="w-24 h-24 xl:w-36 xl:h-36 2xl:w-44 2xl:h-44 text-cyan-400 group-hover:text-cyan-300 transition-colors duration-300" />
                            <h2 className="text-3xl xl:text-5xl 2xl:text-6xl font-bold text-white mt-6 mb-2">{searchItem.name}</h2>
                        </motion.div>
                    </CardContent>
                </Card>
            </Link>
          </motion.div>
        </div>
      </div>
    </div>
  );
}