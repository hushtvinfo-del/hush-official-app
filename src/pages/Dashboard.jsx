import React, { useState, useEffect } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { base44 } from "@/api/base44Client";
import { Link, useNavigate } from "react-router-dom";
import { createPageUrl } from "@/utils";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Trash2, Plus, Play, User2, Loader2 } from "lucide-react";
import { motion } from "framer-motion";
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
import AddToHomeScreen from "@/components/AddToHomeScreen";

export default function Dashboard() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [playlists, setPlaylists] = useState([]);
  const [playlistToDelete, setPlaylistToDelete] = useState(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    const loadPlaylists = () => {
      try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        setPlaylists(localPlaylists);

        if (localPlaylists.length === 0) {
          navigate(createPageUrl('Welcome'), { replace: true });
        }
      } catch (error) {
        console.error('Failed to load playlists:', error);
        setPlaylists([]);
      }
    };

    loadPlaylists();
  }, [navigate]);

  const handleDeletePlaylist = async (playlist) => {
    setDeleting(true);
    
    try {
      const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
      const updatedPlaylists = localPlaylists.filter(p => p.id !== playlist.id);
      localStorage.setItem('playlists', JSON.stringify(updatedPlaylists));

      setPlaylists(updatedPlaylists);

      if (updatedPlaylists.length === 0) {
        navigate(createPageUrl('Welcome'), { replace: true });
        return;
      }

      try {
        const userIdentifier = playlist.username;

        const [channels, movies, series, progress] = await Promise.all([
          base44.entities.FavoriteChannel.filter({ user_email: userIdentifier, playlist_id: playlist.id }).catch(() => []),
          base44.entities.FavoriteMovie.filter({ user_email: userIdentifier, playlist_id: playlist.id }).catch(() => []),
          base44.entities.FavoriteSeries.filter({ user_email: userIdentifier, playlist_id: playlist.id }).catch(() => []),
          base44.entities.WatchProgress.filter({ user_email: userIdentifier, playlist_id: playlist.id }).catch(() => [])
        ]);

        const deletePromises = [];

        channels.forEach(item => deletePromises.push(base44.entities.FavoriteChannel.delete(item.id).catch(() => {})));
        movies.forEach(item => deletePromises.push(base44.entities.FavoriteMovie.delete(item.id).catch(() => {})));
        series.forEach(item => deletePromises.push(base44.entities.FavoriteSeries.delete(item.id).catch(() => {})));
        progress.forEach(item => deletePromises.push(base44.entities.WatchProgress.delete(item.id).catch(() => {})));

        await Promise.all(deletePromises);

        queryClient.invalidateQueries({ queryKey: ['favoriteChannels'] });
        queryClient.invalidateQueries({ queryKey: ['favoriteMovies'] });
        queryClient.invalidateQueries({ queryKey: ['favoriteSeries'] });
        queryClient.invalidateQueries({ queryKey: ['watchProgress'] });

      } catch (remoteError) {
        console.error('Failed to clean up remote data (non-critical):', remoteError);
      }

    } catch (error) {
      console.error('Failed to delete playlist:', error);
    } finally {
      setDeleting(false);
      setPlaylistToDelete(null);
    }
  };

  return (
    <div className="min-h-screen p-4 md:p-8">
      <div className="max-w-4xl mx-auto">
        <div className="mb-8">
          <h1 className="text-3xl md:text-4xl font-bold text-white mb-2">My Accounts</h1>
          <p className="text-orange-300">Manage your HushTV accounts</p>
        </div>

        <div className="mb-6">
          <AddToHomeScreen />
        </div>

        {playlists.length === 0 ? (
          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            className="text-center py-16"
          >
            <div className="w-24 h-24 bg-gradient-to-br from-orange-500 to-orange-700 rounded-full flex items-center justify-center mx-auto mb-6 shadow-2xl">
              <User2 className="w-12 h-12 text-white" />
            </div>
            <h3 className="text-2xl font-bold text-white mb-2">No Accounts Yet</h3>
            <p className="text-gray-400 mb-8">Add your first HushTV account to get started</p>
            <Link to={createPageUrl('AddAccount')}>
              <Button 
                size="lg" 
                className="bg-gradient-to-r from-orange-600 to-orange-800 hover:from-orange-700 hover:to-orange-900 text-white shadow-lg"
              >
                <Plus className="w-5 h-5 mr-2" />
                Add Your First Account
              </Button>
            </Link>
          </motion.div>
        ) : (
          <>
            <div className="grid gap-4 mb-6">
              {playlists.map((playlist, index) => (
                <motion.div
                  key={playlist.id}
                  initial={{ opacity: 0, y: 20 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ delay: index * 0.1 }}
                >
                  <Card className="bg-gradient-to-br from-gray-800/50 to-gray-900/50 backdrop-blur-xl border-orange-500/30 hover:border-orange-500/60 transition-all group overflow-hidden">
                    <CardContent className="p-0">
                      <div className="flex flex-col md:flex-row md:items-center">
                        {/* Account Info Section */}
                        <div className="flex-1 p-4 md:p-6">
                          <div className="flex items-center gap-3">
                            <div className="w-12 h-12 bg-gradient-to-br from-orange-500 to-orange-700 rounded-full flex items-center justify-center shadow-lg flex-shrink-0">
                              <User2 className="w-6 h-6 text-white" />
                            </div>
                            <div className="flex-1 min-w-0">
                              <h3 className="text-lg md:text-xl font-bold text-white truncate">{playlist.name}</h3>
                              <p className="text-gray-400 text-xs md:text-sm flex items-center gap-2">
                                <span className="w-2 h-2 bg-green-400 rounded-full"></span>
                                Active Account
                              </p>
                            </div>
                          </div>
                        </div>

                        {/* Actions Section */}
                        <div className="flex items-center gap-2 p-4 md:p-6 border-t md:border-t-0 md:border-l border-orange-500/20">
                          <Link to={createPageUrl(`MainMenu?playlistId=${playlist.id}`)} className="flex-1 md:flex-none">
                            <Button className="w-full bg-gradient-to-r from-orange-600 to-orange-800 hover:from-orange-700 hover:to-orange-900 text-white shadow-lg">
                              <Play className="w-4 h-4 md:w-5 md:h-5 mr-2" />
                              <span className="whitespace-nowrap">Watch Now</span>
                            </Button>
                          </Link>

                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => setPlaylistToDelete(playlist)}
                            className="text-gray-400 hover:text-red-400 hover:bg-red-500/20 flex-shrink-0"
                          >
                            <Trash2 className="w-5 h-5" />
                          </Button>
                        </div>
                      </div>
                    </CardContent>
                  </Card>
                </motion.div>
              ))}
            </div>

            <Link to={createPageUrl('AddAccount')}>
              <Button 
                size="lg" 
                className="w-full bg-gradient-to-r from-orange-600 to-orange-800 hover:from-orange-700 hover:to-orange-900 text-white shadow-lg group"
              >
                <Plus className="w-5 h-5 mr-2 group-hover:rotate-90 transition-transform" />
                Add New Account
              </Button>
            </Link>
          </>
        )}
      </div>

      <AlertDialog open={!!playlistToDelete} onOpenChange={(open) => !open && setPlaylistToDelete(null)}>
        <AlertDialogContent className="bg-gray-900 border-orange-500/30 max-w-md mx-4">
          <AlertDialogHeader>
            <AlertDialogTitle className="text-white text-xl">Delete Account?</AlertDialogTitle>
            <AlertDialogDescription className="text-gray-400">
              Are you sure you want to remove <span className="text-white font-semibold">"{playlistToDelete?.name}"</span>? 
              <br /><br />
              This will delete all favorites and watch progress. This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter className="flex-col sm:flex-row gap-2">
            <AlertDialogCancel className="bg-gray-800 text-white hover:bg-gray-700 border-gray-700 w-full sm:w-auto" disabled={deleting}>
              Cancel
            </AlertDialogCancel>
            <AlertDialogAction 
              className="bg-gradient-to-r from-red-600 to-red-800 hover:from-red-700 hover:to-red-900 text-white w-full sm:w-auto"
              onClick={() => handleDeletePlaylist(playlistToDelete)}
              disabled={deleting}
            >
              {deleting ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" />
                  Deleting...
                </>
              ) : (
                <>
                  <Trash2 className="w-4 h-4 mr-2" />
                  Delete Account
                </>
              )}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}