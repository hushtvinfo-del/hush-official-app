import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { base44 } from '@/api/base44Client';
import { Link, useNavigate } from 'react-router-dom';
import { createPageUrl } from '@/utils';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { ArrowLeft, Film, Clapperboard, Loader2, User } from 'lucide-react';
import { motion } from 'framer-motion';

const getPlaylistFromLocal = (playlistId) => {
    try {
        const localPlaylists = JSON.parse(localStorage.getItem('playlists') || '[]');
        return localPlaylists.find(p => p.id === playlistId);
    } catch(e) { return null; }
};

const fetchCastContent = async (playlist, actorName, actorId) => {
    if (!playlist || !actorName) return { movies: [], series: [] };

    console.log('🎬 Fetching content for actor:', actorName, 'TMDB ID:', actorId);

    // Fetch library
    const [vodRes, seriesRes] = await Promise.all([
        base44.functions.invoke('xtreamProxy', {
            host: playlist.host,
            username: playlist.username,
            password: playlist.password,
            params: { action: 'get_vod_streams' }
        }),
        base44.functions.invoke('xtreamProxy', {
            host: playlist.host,
            username: playlist.username,
            password: playlist.password,
            params: { action: 'get_series' }
        })
    ]);

    const allMovies = vodRes.data || [];
    const allSeries = seriesRes.data || [];

    console.log('📚 Library:', allMovies.length, 'movies,', allSeries.length, 'series');

    // Get actor's filmography from TMDB
    const tmdbResponse = await base44.integrations.Core.InvokeLLM({
        prompt: `Search TMDB for actor "${actorName}" ${actorId ? `(TMDB ID: ${actorId})` : ''} and return their filmography.
        
Include:
- All movies they appeared in
- All TV shows they appeared in

Return the exact titles as they appear on TMDB.`,
        add_context_from_internet: true,
        response_json_schema: {
            type: "object",
            properties: {
                actor_name: { type: "string" },
                movies: {
                    type: "array",
                    items: { type: "string" },
                    description: "List of movie titles"
                },
                tv_shows: {
                    type: "array",
                    items: { type: "string" },
                    description: "List of TV show titles"
                }
            },
            required: ["actor_name", "movies", "tv_shows"]
        }
    });

    console.log('🎭 Actor filmography:', tmdbResponse.movies?.length || 0, 'movies,', tmdbResponse.tv_shows?.length || 0, 'TV shows');

    // Match with library using flexible matching
    const matchedMovies = [];
    const matchedSeries = [];

    const normalizeTitle = (title) => {
        return title
            .toLowerCase()
            .replace(/[^\w\s]/g, '')
            .replace(/\s+/g, ' ')
            .trim();
    };

    // Match movies
    for (const tmdbMovie of (tmdbResponse.movies || [])) {
        const normTmdb = normalizeTitle(tmdbMovie);
        
        for (const libraryMovie of allMovies) {
            const normLibrary = normalizeTitle(libraryMovie.name);
            
            // Check if titles match or library contains TMDB title
            if (normLibrary.includes(normTmdb) || normTmdb.includes(normLibrary)) {
                if (!matchedMovies.some(m => m.stream_id === libraryMovie.stream_id)) {
                    matchedMovies.push(libraryMovie);
                    console.log('✓ Matched movie:', libraryMovie.name, '<->', tmdbMovie);
                }
            }
        }
    }

    // Match series
    for (const tmdbShow of (tmdbResponse.tv_shows || [])) {
        const normTmdb = normalizeTitle(tmdbShow);
        
        for (const librarySeries of allSeries) {
            const normLibrary = normalizeTitle(librarySeries.name);
            
            if (normLibrary.includes(normTmdb) || normTmdb.includes(normLibrary)) {
                if (!matchedSeries.some(s => s.series_id === librarySeries.series_id)) {
                    matchedSeries.push(librarySeries);
                    console.log('✓ Matched series:', librarySeries.name, '<->', tmdbShow);
                }
            }
        }
    }

    console.log('📊 Final matches:', matchedMovies.length, 'movies,', matchedSeries.length, 'series');

    return {
        movies: matchedMovies,
        series: matchedSeries,
        actorName: tmdbResponse.actor_name || actorName
    };
};

export default function CastSearch() {
    const navigate = useNavigate();
    const urlParams = new URLSearchParams(window.location.search);
    const playlistId = urlParams.get('playlistId');
    const actorName = urlParams.get('actorName') ? decodeURIComponent(urlParams.get('actorName')) : '';
    const actorId = urlParams.get('actorId');

    const { data: playlist } = useQuery({
        queryKey: ['playlist', playlistId],
        queryFn: () => getPlaylistFromLocal(playlistId),
        enabled: !!playlistId
    });

    const { data: results, isLoading, isError } = useQuery({
        queryKey: ['cast-search', playlistId, actorName, actorId],
        queryFn: () => fetchCastContent(playlist, actorName, actorId),
        enabled: !!playlist && !!actorName,
        staleTime: 1000 * 60 * 60,
        retry: 1,
    });

    const totalResults = (results?.movies?.length || 0) + (results?.series?.length || 0);

    return (
        <div className="min-h-screen p-4 md:p-8">
            <div className="max-w-7xl mx-auto">
                <Button 
                    variant="ghost" 
                    onClick={() => navigate(-1)} 
                    className="mb-6 text-cyan-300 hover:text-white hover:bg-blue-500/20"
                >
                    <ArrowLeft className="w-4 h-4 mr-2" />
                    Back
                </Button>

                <div className="mb-8">
                    <h1 className="text-3xl md:text-4xl font-bold text-white mb-2 flex items-center gap-3">
                        <User className="text-cyan-400" />
                        {results?.actorName || actorName}
                    </h1>
                    <p className="text-cyan-300">
                        Content featuring this actor in your library
                    </p>
                </div>

                {isLoading && (
                    <div className="flex flex-col items-center gap-3 text-cyan-300 py-16">
                        <Loader2 className="w-12 h-12 animate-spin" />
                        <span className="text-lg">Searching filmography...</span>
                        <span className="text-sm text-gray-400">Checking TMDB and matching with your library</span>
                    </div>
                )}

                {isError && (
                    <div className="bg-red-900/20 border border-red-500/30 text-red-400 p-6 rounded-lg text-center">
                        <h3 className="font-semibold text-white mb-2">Search Failed</h3>
                        <p>Could not fetch filmography. Please try again.</p>
                    </div>
                )}

                {!isLoading && !isError && results && (
                    <>
                        {totalResults === 0 ? (
                            <div className="text-center py-16">
                                <User className="w-16 h-16 text-gray-500 mx-auto mb-4" />
                                <h3 className="text-xl font-semibold text-white mb-2">No Content Found</h3>
                                <p className="text-gray-400">
                                    Your library doesn't contain any movies or series featuring {results.actorName || actorName}.
                                </p>
                            </div>
                        ) : (
                            <div className="space-y-8">
                                <div className="bg-blue-500/10 border border-blue-500/30 p-4 rounded-lg">
                                    <p className="text-cyan-200 text-lg">
                                        Found <span className="font-bold">{totalResults}</span> titles featuring {results.actorName || actorName}
                                    </p>
                                </div>

                                {results.movies && results.movies.length > 0 && (
                                    <div>
                                        <h2 className="text-2xl font-bold text-white mb-4 flex items-center gap-2">
                                            <Film className="text-cyan-400" />
                                            Movies ({results.movies.length})
                                        </h2>
                                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                                            {results.movies.map((movie, i) => (
                                                <Link key={i} to={createPageUrl(`MovieInfo?playlistId=${playlistId}&vodId=${movie.stream_id}`)}>
                                                    <motion.div
                                                        whileHover={{ scale: 1.05 }}
                                                        className="h-full"
                                                    >
                                                        <Card className="bg-slate-800/50 hover:border-blue-500/60 transition-all">
                                                            <CardContent className="p-2">
                                                                <div className="aspect-[2/3] bg-gray-900 mb-2 rounded overflow-hidden relative">
                                                                    {movie.stream_icon ? (
                                                                        <img 
                                                                            src={movie.stream_icon} 
                                                                            alt={movie.name} 
                                                                            className="w-full h-full object-cover"
                                                                            onError={(e) => {
                                                                                e.target.style.display = 'none';
                                                                                e.target.nextElementSibling.style.display = 'flex';
                                                                            }}
                                                                        />
                                                                    ) : null}
                                                                    <div className="w-full h-full absolute inset-0 bg-gray-800 flex items-center justify-center">
                                                                        <Film className="w-10 h-10 text-cyan-400" />
                                                                    </div>
                                                                </div>
                                                                <p className="text-white text-sm truncate">{movie.name}</p>
                                                            </CardContent>
                                                        </Card>
                                                    </motion.div>
                                                </Link>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {results.series && results.series.length > 0 && (
                                    <div>
                                        <h2 className="text-2xl font-bold text-white mb-4 flex items-center gap-2">
                                            <Clapperboard className="text-cyan-400" />
                                            Series ({results.series.length})
                                        </h2>
                                        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-4">
                                            {results.series.map((series, i) => (
                                                <Link key={i} to={createPageUrl(`SeriesDetails?playlistId=${playlistId}&seriesId=${series.series_id}`)}>
                                                    <motion.div
                                                        whileHover={{ scale: 1.05 }}
                                                        className="h-full"
                                                    >
                                                        <Card className="bg-slate-800/50 hover:border-blue-500/60 transition-all">
                                                            <CardContent className="p-2">
                                                                <div className="aspect-[2/3] bg-gray-900 mb-2 rounded overflow-hidden relative">
                                                                    {series.cover ? (
                                                                        <img 
                                                                            src={series.cover} 
                                                                            alt={series.name} 
                                                                            className="w-full h-full object-cover"
                                                                            onError={(e) => {
                                                                                e.target.style.display = 'none';
                                                                                e.target.nextElementSibling.style.display = 'flex';
                                                                            }}
                                                                        />
                                                                    ) : null}
                                                                    <div className="w-full h-full absolute inset-0 bg-gray-800 flex items-center justify-center">
                                                                        <Clapperboard className="w-10 h-10 text-cyan-400" />
                                                                    </div>
                                                                </div>
                                                                <p className="text-white text-sm truncate">{series.name}</p>
                                                            </CardContent>
                                                        </Card>
                                                    </motion.div>
                                                </Link>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        )}
                    </>
                )}
            </div>
        </div>
    );
}